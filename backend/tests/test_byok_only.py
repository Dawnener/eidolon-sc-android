"""BYOK-only deployments (ADR-0028): the server has no chat or memory model of its own, so
every text turn and call must bring the user's LLM; without one it is refused before
anything is recorded, and without speech keys a call is unavailable."""

import httpx

from eidolon.own_keys import OWN_LLM_REQUIRED, OWN_SPEECH_REQUIRED
from eidolon.service_status import probes
from tests.browser import Browser
from tests.conftest import ClientFactory
from tests.fakes import FakeLLM, FakeLLMService, FakeSpeech
from tests.test_conversation_titles import REPLY, TITLE, listed_titles
from tests.test_own_llm import LLM, MEMORY_KEY, MEMORY_LLM, THINKING
from tests.test_own_speech_keys import OWN_KEYS
from tests.test_text_turn import send_turn, start_conversation


def backend_of(client: httpx.AsyncClient):
    return client._transport.app.state.backend  # type: ignore[attr-defined]


async def test_a_turn_without_own_keys_is_refused_before_anything_is_recorded(
    make_client: ClientFactory,
):
    deployer = FakeLLM(REPLY)
    client = make_client(deployer, deployer_llm=False)
    conversation = await start_conversation(client)

    res = await client.post(
        f"/api/conversations/{conversation}/turns", json={"text": "你好"}
    )

    assert res.status_code == 429
    assert res.json() == {"detail": OWN_LLM_REQUIRED, "retry_at": None}
    assert deployer.keys == []


async def test_a_turn_with_own_keys_runs_without_a_deployer_chat_model(
    make_client: ClientFactory, own_llm: FakeLLMService
):
    deployer = FakeLLM(REPLY)
    own_llm.llm = FakeLLM(REPLY)
    client = make_client(deployer, deployer_llm=False)
    conversation = await start_conversation(client)

    events = await send_turn(client, conversation, "你好", own_llm=LLM)

    assert events[-1]["type"] == "done"
    assert own_llm.llm.keys == [LLM["api_key"]]
    assert deployer.keys == []


async def test_the_pre_call_check_refuses_without_an_own_llm(
    make_client: ClientFactory,
):
    client = make_client(FakeLLM(), deployer_llm=False, voice_configured=False)

    res = await client.get("/api/ice-servers", params={"own": ""})

    assert res.status_code == 429
    assert res.json() == {"detail": OWN_LLM_REQUIRED, "retry_at": None}


async def test_the_pre_call_check_asks_for_speech_when_the_deployer_has_none(
    make_client: ClientFactory,
):
    client = make_client(FakeLLM(), deployer_llm=False, voice_configured=False)

    res = await client.get("/api/ice-servers", params={"own": "llm"})

    assert res.status_code == 503
    assert res.json()["detail"] == OWN_SPEECH_REQUIRED


async def test_the_pre_call_check_passes_with_all_own_keys(
    make_client: ClientFactory,
):
    client = make_client(FakeLLM(), deployer_llm=False, voice_configured=False)

    res = await client.get("/api/ice-servers", params={"own": "llm,tts,stt"})

    assert res.status_code == 200


async def test_a_call_with_all_own_keys_works_without_a_deployer_chat_model(
    make_client: ClientFactory,
    own_llm: FakeLLMService,
    browser: Browser,
):
    own_llm.llm = FakeLLM(REPLY, REPLY, REPLY)
    deployer = FakeLLM()
    client = make_client(deployer, deployer_llm=False, voice_configured=False)
    conversation = await start_conversation(client)

    called = await browser.call(client, conversation_id=conversation, own_keys=OWN_KEYS)

    assert called.status_code == 200
    assert deployer.keys == []


async def test_service_status_probes_no_llms_without_a_deployer_chat_model(
    make_client: ClientFactory,
):
    client = make_client(FakeLLM(), deployer_llm=False)
    backend = backend_of(client)

    found = probes(backend.config, FakeSpeech(), "voice-id", None, None)

    assert "chat_llm" not in found
    assert "memory_llm" not in found


async def test_the_admin_configuration_reports_the_models_and_reach_outs_unavailable(
    make_client: ClientFactory,
):
    client = make_client(FakeLLM(), deployer_llm=False)
    config = backend_of(client).config

    described = config.describe()

    assert "unavailable" in described["chat_llm"]
    assert "unavailable" in described["memory_llm"]
    assert "unavailable" in described["reach_outs"]
    assert config.has_service("chat_llm") is False
    assert config.has_service("memory_llm") is False
    assert config.reach_outs.enabled is False
    lease = config.lease()
    assert lease.keys.chat_llm is None
    assert lease.keys.memory_llm is None
    lease.release()


async def test_titles_are_named_on_the_users_own_background_model(
    make_client: ClientFactory, own_llm: FakeLLMService
):
    own_llm.llm = FakeLLM(REPLY, REPLY, REPLY, [TITLE])
    deployer = FakeLLM()
    client = make_client(deployer, deployer_llm=False)
    conversation = await start_conversation(client)
    own_keys = {"llm": THINKING, "memory_llm": MEMORY_LLM}

    for text in ["第一句", "第二句", "第三句"]:
        assert (await send_turn(client, conversation, text, own_keys=own_keys))[-1]["type"] == "done"

    assert await listed_titles(client) == [TITLE]
    assert own_llm.llm.keys[-1] == MEMORY_KEY
    assert deployer.keys == []
