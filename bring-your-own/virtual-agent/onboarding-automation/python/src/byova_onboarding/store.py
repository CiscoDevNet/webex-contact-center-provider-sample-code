"""In-memory state used by the runnable sample.

Replace this module with durable, encrypted storage and distributed locking before
running more than one process or onboarding production customers.
"""

from __future__ import annotations

import asyncio
import secrets
from copy import deepcopy
from datetime import datetime, timedelta
from uuid import uuid4

from .models import CustomerRecord, LifecycleEvent, OAuthTokens, utc_now

TenantKey = tuple[str, str]


class InvalidOAuthState(ValueError):
    """Raised when OAuth state is absent, expired, or already consumed."""


class InMemoryStore:
    """Concurrency-safe sample store with lifecycle generation fencing."""

    def __init__(self) -> None:
        self._guard = asyncio.Lock()
        self._tenant_locks: dict[TenantKey, asyncio.Lock] = {}
        self._oauth_states: dict[str, datetime] = {}
        self._integration_tokens: OAuthTokens | None = None
        self._customers: dict[TenantKey, CustomerRecord] = {}

    async def tenant_lock(self, key: TenantKey) -> asyncio.Lock:
        async with self._guard:
            return self._tenant_locks.setdefault(key, asyncio.Lock())

    async def issue_oauth_state(self, ttl_seconds: int) -> str:
        state = secrets.token_urlsafe(32)
        async with self._guard:
            self._oauth_states[state] = utc_now() + timedelta(seconds=ttl_seconds)
        return state

    async def consume_oauth_state(self, state: str) -> None:
        async with self._guard:
            expires_at = self._oauth_states.pop(state, None)
        if expires_at is None or expires_at <= utc_now():
            raise InvalidOAuthState("OAuth state is absent, expired, or already used")

    async def save_integration_tokens(self, tokens: OAuthTokens) -> None:
        async with self._guard:
            self._integration_tokens = deepcopy(tokens)

    async def get_integration_tokens(self) -> OAuthTokens | None:
        async with self._guard:
            return deepcopy(self._integration_tokens)

    async def begin_authorization(self, event: LifecycleEvent) -> int | None:
        """Start unless the event is duplicate or older than current state."""

        key = (event.service_app_id, event.customer_org_id)
        async with self._guard:
            record = self._customers.get(key)
            if record is None:
                record = CustomerRecord(
                    record_id=str(uuid4()),
                    service_app_id=event.service_app_id,
                    customer_org_id=event.customer_org_id,
                )
                self._customers[key] = record
            if event.event_key in record.processed_event_keys:
                return None
            if record.lifecycle_at and event.authorization_date < record.lifecycle_at:
                return None
            if (
                record.status == "inactive"
                and record.lifecycle_at
                and event.authorization_date <= record.lifecycle_at
            ):
                # A redelivered authorization from the generation that was revoked
                # must not reactivate the customer. A genuine reauthorization has a
                # newer data.authorizationDate.
                return None
            record.generation += 1
            record.status = "provisioning"
            record.lifecycle_at = event.authorization_date
            record.last_error = None
            return record.generation

    async def complete_authorization(
        self,
        event: LifecycleEvent,
        generation: int,
        tokens: OAuthTokens,
        data_source: dict[str, object],
        nonce: str,
    ) -> bool:
        """Commit only if a later deauthorization has not fenced this worker."""

        key = (event.service_app_id, event.customer_org_id)
        async with self._guard:
            record = self._customers[key]
            if record.generation != generation or record.status != "provisioning":
                return False
            record.service_app_tokens = deepcopy(tokens)
            record.data_source_id = str(data_source["id"])
            record.data_source_status = str(data_source.get("status", "active"))
            record.data_source_jws = (
                str(data_source["jwsToken"]) if data_source.get("jwsToken") else None
            )
            record.data_source_token_expiry_time = (
                str(data_source["tokenExpiryTime"])
                if data_source.get("tokenExpiryTime")
                else None
            )
            record.data_source_nonce = nonce
            record.processed_event_keys.add(event.event_key)
            record.status = "active"
            return True

    async def fail_authorization(
        self, event: LifecycleEvent, generation: int, reason: str
    ) -> None:
        key = (event.service_app_id, event.customer_org_id)
        async with self._guard:
            record = self._customers.get(key)
            if record and record.generation == generation:
                record.status = "error"
                record.last_error = reason
                record.erase_credentials()

    async def deauthorize(self, event: LifecycleEvent) -> str:
        """Fence older work, disable the tenant, and erase its credentials."""

        key = (event.service_app_id, event.customer_org_id)
        async with self._guard:
            record = self._customers.get(key)
            if record is None:
                record = CustomerRecord(
                    record_id=str(uuid4()),
                    service_app_id=event.service_app_id,
                    customer_org_id=event.customer_org_id,
                )
                self._customers[key] = record
            if event.event_key in record.processed_event_keys:
                return record.record_id
            if record.lifecycle_at and event.authorization_date < record.lifecycle_at:
                return record.record_id
            record.generation += 1
            record.status = "inactive"
            record.lifecycle_at = event.authorization_date
            record.processed_event_keys.add(event.event_key)
            record.last_error = None
            record.erase_credentials()
            return record.record_id

    async def complete_renewal(
        self,
        key: TenantKey,
        generation: int,
        tokens: OAuthTokens,
        data_source: dict[str, object],
        nonce: str,
    ) -> bool:
        async with self._guard:
            record = self._customers.get(key)
            if (
                not record
                or record.generation != generation
                or record.status != "active"
            ):
                return False
            record.service_app_tokens = deepcopy(tokens)
            record.data_source_jws = (
                str(data_source["jwsToken"]) if data_source.get("jwsToken") else None
            )
            record.data_source_token_expiry_time = (
                str(data_source["tokenExpiryTime"])
                if data_source.get("tokenExpiryTime")
                else None
            )
            record.data_source_nonce = nonce
            return True

    async def get_customer(self, key: TenantKey) -> CustomerRecord | None:
        async with self._guard:
            record = self._customers.get(key)
            return deepcopy(record) if record else None
