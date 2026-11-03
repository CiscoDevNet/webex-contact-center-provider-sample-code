"""Environment-backed configuration with no secret values in representations."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from urllib.parse import urlparse

AUTOMATION_SCOPES = (
    "spark:applications_token",
    "application:webhooks_write",
    "application:webhooks_read",
)


def _required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise ValueError(f"Missing required environment variable: {name}")
    return value


def _https_url(name: str, value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise ValueError(f"{name} must be an absolute HTTPS URL")
    return value.rstrip("/")


@dataclass(frozen=True)
class Settings:
    """Runtime settings for one provider Integration and one Service App."""

    integration_client_id: str
    integration_client_secret: str = field(repr=False)
    integration_redirect_uri: str
    service_app_id: str
    service_app_client_id: str
    service_app_client_secret: str = field(repr=False)
    webhook_target_url: str
    webhook_secret: str = field(repr=False)
    data_source_url: str
    data_source_schema_id: str
    data_source_audience: str
    data_source_subject: str
    data_source_token_life_minutes: int = 60
    webex_api_base_url: str = "https://webexapis.com/v1"
    oauth_state_ttl_seconds: int = 600

    def __post_init__(self) -> None:
        _https_url("integration_redirect_uri", self.integration_redirect_uri)
        _https_url("webhook_target_url", self.webhook_target_url)
        _https_url("data_source_url", self.data_source_url)
        _https_url("webex_api_base_url", self.webex_api_base_url)
        if not 1 <= self.data_source_token_life_minutes <= 1440:
            raise ValueError(
                "data_source_token_life_minutes must be between 1 and 1440"
            )
        if self.oauth_state_ttl_seconds < 60:
            raise ValueError("oauth_state_ttl_seconds must be at least 60")

    @classmethod
    def from_env(cls) -> Settings:
        """Load and validate the sample's required environment variables."""

        return cls(
            webex_api_base_url=os.getenv(
                "WEBEX_API_BASE_URL", "https://webexapis.com/v1"
            ).rstrip("/"),
            integration_client_id=_required("INTEGRATION_CLIENT_ID"),
            integration_client_secret=_required("INTEGRATION_CLIENT_SECRET"),
            integration_redirect_uri=_required("INTEGRATION_REDIRECT_URI"),
            service_app_id=_required("SERVICE_APP_ID"),
            service_app_client_id=_required("SERVICE_APP_CLIENT_ID"),
            service_app_client_secret=_required("SERVICE_APP_CLIENT_SECRET"),
            webhook_target_url=_required("WEBHOOK_TARGET_URL"),
            webhook_secret=_required("WEBHOOK_SECRET"),
            data_source_url=_required("BYOVA_DATA_SOURCE_URL"),
            data_source_schema_id=os.getenv(
                "BYOVA_SCHEMA_ID", "5397013b-7920-4ffc-807c-e8a3e0a18f43"
            ),
            data_source_audience=os.getenv("BYOVA_AUDIENCE", "BYOVAGateway"),
            data_source_subject=os.getenv("BYOVA_SUBJECT", "callAudioData"),
            data_source_token_life_minutes=int(
                os.getenv("BYOVA_TOKEN_LIFE_MINUTES", "60")
            ),
            oauth_state_ttl_seconds=int(os.getenv("OAUTH_STATE_TTL_SECONDS", "600")),
        )
