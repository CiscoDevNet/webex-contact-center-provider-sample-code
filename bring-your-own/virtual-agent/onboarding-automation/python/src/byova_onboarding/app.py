"""FastAPI entry point for the automated BYOVA onboarding sample."""

from __future__ import annotations

import hashlib
import json
import logging
from contextlib import asynccontextmanager
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException, Query, Request, status
from fastapi.responses import RedirectResponse

from .config import Settings
from .models import LifecycleEvent, parse_webex_time
from .service import OnboardingService
from .signature import verify_spark_signature
from .store import InMemoryStore, InvalidOAuthState
from .webex import WebexAPIError, WebexClient

LOGGER = logging.getLogger("byova_onboarding")


def _parse_lifecycle_event(payload: Any, settings: Settings) -> LifecycleEvent:
    if not isinstance(payload, dict) or payload.get("resource") != "serviceApp":
        raise ValueError("unexpected webhook resource")
    event = payload.get("event")
    if event not in ("authorized", "deauthorized"):
        raise ValueError("unexpected webhook event")

    data = payload.get("data")
    if not isinstance(data, dict) or data.get("id") != settings.service_app_id:
        raise ValueError("webhook is for a different Service App")

    # The envelope orgId belongs to the webhook owner. It is intentionally ignored.
    customer_org_id = data.get("authorizerOrgId")
    authorization_date = data.get("authorizationDate")
    if not isinstance(customer_org_id, str) or not customer_org_id:
        raise ValueError("webhook is missing data.authorizerOrgId")
    if not isinstance(authorization_date, str) or not authorization_date:
        raise ValueError("webhook is missing data.authorizationDate")

    logical_key = "\x1f".join(
        (settings.service_app_id, customer_org_id, event, authorization_date)
    ).encode("utf-8")
    return LifecycleEvent(
        event=event,
        service_app_id=settings.service_app_id,
        customer_org_id=customer_org_id,
        authorization_date=parse_webex_time(authorization_date),
        event_key=hashlib.sha256(logical_key).hexdigest(),
    )


def create_app(
    settings: Settings | None = None,
    *,
    store: InMemoryStore | None = None,
    http: httpx.AsyncClient | None = None,
) -> FastAPI:
    """Create the app; injectable dependencies keep all API behavior testable."""

    resolved_settings = settings or Settings.from_env()
    resolved_store = store or InMemoryStore()
    webex = WebexClient(resolved_settings, resolved_store, http)
    service = OnboardingService(resolved_settings, resolved_store, webex)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        try:
            yield
        finally:
            await webex.close()

    app = FastAPI(
        title="BYOVA onboarding automation sample",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        lifespan=lifespan,
    )
    app.state.settings = resolved_settings
    app.state.store = resolved_store
    app.state.webex = webex
    app.state.service = service

    @app.get("/healthz")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/oauth/start", response_class=RedirectResponse)
    async def oauth_start() -> RedirectResponse:
        return RedirectResponse(await service.start_provider_oauth())

    @app.get("/oauth/callback")
    async def oauth_callback(
        code: str = Query(min_length=1), state: str = Query(min_length=1)
    ) -> dict[str, object]:
        try:
            created_webhook_ids = await service.finish_provider_oauth(code, state)
        except InvalidOAuthState as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
            ) from exc
        except WebexAPIError as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
            ) from exc
        return {
            "status": "ready",
            "webhooksCreated": len(created_webhook_ids),
        }

    @app.post("/webhooks/service-app", status_code=status.HTTP_202_ACCEPTED)
    async def service_app_webhook(request: Request) -> dict[str, str]:
        raw_body = await request.body()
        signature = request.headers.get("x-spark-signature")
        if not verify_spark_signature(
            raw_body, signature, resolved_settings.webhook_secret
        ):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="invalid webhook signature",
            )
        try:
            payload = json.loads(raw_body)
            event = _parse_lifecycle_event(payload, resolved_settings)
        except (json.JSONDecodeError, UnicodeDecodeError, ValueError) as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
            ) from exc

        try:
            result = await service.handle_lifecycle_event(event)
        except WebexAPIError as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
            ) from exc

        # Never log the customer org, raw body, tokens, JWS, or Authorization headers.
        LOGGER.info(
            "processed service app lifecycle event",
            extra={
                "event": event.event,
                "onboarding_record_id": result.record_id,
                "outcome": result.outcome,
            },
        )
        return {"status": result.outcome, "recordId": result.record_id}

    return app
