"""`/api/download/android`: the download page's button, always the newest APK."""

from pathlib import Path

from tests.conftest import ClientFactory
from tests.fakes import FakeLLM


def an_apk(directory: Path, name: str) -> None:
    (directory / name).write_bytes(b"apk")


async def test_download_android_redirects_to_the_newest_release(
    make_client: ClientFactory, tmp_path: Path, monkeypatch
):
    monkeypatch.setenv("EIDOLON_APK_DIR", str(tmp_path))
    an_apk(tmp_path, "Eidolon-SC-0.1.9-release.apk")
    an_apk(tmp_path, "Eidolon-SC-0.1.10-release.apk")
    an_apk(tmp_path, "Eidolon-SC-0.1.10-debug.apk")
    an_apk(tmp_path, "something-else.apk")

    res = await make_client(FakeLLM(), user=None).get("/api/download/android")

    assert res.status_code == 302
    assert res.headers["location"] == "/apk/native/release/Eidolon-SC-0.1.10-release.apk"
    assert res.headers["cache-control"] == "no-store"


async def test_download_android_says_so_when_there_is_no_apk(
    make_client: ClientFactory, tmp_path: Path, monkeypatch
):
    monkeypatch.setenv("EIDOLON_APK_DIR", str(tmp_path))

    res = await make_client(FakeLLM(), user=None).get("/api/download/android")

    assert res.status_code == 404
