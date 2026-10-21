"""Users' own model for her reach-outs (GLOSSARY.md「搭话」), kept on the server, encrypted.

Reach-outs are generated while the user is away, so their browser cannot bring the key
with a request as it does for turns (ADR-0017). A user may instead leave one here: a model
of their own that only reach-outs run on. It is encrypted with the deployment's secret key
and never sent back; the server can decrypt it, which is the price of reaching out while
the user is offline (ADR-0030).

Nothing here is a turn's model: text turns and calls still bring their own keys from the
browser, and a deployment that configured no model of its own stays BYOK-only for them.
"""

import base64
import json
import sqlite3
from datetime import UTC, datetime
from pathlib import Path

from cryptography.fernet import Fernet, InvalidToken
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from eidolon.config import LLMSettings
from eidolon.database import connect

_SCHEMA = """
-- One reach-out model for each user who left one, encrypted; see `reach_out_models`.
CREATE TABLE IF NOT EXISTS reach_out_models (
    user_id TEXT PRIMARY KEY REFERENCES users(id),
    settings TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
"""


class NoSecretKey(Exception):
    """The deployment has no `EIDOLON_SECRET_KEY`, so nothing can be kept encrypted."""


class ReachOutModels:
    """Users' reach-out models, encrypted with the deployment's secret key, on the database
    at `path`."""

    def __init__(self, path: Path, secret_key: str | None) -> None:
        self._db: sqlite3.Connection = connect(path)
        self._db.row_factory = sqlite3.Row
        self._cipher = _cipher(secret_key) if secret_key else None
        with self._db:
            self._db.executescript(_SCHEMA)

    @property
    def configured(self) -> bool:
        """Whether the deployment can keep an encrypted model at all."""
        return self._cipher is not None

    def save(self, user_id: str, settings: LLMSettings) -> None:
        """Keep `settings` for `user_id`, replacing one kept before; raise `NoSecretKey`
        where the deployment has none."""
        if self._cipher is None:
            raise NoSecretKey
        token = self._cipher.encrypt(_encode(settings).encode()).decode()
        with self._db:
            self._db.execute(
                "INSERT OR REPLACE INTO reach_out_models (user_id, settings, updated_at)"
                " VALUES (?, ?, ?)",
                (user_id, token, datetime.now(UTC).isoformat()),
            )

    def load(self, user_id: str) -> LLMSettings | None:
        """The model kept for `user_id`; None if none, or if it can no longer be read, in
        which case it is deleted."""
        if self._cipher is None:
            return None
        with self._db:
            row = self._db.execute(
                "SELECT settings FROM reach_out_models WHERE user_id = ?", (user_id,)
            ).fetchone()
            if row is None:
                return None
            try:
                return _decode(self._cipher.decrypt(row["settings"].encode()).decode())
            except (InvalidToken, ValueError, KeyError, TypeError):
                self._db.execute("DELETE FROM reach_out_models WHERE user_id = ?", (user_id,))
                return None

    def describe(self, user_id: str) -> dict | None:
        """What the settings page is told of the model, without the key itself."""
        settings = self.load(user_id)
        if settings is None:
            return None
        return {
            "base_url": settings.base_url,
            "model": settings.model,
            "protocol": settings.protocol,
            "last4": settings.api_key[-4:],
        }

    def delete(self, user_id: str) -> None:
        with self._db:
            self._db.execute("DELETE FROM reach_out_models WHERE user_id = ?", (user_id,))

    def close(self) -> None:
        self._db.close()


def _encode(settings: LLMSettings) -> str:
    return json.dumps(
        {
            "base_url": settings.base_url,
            "api_key": settings.api_key,
            "model": settings.model,
            "protocol": settings.protocol,
            "max_tokens": settings.max_tokens,
            "reasoning_effort": settings.reasoning_effort,
            "extra_body": settings.extra_body,
        },
        ensure_ascii=False,
    )


def _decode(plain: str) -> LLMSettings:
    values = json.loads(plain)
    if not isinstance(values, dict):
        raise ValueError("not an object")
    return LLMSettings(
        base_url=values["base_url"],
        api_key=values["api_key"],
        model=values["model"],
        protocol=values.get("protocol", "chat-completions"),
        max_tokens=values.get("max_tokens"),
        reasoning_effort=values.get("reasoning_effort"),
        extra_body=values.get("extra_body"),
    )


def _cipher(secret_key: str) -> Fernet:
    """The cipher for users' reach-out models, derived from the deployment's secret key
    apart from the ones for the ban list and the admin's overrides."""
    derived = HKDF(
        algorithm=hashes.SHA256(), length=32, salt=None, info=b"eidolon reach-out models"
    ).derive(secret_key.encode())
    return Fernet(base64.urlsafe_b64encode(derived))
