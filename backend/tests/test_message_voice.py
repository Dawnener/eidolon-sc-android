"""A chat message voiced on demand: the text stays the message's own, and the audio is
made on the deployer's Fish Audio key."""

from tests.conftest import ClientFactory
from tests.fakes import FakeLLM, FakeSpeech
from tests.test_reach_outs import REPLY, talked


async def test_a_character_message_can_be_voiced_for_its_owner(
    make_client: ClientFactory, speech: FakeSpeech
):
    client, _, _, conversation = await talked(make_client, REPLY)
    messages = (await client.get(f"/api/conversations/{conversation}/messages")).json()
    reply = next(message for message in messages if message["role"] == "character")

    res = await client.get(
        f"/api/conversations/{conversation}/messages/{reply['id']}/voice"
    )

    assert res.status_code == 200
    assert res.headers["content-type"] == "audio/mpeg"
    assert res.content.startswith(b"mp3:")
    assert speech.voiced


async def test_a_message_cannot_be_voiced_for_someone_else(
    make_client: ClientFactory, speech: FakeSpeech
):
    owner, _, _, conversation = await talked(make_client, REPLY)
    messages = (await owner.get(f"/api/conversations/{conversation}/messages")).json()
    reply = next(message for message in messages if message["role"] == "character")
    stranger = make_client(FakeLLM(), user="stranger")

    res = await stranger.get(
        f"/api/conversations/{conversation}/messages/{reply['id']}/voice"
    )

    assert res.status_code == 404
