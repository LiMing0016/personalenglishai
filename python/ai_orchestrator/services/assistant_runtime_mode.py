from __future__ import annotations

import os
from dataclasses import dataclass

from ..schemas.assistant_request import AgentMode


_VALID_AGENT_MODES = {"multi_agent", "single_agent_raw"}


def _parse_bool(value: str | None) -> bool:
    return (value or "").strip().lower() in {"1", "true", "yes", "on"}


def _parse_agent_mode(value: str | None, *, default: AgentMode = "multi_agent") -> AgentMode:
    normalized = (value or "").strip() or default
    if normalized not in _VALID_AGENT_MODES:
        raise ValueError(f"Unsupported AI assistant agent mode: {normalized}")
    return normalized  # type: ignore[return-value]


@dataclass(frozen=True, slots=True)
class AssistantRuntimeModeResolver:
    default_mode: AgentMode = "multi_agent"
    request_override_enabled: bool = False

    @classmethod
    def from_env(cls) -> "AssistantRuntimeModeResolver":
        return cls(
            default_mode=_parse_agent_mode(os.getenv("AI_ASSISTANT_AGENT_MODE")),
            request_override_enabled=_parse_bool(
                os.getenv("AI_ASSISTANT_AGENT_MODE_REQUEST_OVERRIDE_ENABLED")
            ),
        )

    def resolve(self, requested_mode: AgentMode | None) -> AgentMode:
        if self.request_override_enabled and requested_mode is not None:
            return requested_mode
        return self.default_mode


def build_session_key(mode: AgentMode, conversation_id: str) -> str:
    prefix = "single-raw" if mode == "single_agent_raw" else "multi"
    return f"{prefix}:{conversation_id}"
