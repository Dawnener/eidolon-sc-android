"""A user's own model for reach-outs, kept on the server encrypted: tried before it is kept,
never sent back, and used for reach-outs while the user is away; a deployment without a
model of its own can still reach out to users who left one."""

import sqlite3
from dataclasses import replace
from datetime import UTC, datetime, timedelta

from eidolon.config import Settings
from tests.conftest import ClientFactory
from tests.fakes import FakeClock, FakeLLM, FakeLLMService, FakeRandom
from tests.test_reach_outs import (
    MIDDLE,
    REACH_OUT,
    afternoon,
    backend_of,
    last_contact,
    live_in,
    reach_outs,
    record,
)
from tests.test_text_turn import send_turn, start_conversation

REPLY = ["<ja>はい</ja><zh>好的</zh>"]
MINE = "sk-reach-out-key-1234"
LLM = {
    "base_url": "https://llm.example.com/v1",
    "api_key": MINE,
    "model": "my-model",
    "protocol": "chat-completions",
}


async def test_a_reach_out_model_is_tried_kept_encrypted_and_never_returned(
    make_client: ClientFactory, own_llm: FakeLLMService, settings: Settings
):
    own_llm.llm = FakeLLM(REPLY)
    client = make_client(FakeLLM(), deployer_llm=False)

    saved = await client.put("/api/account/reach-out-llm", json=LLM)

    assert saved.status_code == 204
    assert [(s.text.api_key, s.text.base_url, s.text.model) for s in own_llm.checked] == [
        (MINE, "https://llm.example.com/v1", "my-model")
    ]
    shown = await client.get("/api/account/reach-out-llm")
    assert shown.json() == {
        "configured": True,
        "base_url": "https://llm.example.com/v1",
        "model": "my-model",
        "protocol": "chat-completions",
        "last4": "1234",
    }
    assert MINE not in shown.text
    with sqlite3.connect(settings.database_path) as db:
        [kept] = db.execute("SELECT settings FROM reach_out_models").fetchone()
    assert MINE not in kept

    assert (await client.delete("/api/account/reach-out-llm")).status_code == 204
    assert (await client.get("/api/account/reach-out-llm")).json() == {"configured": False}


async def test_without_a_secret_key_the_model_cannot_be_kept(
    make_client: ClientFactory, settings: Settings
):
    client = make_client(
        FakeLLM(), deployer_llm=False, settings=replace(settings, secret_key=None)
    )

    saved = await client.put("/api/account/reach-out-llm", json=LLM)

    assert saved.status_code == 503
    assert "EIDOLON_SECRET_KEY" in saved.json()["detail"]


async def test_a_byok_only_deployment_reaches_out_on_the_users_kept_model(
    make_client: ClientFactory, own_llm: FakeLLMService, settings: Settings
):
    own_llm.llm = FakeLLM(REPLY, REACH_OUT)
    clock = FakeClock(datetime.now(UTC))
    client = make_client(
        FakeLLM(),
        deployer_llm=False,
        settings=replace(settings, reach_outs=True),
        clock=clock,
        random=FakeRandom(MIDDLE),
    )
    await live_in(client, afternoon(clock.now))
    conversation = await start_conversation(client)
    await send_turn(client, conversation, "我去练习了", own_llm=LLM)
    assert (await client.put("/api/account/reach-out-llm", json=LLM)).status_code == 204
    last_contact(settings.database_path, conversation, clock.now - timedelta(hours=28))

    await backend_of(client).reach_outs.run_due()

    [said] = reach_outs(await record(client, conversation))
    assert said["ja"] == "プロデューサーさん！レッスン終わったよ"
    assert any(
        opened.api_key == MINE and opened.model == "my-model" for opened in own_llm.opened
    )


async def test_without_a_model_of_either_kind_there_is_no_reach_out(
    make_client: ClientFactory, own_llm: FakeLLMService, settings: Settings
):
    own_llm.llm = FakeLLM(REPLY)
    clock = FakeClock(datetime.now(UTC))
    client = make_client(
        FakeLLM(),
        deployer_llm=False,
        settings=replace(settings, reach_outs=True),
        clock=clock,
        random=FakeRandom(MIDDLE),
    )
    await live_in(client, afternoon(clock.now))
    conversation = await start_conversation(client)
    await send_turn(client, conversation, "我去练习了", own_llm=LLM)
    last_contact(settings.database_path, conversation, clock.now - timedelta(hours=28))

    await backend_of(client).reach_outs.run_due()

    assert reach_outs(await record(client, conversation)) == []
