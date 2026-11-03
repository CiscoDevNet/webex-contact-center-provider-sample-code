"""Typed Webex API calls required by the onboarding workflow."""

from __future__ import annotations

import secrets
from typing import Any
from urllib.parse import quote, urlencode

import httpx

from .config import AUTOMATION_SCOPES, Settings
from .models import OAuthTokens
from .store import InMemoryStore


class WebexAPIError(RuntimeError):
    """A redacted Webex API failure safe to record in sample state."""


class WebexClient:
    def __init__(
        self,
        settings: Settings,
        store: InMemoryStore,
        http: httpx.AsyncClient | None = None,
    ) -> None:
        self.settings = settings
        self.store = store
        self._owns_http = http is None
        self.http = http or httpx.AsyncClient(timeout=15.0)

    async def close(self) -> None:
        if self._owns_http:
            await self.http.aclose()

    def authorization_url(self, state: str) -> str:
        query = urlencode(
            {
                "response_type": "code",
                "client_id": self.settings.integration_client_id,
                "redirect_uri": self.settings.integration_redirect_uri,
                "scope": " ".join(AUTOMATION_SCOPES),
                "state": state,
            }
        )
        return f"{self.settings.webex_api_base_url}/authorize?{query}"

    async def exchange_integration_code(self, code: str) -> OAuthTokens:
        payload = await self._token_request(
            {
                "grant_type": "authorization_code",
                "client_id": self.settings.integration_client_id,
                "client_secret": self.settings.integration_client_secret,
                "redirect_uri": self.settings.integration_redirect_uri,
                "code": code,
            }
        )
        return self._parse_tokens(payload)

    async def integration_access_token(self) -> str:
        tokens = await self.store.get_integration_tokens()
        if tokens is None:
            raise WebexAPIError("Provider Integration has not been authorized")
        if not tokens.needs_refresh():
            return tokens.access_token
        if not tokens.refresh_token:
            raise WebexAPIError("Provider Integration refresh token is missing")
        payload = await self._token_request(
            {
                "grant_type": "refresh_token",
                "client_id": self.settings.integration_client_id,
                "client_secret": self.settings.integration_client_secret,
                "refresh_token": tokens.refresh_token,
            }
        )
        refreshed = self._parse_tokens(payload, tokens.refresh_token)
        await self.store.save_integration_tokens(refreshed)
        return refreshed.access_token

    async def ensure_lifecycle_webhooks(self) -> list[str]:
        """Create only missing exact-match authorized/deauthorized subscriptions."""

        bearer = await self.integration_access_token()
        response = await self._request("GET", "/webhooks", bearer=bearer)
        payload = self._json_response(response)
        existing = payload.get("items", []) if isinstance(payload, dict) else []
        created_ids: list[str] = []
        expected_filter = f"id={self.settings.service_app_id}"

        for event in ("authorized", "deauthorized"):
            found = any(
                item.get("resource") == "serviceApp"
                and item.get("event") == event
                and item.get("targetUrl") == self.settings.webhook_target_url
                and item.get("filter") == expected_filter
                and item.get("status", "active") == "active"
                for item in existing
            )
            if found:
                continue
            created = await self._request(
                "POST",
                "/webhooks",
                bearer=bearer,
                json={
                    "name": f"BYOVA Service App {event}",
                    "targetUrl": self.settings.webhook_target_url,
                    "resource": "serviceApp",
                    "event": event,
                    "filter": expected_filter,
                    "secret": self.settings.webhook_secret,
                },
            )
            created_payload = self._json_response(created)
            if not isinstance(created_payload, dict):
                raise WebexAPIError("Webhook create returned an invalid response")
            if created_payload.get("id"):
                created_ids.append(str(created_payload["id"]))
            existing.append(created_payload)
        return created_ids

    async def fetch_customer_service_app_tokens(
        self, customer_org_id: str
    ) -> OAuthTokens:
        integration_token = await self.integration_access_token()
        application_id = quote(self.settings.service_app_id, safe="")
        response = await self._request(
            "POST",
            f"/applications/{application_id}/token",
            bearer=integration_token,
            json={
                "clientId": self.settings.service_app_client_id,
                "clientSecret": self.settings.service_app_client_secret,
                "targetOrgId": customer_org_id,
            },
        )
        payload = self._json_response(response)
        if not isinstance(payload, dict):
            raise WebexAPIError(
                "Application token endpoint returned an invalid response"
            )
        return self._parse_tokens(payload)

    async def refresh_customer_tokens(self, refresh_token: str) -> OAuthTokens:
        payload = await self._token_request(
            {
                "grant_type": "refresh_token",
                "client_id": self.settings.service_app_client_id,
                "client_secret": self.settings.service_app_client_secret,
                "refresh_token": refresh_token,
            }
        )
        return self._parse_tokens(payload, refresh_token)

    def data_source_payload(
        self, nonce: str, *, include_active_status: bool = False
    ) -> dict[str, object]:
        """Return the current documented Data Sources REST shape."""

        payload: dict[str, object] = {
            "schemaId": [self.settings.data_source_schema_id],
            "url": self.settings.data_source_url,
            "audience": self.settings.data_source_audience,
            "subject": self.settings.data_source_subject,
            "nonce": nonce,
            "tokenLifeMinutes": str(self.settings.data_source_token_life_minutes),
        }
        if include_active_status:
            payload["status"] = "active"
        return payload

    async def reconcile_data_source(
        self, customer_access_token: str
    ) -> tuple[dict[str, Any], str]:
        """Create one intended Data Source, or update the sole route match."""

        listed = await self._request(
            "GET", "/datasources", bearer=customer_access_token
        )
        listed_payload = self._json_response(listed)
        if isinstance(listed_payload, dict):
            items = listed_payload.get("items", [])
        elif isinstance(listed_payload, list):
            items = listed_payload
        else:
            items = []
        route_matches = [
            item for item in items if item.get("url") == self.settings.data_source_url
        ]
        if any(
            item.get("applicationId")
            and item.get("applicationId") != self.settings.service_app_client_id
            for item in route_matches
        ):
            raise WebexAPIError("The intended route belongs to another Service App")
        matches = route_matches
        if len(matches) > 1:
            raise WebexAPIError("Several Data Sources use the intended provider route")

        nonce = secrets.token_urlsafe(32)
        body = self.data_source_payload(nonce)
        if matches:
            data_source_id = quote(str(matches[0]["id"]), safe="")
            response = await self._request(
                "PUT",
                f"/datasources/{data_source_id}",
                bearer=customer_access_token,
                json=self.data_source_payload(nonce, include_active_status=True),
            )
        else:
            response = await self._request(
                "POST", "/datasources", bearer=customer_access_token, json=body
            )

        data_source = self._json_response(response)
        if not isinstance(data_source, dict):
            raise WebexAPIError("Data Source endpoint returned an invalid response")
        self._validate_data_source(data_source)
        return data_source, nonce

    async def renew_data_source(
        self, data_source_id: str, customer_access_token: str
    ) -> tuple[dict[str, Any], str]:
        nonce = secrets.token_urlsafe(32)
        response = await self._request(
            "PUT",
            f"/datasources/{quote(data_source_id, safe='')}",
            bearer=customer_access_token,
            json=self.data_source_payload(nonce, include_active_status=True),
        )
        data_source = self._json_response(response)
        if not isinstance(data_source, dict):
            raise WebexAPIError("Data Source endpoint returned an invalid response")
        self._validate_data_source(data_source)
        return data_source, nonce

    async def _token_request(self, form: dict[str, str]) -> dict[str, Any]:
        response = await self._request("POST", "/access_token", data=form)
        payload = self._json_response(response)
        if not isinstance(payload, dict):
            raise WebexAPIError("Webex token endpoint returned an invalid response")
        return payload

    def _parse_tokens(
        self,
        payload: dict[str, Any],
        previous_refresh_token: str | None = None,
    ) -> OAuthTokens:
        try:
            return OAuthTokens.from_api(payload, previous_refresh_token)
        except (TypeError, ValueError) as exc:
            raise WebexAPIError(
                "Webex token endpoint returned an invalid response"
            ) from exc

    @staticmethod
    def _json_response(response: httpx.Response) -> Any:
        try:
            return response.json()
        except ValueError as exc:
            raise WebexAPIError("Webex API returned a non-JSON response") from exc

    async def _request(
        self,
        method: str,
        path: str,
        *,
        bearer: str | None = None,
        json: dict[str, object] | None = None,
        data: dict[str, str] | None = None,
    ) -> httpx.Response:
        headers = {"Accept": "application/json"}
        if bearer:
            headers["Authorization"] = f"Bearer {bearer}"
        try:
            response = await self.http.request(
                method,
                f"{self.settings.webex_api_base_url}{path}",
                headers=headers,
                json=json,
                data=data,
            )
        except httpx.RequestError as exc:
            raise WebexAPIError("Webex API request failed") from exc
        if response.is_error:
            tracking_id = response.headers.get("trackingid")
            if not tracking_id:
                try:
                    tracking_id = response.json().get("trackingId")
                except (ValueError, AttributeError):
                    tracking_id = None
            suffix = f" (tracking ID: {tracking_id})" if tracking_id else ""
            raise WebexAPIError(
                f"Webex API returned HTTP {response.status_code}{suffix}"
            )
        return response

    def _validate_data_source(self, payload: dict[str, Any]) -> None:
        if not payload.get("id"):
            raise WebexAPIError("Data Source response is missing id")
        if payload.get("status") not in (None, "active"):
            raise WebexAPIError("Data Source did not become active")
        if payload.get("url") not in (None, self.settings.data_source_url):
            raise WebexAPIError("Data Source response contains an unexpected route")
        if payload.get("applicationId") not in (
            None,
            self.settings.service_app_client_id,
        ):
            raise WebexAPIError(
                "Data Source response contains an unexpected Service App"
            )
        schema_ids = payload.get("schemaId")
        if schema_ids is not None:
            normalized = schema_ids if isinstance(schema_ids, list) else [schema_ids]
            if self.settings.data_source_schema_id not in normalized:
                raise WebexAPIError(
                    "Data Source response contains an unexpected schema"
                )
