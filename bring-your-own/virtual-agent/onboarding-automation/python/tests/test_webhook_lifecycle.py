from __future__ import annotations

import hashlib
import hmac
import json
from datetime import timedelta

import httpx
import pytest

from byova_onboarding.app import create_app
from byova_onboarding.config import Settings
from byova_onboarding.models import LifecycleEvent, OAuthTokens, utc_now
from byova_onboarding.service import OnboardingService
from byova_onboarding.signature import verify_spark_signature
from byova_onboarding.store import InMemoryStore
from byova_onboarding.webex import WebexClient


def signed_body(payload: dict[str, object], secret: str) -> tuple[bytes, str]:
    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    signature = hmac.new(secret.encode(), raw, hashlib.sha1).hexdigest()
    return raw, signature


def service_app_event(
    *,
    event: str = "authorized",
    service_app_id: str = "service-app-id",
    customer_org_id: str = "customer-authorizer-org",
    authorization_date: str = "2026-09-29T08:00:00Z",
) -> dict[str, object]:
    return {
        "id": "webhook-envelope-id",
        "resource": "serviceApp",
        "event": event,
        "orgId": "provider-webhook-owner-org",
        "data": {
            "id": service_app_id,
            "authorizerOrgId": customer_org_id,
            "authorizationDate": authorization_date,
        },
    }


def test_signature_uses_exact_raw_bytes() -> None:
    raw = b'{"a":1, "b":2}'
    signature = hmac.new(b"secret", raw, hashlib.sha1).hexdigest()

    assert verify_spark_signature(raw, signature, "secret")
    assert not verify_spark_signature(b'{"a":1,"b":2}', signature, "secret")
    assert not verify_spark_signature(raw, "not-hex", "secret")
    assert not verify_spark_signature(raw, None, "secret")


@pytest.mark.asyncio
async def test_authorized_event_uses_data_authorizer_org_and_is_idempotent(
    settings: Settings, integration_tokens
) -> None:
    store = InMemoryStore()
    await store.save_integration_tokens(integration_tokens)
    requests: list[httpx.Request] = []

    def webex_handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path.endswith("/token"):
            return httpx.Response(
                200,
                json={
                    "access_token": "customer-access",
                    "refresh_token": "customer-refresh",
                    "expires_in": 3600,
                },
            )
        if request.method == "GET" and request.url.path.endswith("/datasources"):
            return httpx.Response(200, json={"items": []})
        if request.method == "POST" and request.url.path.endswith("/datasources"):
            body = json.loads(request.content)
            return httpx.Response(
                200,
                json={
                    "id": "datasource-id",
                    "status": "active",
                    "url": body["url"],
                    "jwsToken": "customer-jws",
                    "tokenExpiryTime": "2026-09-29T09:00:00Z",
                },
            )
        raise AssertionError(f"unexpected request: {request.method} {request.url}")

    async with httpx.AsyncClient(
        transport=httpx.MockTransport(webex_handler)
    ) as webex_http:
        app = create_app(settings, store=store, http=webex_http)
        raw, signature = signed_body(service_app_event(), settings.webhook_secret)
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(
            transport=transport, base_url="https://sample.test"
        ) as client:
            first = await client.post(
                "/webhooks/service-app",
                content=raw,
                headers={"x-spark-signature": signature},
            )
            second = await client.post(
                "/webhooks/service-app",
                content=raw,
                headers={"X-Spark-Signature": signature},
            )

    assert first.status_code == 202
    assert first.json()["status"] == "active"
    assert second.status_code == 202
    assert second.json()["status"] == "unchanged"
    assert len(requests) == 3
    token_body = json.loads(requests[0].content)
    assert token_body["targetOrgId"] == "customer-authorizer-org"
    assert token_body["targetOrgId"] != "provider-webhook-owner-org"

    record = await store.get_customer(("service-app-id", "customer-authorizer-org"))
    assert record is not None
    assert record.status == "active"
    assert record.data_source_id == "datasource-id"


@pytest.mark.asyncio
async def test_wrong_service_app_and_bad_signature_are_rejected(
    settings: Settings,
) -> None:
    app = create_app(settings, store=InMemoryStore())
    transport = httpx.ASGITransport(app=app)
    wrong_raw, wrong_signature = signed_body(
        service_app_event(service_app_id="some-other-app"), settings.webhook_secret
    )

    async with httpx.AsyncClient(
        transport=transport, base_url="https://sample.test"
    ) as client:
        wrong_app = await client.post(
            "/webhooks/service-app",
            content=wrong_raw,
            headers={"x-spark-signature": wrong_signature},
        )
        bad_signature = await client.post(
            "/webhooks/service-app",
            content=wrong_raw,
            headers={"x-spark-signature": "0" * 40},
        )

    assert wrong_app.status_code == 400
    assert bad_signature.status_code == 401


@pytest.mark.asyncio
async def test_deauthorization_erases_secrets_and_fences_stale_work() -> None:
    store = InMemoryStore()
    authorization = LifecycleEvent(
        event="authorized",
        service_app_id="service-app-id",
        customer_org_id="customer-org",
        authorization_date=utc_now(),
        event_key="authorized-key",
    )
    generation = await store.begin_authorization(authorization)
    assert generation is not None

    deauthorization = LifecycleEvent(
        event="deauthorized",
        service_app_id="service-app-id",
        customer_org_id="customer-org",
        # Webex can retain the original authorizationDate on deauthorization.
        authorization_date=authorization.authorization_date,
        event_key="deauthorized-key",
    )
    await store.deauthorize(deauthorization)

    committed = await store.complete_authorization(
        authorization,
        generation,
        OAuthTokens(
            access_token="must-not-survive",
            refresh_token="must-not-survive",
            expires_at=utc_now() + timedelta(hours=1),
        ),
        {
            "id": "datasource-id",
            "status": "active",
            "jwsToken": "must-not-survive",
        },
        "must-not-survive",
    )

    record = await store.get_customer(("service-app-id", "customer-org"))
    assert committed is False
    assert record is not None
    assert record.status == "inactive"
    assert record.service_app_tokens is None
    assert record.data_source_jws is None
    assert record.data_source_nonce is None

    redelivered_generation = await store.begin_authorization(authorization)
    assert redelivered_generation is None
    record = await store.get_customer(("service-app-id", "customer-org"))
    assert record is not None
    assert record.status == "inactive"


@pytest.mark.asyncio
async def test_customer_renewal_refreshes_oauth_then_data_source(
    settings: Settings,
) -> None:
    store = InMemoryStore()
    event = LifecycleEvent(
        event="authorized",
        service_app_id=settings.service_app_id,
        customer_org_id="customer-org",
        authorization_date=utc_now(),
        event_key="authorized-key",
    )
    generation = await store.begin_authorization(event)
    assert generation is not None
    await store.complete_authorization(
        event,
        generation,
        OAuthTokens(
            access_token="old-access",
            refresh_token="customer-refresh",
            expires_at=utc_now() + timedelta(hours=1),
        ),
        {
            "id": "datasource-id",
            "status": "active",
            "jwsToken": "old-jws",
            "tokenExpiryTime": "2026-09-29T09:00:00Z",
        },
        "old-nonce",
    )
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path.endswith("/access_token"):
            return httpx.Response(
                200,
                json={"access_token": "new-access", "expires_in": 3600},
            )
        body = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "id": "datasource-id",
                "schemaId": "schema-id",
                "status": "active",
                "url": body["url"],
                "jwsToken": "new-jws",
                "tokenExpiryTime": "2026-09-29T10:00:00Z",
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        webex = WebexClient(settings, store, http)
        service = OnboardingService(settings, store, webex)
        result = await service.renew_customer((settings.service_app_id, "customer-org"))

    assert result.outcome == "renewed"
    assert [request.url.path for request in requests] == [
        "/v1/access_token",
        "/v1/datasources/datasource-id",
    ]
    assert "authorization" not in requests[0].headers
    assert requests[1].headers["authorization"] == "Bearer new-access"
    record = await store.get_customer((settings.service_app_id, "customer-org"))
    assert record is not None
    assert record.service_app_tokens is not None
    assert record.service_app_tokens.access_token == "new-access"
    assert record.service_app_tokens.refresh_token == "customer-refresh"
    assert record.data_source_jws == "new-jws"
