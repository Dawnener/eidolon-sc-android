"""Events for clients keeping a connection open (a reach-out just came), in memory only.

The backend is a single process: a client's SSE connection subscribes here, and a reach-out
published on the event loop reaches every connection the user has open. Nothing is kept: a
client that was away when it happened finds the reach-out in the conversation as usual, and
subscribing again does not replay it.
"""

import asyncio
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass


@dataclass(frozen=True)
class PushEvent:
    """A reach-out said in one of the user's conversations."""

    user_id: str
    character_id: str
    conversation_id: str
    title: str
    body: str

    def shown(self) -> dict:
        return {
            "type": "reach_out",
            "character_id": self.character_id,
            "conversation_id": self.conversation_id,
            "title": self.title,
            "body": self.body,
        }


class PushHub:
    """The open push connections, by the user whose events they want."""

    def __init__(self) -> None:
        self._queues: dict[str, set[asyncio.Queue[PushEvent]]] = {}

    @contextmanager
    def subscribe(self, user_id: str) -> Iterator[asyncio.Queue[PushEvent]]:
        """A queue of the user's events while the block runs; one event per connection."""
        queue: asyncio.Queue[PushEvent] = asyncio.Queue(maxsize=32)
        self._queues.setdefault(user_id, set()).add(queue)
        try:
            yield queue
        finally:
            queues = self._queues.get(user_id)
            if queues is not None:
                queues.discard(queue)
                if not queues:
                    del self._queues[user_id]

    def publish(self, event: PushEvent) -> None:
        """Give `event` to every connection the user has open; a connection too far behind
        misses it rather than holding the others up."""
        for queue in tuple(self._queues.get(event.user_id, ())):
            try:
                queue.put_nowait(event)
            except asyncio.QueueFull:
                pass
