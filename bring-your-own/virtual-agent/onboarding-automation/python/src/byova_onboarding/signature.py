"""Webhook signature verification helpers."""

from __future__ import annotations

import hashlib
import hmac
import re

_SHA1_HEX = re.compile(r"^[0-9a-fA-F]{40}$")


def verify_spark_signature(raw_body: bytes, signature: str | None, secret: str) -> bool:
    """Verify the documented X-Spark-Signature HMAC-SHA1 compatibility header."""

    if (
        not raw_body
        or not signature
        or not secret
        or not _SHA1_HEX.fullmatch(signature)
    ):
        return False
    expected = hmac.new(secret.encode("utf-8"), raw_body, hashlib.sha1).hexdigest()
    return hmac.compare_digest(expected, signature.lower())
