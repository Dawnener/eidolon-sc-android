"""The database: every connection to it is opened the same way, and every table in it
is either a user's data, deleted with the user, or not."""

import sqlite3
from pathlib import Path

from eidolon import database
from eidolon.config import Settings
from eidolon.data_access import Database
from eidolon.store import Store
from tests.conftest import ClientFactory
from tests.fakes import FakeEmbedder, FakeJudge, FakeLLM, FakeReranker

SOURCE = Path(database.__file__).parent


def test_a_connection_keeps_a_log_overwrites_what_is_deleted_and_checks_references(
    tmp_path: Path,
):
    db = database.connect(tmp_path / "eidolon.db")

    assert db.execute("PRAGMA journal_mode").fetchone() == ("wal",)
    assert db.execute("PRAGMA synchronous").fetchone() == (1,)  # NORMAL: no fsync a commit
    assert db.execute("PRAGMA secure_delete").fetchone() == (1,)
    assert db.execute("PRAGMA foreign_keys").fetchone() == (1,)
    assert db.execute("PRAGMA busy_timeout").fetchone() == (30000,)
    db.close()


def test_connecting_makes_the_directory_the_database_is_in(tmp_path: Path):
    path = tmp_path / "data" / "eidolon.db"

    database.connect(path).close()

    assert path.exists()


async def test_a_deployments_id_is_made_once_and_kept(tmp_path: Path):
    path = tmp_path / "eidolon.db"
    first_db = Database(path)
    first = Store(first_db)
    made = first.deployment_id()
    await first_db.close()

    again_db = Database(path)
    again = Store(again_db)
    try:
        assert again.deployment_id() == made
    finally:
        await again_db.close()
    assert made


async def test_two_deployments_have_different_ids(tmp_path: Path):
    one_db = Database(tmp_path / "one.db")
    two_db = Database(tmp_path / "two.db")
    one = Store(one_db)
    two = Store(two_db)
    try:
        assert one.deployment_id() != two.deployment_id()
    finally:
        await one_db.close()
        await two_db.close()


async def test_health_reports_the_deployments_stable_id(make_client: ClientFactory):
    client = make_client(FakeLLM(), user=None)
    first = (await client.get("/api/health")).json()
    second = (await client.get("/api/health")).json()

    assert first["ok"] is True
    assert first["deployment_id"]
    assert first["deployment_id"] == second["deployment_id"]


async def test_every_table_is_either_a_users_data_or_not(
    make_client: ClientFactory, settings: Settings
):
    make_client(FakeLLM(), retrieval_judge=FakeJudge(), embedder=FakeEmbedder(), reranker=FakeReranker())

    with sqlite3.connect(settings.database_path) as db:
        tables = {
            name
            for (name,) in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")
            if not name.startswith("sqlite_")
        }
    users = {table for table, _ in database.USER_DATA}
    users |= {table for table, *_ in database.ANONYMIZED_USER_DATA}
    assert not users & database.NOT_USER_DATA
    assert users | database.NOT_USER_DATA == tables


def test_only_the_database_module_connects_to_the_database():
    connecting = [
        path.relative_to(SOURCE).as_posix()
        for path in SOURCE.rglob("*.py")
        if "sqlite3.connect(" in path.read_text(encoding="utf-8")
    ]

    assert connecting == ["database.py"]
