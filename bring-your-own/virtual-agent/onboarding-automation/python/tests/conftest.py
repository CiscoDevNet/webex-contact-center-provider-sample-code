from __future__ import annotations

from datetime import timedelta

import pytest

from byova_onboarding.config import Settings
from byova_onboarding.models import OAuthTokens, utc_now


@pytest.fixture
def settings() -> Settings:
    return Settings(
        webex_api_base_url="https://webexapis.example/v1",
        integration_client_id="integration-client",
        integration_client_secret="integration-secret",
        integration_redirect_uri="https://provider.example/oauth/callback",
        service_app_id="service-app-id",
        service_app_client_id="service-client",
        service_app_client_secret="service-secret",
        webhook_target_url="https://provider.example/webhooks/service-app",
        webhook_secret="webhook-secret",
        data_source_url="https://provider.example/byova",
        data_source_schema_id="schema-id",
        data_source_audience="BYOVAGateway",
        data_source_subject="callAudioData",
        data_source_token_life_minutes=60,
    )


@pytest.fixture
def integration_tokens() -> OAuthTokens:
    return OAuthTokens(
        access_token="integration-access",
        refresh_token="integration-refresh",
        expires_at=utc_now() + timedelta(hours=1),
    )
