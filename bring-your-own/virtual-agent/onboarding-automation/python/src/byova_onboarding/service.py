"""Orchestration for provider OAuth and per-customer BYOVA onboarding."""

from __future__ import annotations

from dataclasses import dataclass

from .config import Settings
from .models import LifecycleEvent
from .store import InMemoryStore, TenantKey
from .webex import WebexClient


@dataclass(frozen=True)
class LifecycleResult:
    outcome: str
    record_id: str


class OnboardingService:
    def __init__(
        self, settings: Settings, store: InMemoryStore, webex: WebexClient
    ) -> None:
        self.settings = settings
        self.store = store
        self.webex = webex

    async def start_provider_oauth(self) -> str:
        state = await self.store.issue_oauth_state(
            self.settings.oauth_state_ttl_seconds
        )
        return self.webex.authorization_url(state)

    async def finish_provider_oauth(self, code: str, state: str) -> list[str]:
        # Consume state before sending the one-time code to Webex.
        await self.store.consume_oauth_state(state)
        tokens = await self.webex.exchange_integration_code(code)
        await self.store.save_integration_tokens(tokens)
        return await self.webex.ensure_lifecycle_webhooks()

    async def handle_lifecycle_event(self, event: LifecycleEvent) -> LifecycleResult:
        key = (event.service_app_id, event.customer_org_id)

        if event.event == "deauthorized":
            # Deliberately bypass the provisioning lock so this fences in-flight work.
            record_id = await self.store.deauthorize(event)
            return LifecycleResult("inactive", record_id)

        lock = await self.store.tenant_lock(key)
        async with lock:
            generation = await self.store.begin_authorization(event)
            if generation is None:
                record = await self.store.get_customer(key)
                if record is None:
                    raise RuntimeError("customer record disappeared")
                return LifecycleResult("unchanged", record.record_id)

            try:
                customer_tokens = await self.webex.fetch_customer_service_app_tokens(
                    event.customer_org_id
                )
                data_source, nonce = await self.webex.reconcile_data_source(
                    customer_tokens.access_token
                )
                committed = await self.store.complete_authorization(
                    event, generation, customer_tokens, data_source, nonce
                )
            except Exception as exc:
                await self.store.fail_authorization(
                    event, generation, type(exc).__name__
                )
                raise

            record = await self.store.get_customer(key)
            if record is None:
                raise RuntimeError("customer record disappeared")
            return LifecycleResult(
                "active" if committed else "fenced", record.record_id
            )

    async def renew_customer(self, key: TenantKey) -> LifecycleResult:
        """Refresh customer OAuth credentials and Data Source JWS independently."""

        lock = await self.store.tenant_lock(key)
        async with lock:
            record = await self.store.get_customer(key)
            if (
                record is None
                or record.status != "active"
                or record.service_app_tokens is None
                or not record.service_app_tokens.refresh_token
                or not record.data_source_id
            ):
                raise ValueError(
                    "customer is not active or lacks renewable credentials"
                )

            generation = record.generation
            refreshed = await self.webex.refresh_customer_tokens(
                record.service_app_tokens.refresh_token
            )
            data_source, nonce = await self.webex.renew_data_source(
                record.data_source_id, refreshed.access_token
            )
            committed = await self.store.complete_renewal(
                key, generation, refreshed, data_source, nonce
            )
            return LifecycleResult(
                "renewed" if committed else "fenced", record.record_id
            )
