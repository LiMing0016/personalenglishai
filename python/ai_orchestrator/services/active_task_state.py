from __future__ import annotations

from typing import Protocol

from python.ai_orchestrator.schemas.routing_state import ActiveTaskState


class ActiveTaskStateStore(Protocol):
    def get(self, conversation_id: str) -> ActiveTaskState | None:
        ...

    def save(self, state: ActiveTaskState) -> None:
        ...

    def clear(self, conversation_id: str) -> None:
        ...


class InMemoryActiveTaskStateStore:
    def __init__(self) -> None:
        self._states: dict[str, ActiveTaskState] = {}

    def get(self, conversation_id: str) -> ActiveTaskState | None:
        return self._states.get(conversation_id)

    def save(self, state: ActiveTaskState) -> None:
        self._states[state.conversation_id] = state

    def clear(self, conversation_id: str) -> None:
        self._states.pop(conversation_id, None)
