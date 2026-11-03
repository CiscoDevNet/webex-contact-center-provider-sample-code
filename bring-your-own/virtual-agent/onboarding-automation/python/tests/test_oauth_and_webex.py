from __future__ import annotations

import json
from urllib.parse import parse_qs, urlparse

import httpx
import pytest

from byova_onboarding.config import AUTOMATION_SCOPES, Settings
from byova_onboarding.store import InMemoryStore, InvalidOAuthState
from byova_onboarding.webex import WebexAPIError, WebexClient


@pytest.mark.asyncio
async def test_authorization_url_has_exact_scopes_and_state(settings: Settings) -> None:
    client = WebexClient(settings, InMemoryStore())

    parsed = urlparse(client.authorization_url("csrf-state"))
    query = parse_qs(parsed.query)

    assert parsed.path == "/v1/authorize"
    assert query == {
        "response_type": ["code"],
        "client_id": ["integration-client"],
        "redirect_uri": ["https://provider.example/oauth/callback"],
        "scope": [" ".join(AUTOMATION_SCOPES)],
        "state": ["csrf-state"],
    }


@pytest.mark.asyncio
async def test_oauth_state_is_single_use() -> None:
    store = InMemoryStore()
    state = await store.issue_oauth_state(600)

    await store.consume_oauth_state(state)

    with pytest.raises(InvalidOAuthState):
        await store.consume_oauth_state(state)


@pytest.mark.asyncio
async def test_customer_token_request_uses_integration_bearer_and_authorizer_org(
    settings: Settings, integration_tokens
) -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(
            200,
            json={
                "access_token": "customer-access",
                "refresh_token": "customer-refresh",
                "expires_in": 3600,
            },
        )

    store = InMemoryStore()
    await store.save_integration_tokens(integration_tokens)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = WebexClient(settings, store, http)
        tokens = await client.fetch_customer_service_app_tokens(
            "customer-authorizer-org"
        )

    assert tokens.access_token == "customer-access"
    assert len(seen) == 1
    request = seen[0]
    assert request.method == "POST"
    assert request.url.path == "/v1/applications/service-app-id/token"
    assert request.headers["authorization"] == "Bearer integration-access"
    assert json.loads(request.content) == {
        "clientId": "service-client",
        "clientSecret": "service-secret",
        "targetOrgId": "customer-authorizer-org",
    }


@pytest.mark.asyncio
async def test_customer_token_refresh_is_separate_from_integration_auth(
    settings: Settings,
) -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return httpx.Response(
            200,
            json={"access_token": "new-customer-access", "expires_in": 3600},
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = WebexClient(settings, InMemoryStore(), http)
        tokens = await client.refresh_customer_tokens("customer-refresh")

    assert tokens.access_token == "new-customer-access"
    assert tokens.refresh_token == "customer-refresh"
    assert len(seen) == 1
    request = seen[0]
    assert request.url.path == "/v1/access_token"
    assert "authorization" not in request.headers
    assert request.headers["content-type"].startswith(
        "application/x-www-form-urlencoded"
    )
    assert parse_qs(request.content.decode()) == {
        "grant_type": ["refresh_token"],
        "client_id": ["service-client"],
        "client_secret": ["service-secret"],
        "refresh_token": ["customer-refresh"],
    }


@pytest.mark.asyncio
async def test_data_source_create_uses_current_rest_shape(settings: Settings) -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        if request.method == "GET":
            return httpx.Response(200, json={"items": []})
        request_body = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "id": "datasource-id",
                "applicationId": settings.service_app_client_id,
                "status": "active",
                "url": request_body["url"],
                "jwsToken": "secret-jws",
                "tokenExpiryTime": "2026-09-29T12:00:00Z",
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = WebexClient(settings, InMemoryStore(), http)
        data_source, nonce = await client.reconcile_data_source("customer-access")

    assert data_source["id"] == "datasource-id"
    assert [request.method for request in seen] == ["GET", "POST"]
    assert seen[0].url.path == "/v1/datasources"
    assert seen[1].url.path == "/v1/datasources"
    assert seen[1].headers["authorization"] == "Bearer customer-access"
    assert json.loads(seen[1].content) == {
        "schemaId": ["schema-id"],
        "url": "https://provider.example/byova",
        "audience": "BYOVAGateway",
        "subject": "callAudioData",
        "nonce": nonce,
        "tokenLifeMinutes": "60",
    }


@pytest.mark.asyncio
async def test_data_source_renewal_sends_full_active_update(settings: Settings) -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        body = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "id": "datasource-id",
                "applicationId": settings.service_app_client_id,
                "schemaId": "schema-id",
                "status": "active",
                "url": body["url"],
                "jwsToken": "renewed-jws",
                "tokenExpiryTime": "2026-09-29T13:00:00Z",
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = WebexClient(settings, InMemoryStore(), http)
        _, nonce = await client.renew_data_source("datasource-id", "customer-access")

    assert len(seen) == 1
    assert seen[0].method == "PUT"
    assert seen[0].url.path == "/v1/datasources/datasource-id"
    assert json.loads(seen[0].content) == {
        "schemaId": ["schema-id"],
        "url": "https://provider.example/byova",
        "audience": "BYOVAGateway",
        "subject": "callAudioData",
        "nonce": nonce,
        "tokenLifeMinutes": "60",
        "status": "active",
    }


@pytest.mark.asyncio
async def test_data_source_reconciliation_accepts_own_client_id(
    settings: Settings,
) -> None:
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        if request.method == "GET":
            return httpx.Response(
                200,
                json={
                    "items": [
                        {
                            "id": "datasource-id",
                            "url": settings.data_source_url,
                            "applicationId": settings.service_app_client_id,
                        }
                    ]
                },
            )
        return httpx.Response(
            200,
            json={
                "id": "datasource-id",
                "url": settings.data_source_url,
                "applicationId": settings.service_app_client_id,
                "status": "active",
                "jwsToken": "renewed-jws",
                "tokenExpiryTime": "2026-09-29T13:00:00Z",
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = WebexClient(settings, InMemoryStore(), http)
        data_source, _ = await client.reconcile_data_source("customer-access")

    assert data_source["id"] == "datasource-id"
    assert [request.method for request in seen] == ["GET", "PUT"]
    assert seen[1].url.path == "/v1/datasources/datasource-id"


@pytest.mark.asyncio
async def test_data_source_reconciliation_refuses_foreign_route(
    settings: Settings,
) -> None:
    request_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal request_count
        request_count += 1
        return httpx.Response(
            200,
            json={
                "items": [
                    {
                        "id": "foreign-datasource",
                        "url": settings.data_source_url,
                        "applicationId": "another-service-app",
                    }
                ]
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = WebexClient(settings, InMemoryStore(), http)
        with pytest.raises(WebexAPIError, match="another Service App"):
            await client.reconcile_data_source("customer-access")

    assert request_count == 1


@pytest.mark.asyncio
async def test_webhook_reconciliation_creates_only_missing_exact_records(
    settings: Settings, integration_tokens
) -> None:
    created: list[dict[str, object]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "GET":
            return httpx.Response(
                200,
                json={
                    "items": [
                        {
                            "id": "authorized-hook",
                            "targetUrl": settings.webhook_target_url,
                            "resource": "serviceApp",
                            "event": "authorized",
                            "filter": "id=service-app-id",
                            "status": "active",
                        }
                    ]
                },
            )
        body = json.loads(request.content)
        created.append(body)
        return httpx.Response(200, json={"id": "deauthorized-hook", **body})

    store = InMemoryStore()
    await store.save_integration_tokens(integration_tokens)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = WebexClient(settings, store, http)
        ids = await client.ensure_lifecycle_webhooks()

    assert ids == ["deauthorized-hook"]
    assert created == [
        {
            "name": "BYOVA Service App deauthorized",
            "targetUrl": settings.webhook_target_url,
            "resource": "serviceApp",
            "event": "deauthorized",
            "filter": "id=service-app-id",
            "secret": "webhook-secret",
        }
    ]
