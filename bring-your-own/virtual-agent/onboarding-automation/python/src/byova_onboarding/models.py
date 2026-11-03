"""Small domain models used by the sample."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Any


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def parse_webex_time(value: str) -> datetime:
    """Parse a Webex ISO-8601 timestamp into an aware UTC datetime."""

    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        raise ValueError("timestamp must include a timezone")
    return parsed.astimezone(timezone.utc)


@dataclass
class OAuthTokens:
    access_token: str = field(repr=False)
    refresh_token: str | None = field(default=None, repr=False)
    expires_at: datetime = field(default_factory=utc_now)
    refresh_token_expires_at: datetime | None = None

    @classmethod
    def from_api(
        cls,
        payload: dict[str, Any],
        previous_refresh_token: str | None = None,
    ) -> OAuthTokens:
        access_token = payload.get("access_token")
        if not isinstance(access_token, str) or not access_token:
            raise ValueError("token response is missing access_token")

        now = utc_now()
        expires_in = int(payload.get("expires_in", 0))
        refresh_expires_in = payload.get("refresh_token_expires_in")
        refresh_token = payload.get("refresh_token") or previous_refresh_token
        return cls(
            access_token=access_token,
            refresh_token=refresh_token,
            expires_at=now + timedelta(seconds=expires_in),
            refresh_token_expires_at=(
                now + timedelta(seconds=int(refresh_expires_in))
                if refresh_expires_in is not None
                else None
            ),
        )

    def needs_refresh(self, margin_seconds: int = 60) -> bool:
        return self.expires_at <= utc_now() + timedelta(seconds=margin_seconds)


@dataclass(frozen=True)
class LifecycleEvent:
    event: str
    service_app_id: str
    customer_org_id: str
    authorization_date: datetime
    event_key: str


@dataclass
class CustomerRecord:
    record_id: str
    service_app_id: str
    customer_org_id: str = field(repr=False)
    generation: int = 0
    status: str = "pending"
    lifecycle_at: datetime | None = None
    processed_event_keys: set[str] = field(default_factory=set, repr=False)
    service_app_tokens: OAuthTokens | None = field(default=None, repr=False)
    data_source_id: str | None = None
    data_source_status: str | None = None
    data_source_jws: str | None = field(default=None, repr=False)
    data_source_token_expiry_time: str | None = None
    data_source_nonce: str | None = field(default=None, repr=False)
    last_error: str | None = None

    def erase_credentials(self) -> None:
        self.service_app_tokens = None
        self.data_source_jws = None
        self.data_source_nonce = None
