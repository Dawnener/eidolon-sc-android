"""Reach-out push events: the hub gives each open connection its own copy, and a reach-out
recorded is published to the user's connections; the stream itself needs a session."""

from dataclasses import replace
from datetime import timedelta

from eidolon.config import Settings
from eidolon.push import PushEvent, PushHub
from tests.conftest import ClientFactory
from tests.fakes import FakeLLM, FakeRandom
from tests.test_reach_outs import MIDDLE, REACH_OUT, backend_of, last_contact, talked


def event(character: str = "mano", user: str = "producer") -> PushEvent:
    return PushEvent(user, character, "c-1", "櫻木真乃", "她给你发来一条消息")


def test_each_connection_of_the_user_gets_the_event_and_no_one_else_does():
    hub = PushHub()

    with (
        hub.subscribe("producer") as one,
        hub.subscribe("producer") as two,
        hub.subscribe("someone") as other,
    ):
        hub.publish(event())

        assert one.get_nowait() == event()
        assert two.get_nowait() == event()
        assert other.empty()


def test_a_connection_that_left_is_no_longer_given_events():
    hub = PushHub()
    with hub.subscribe("producer") as queue:
        pass

    hub.publish(event())

    assert queue.empty()


async def test_a_reach_out_is_published_to_the_users_open_connection(
    make_client: ClientFactory, settings: Settings
):
    client, _, clock, conversation = await talked(
        make_client,
        REACH_OUT,
        settings=replace(settings, reach_outs=True),
        random=FakeRandom(MIDDLE),
    )
    last_contact(settings.database_path, conversation, clock.now - timedelta(hours=28))
    backend = backend_of(client)
    user = backend.store.first_user_named("producer")
    assert user is not None

    with backend.pushes.subscribe(user.id) as queue:
        await backend.reach_outs.run_due()

    pushed = queue.get_nowait()
    assert pushed.character_id == "mano"
    assert pushed.conversation_id == conversation
    assert pushed.title == "櫻木真乃"
    assert pushed.body


async def test_the_push_stream_needs_a_session(make_client: ClientFactory):
    anonymous = make_client(FakeLLM(), user=None)

    refused = await anonymous.get("/api/push/stream")

    assert refused.status_code == 401
