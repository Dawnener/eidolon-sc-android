"""The deployer's reach-out model: it is used for reach-outs apart from the chat model."""

from dataclasses import replace
from datetime import timedelta

from eidolon.config import LLMSettings, Settings
from tests.conftest import ClientFactory
from tests.fakes import FakeBackgroundLLM, FakeRandom, FakeServiceClients
from tests.test_reach_outs import (
    MIDDLE,
    REACH_OUT,
    backend_of,
    last_contact,
    reach_outs,
    record,
    talked,
)


async def test_reach_outs_use_the_deployers_own_reach_out_model(
    make_client: ClientFactory, settings: Settings
):
    reach_out = LLMSettings(
        "https://reach.example.com/v1", "reach-key", "reach-model", "chat-completions",
        reasoning_effort="low",
    )
    clients = FakeServiceClients()
    background = FakeBackgroundLLM(*(["".join(REACH_OUT)] * 20))
    client, _, clock, conversation = await talked(
        make_client,
        REACH_OUT,
        settings=replace(settings, reach_outs=True, reach_out_llm=reach_out),
        random=FakeRandom(MIDDLE, MIDDLE, MIDDLE, MIDDLE, MIDDLE),
        clients=clients,
        background_llm=background,
    )
    last_contact(settings.database_path, conversation, clock.now - timedelta(hours=28))

    await backend_of(client).reach_outs.run_due()

    [said] = reach_outs(await record(client, conversation))
    assert said["ja"] == "プロデューサーさん！レッスン終わったよ"
    assert reach_out in clients.memory_opened
    assert background.prompts
