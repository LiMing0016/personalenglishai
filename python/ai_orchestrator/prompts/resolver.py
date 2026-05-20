from __future__ import annotations

import json
import os
from typing import Any
from urllib.parse import urlparse


_VALID_PROMPT_SOURCES = {"local", "hybrid", "remote"}
_OPENAI_PLATFORM_HOSTS = {"api.openai.com"}


class PromptResolutionError(RuntimeError):
    pass


def resolve_agent_prompt_kwargs(agent_key: str, *, dynamic: bool = False) -> dict[str, Any]:
    source = _prompt_source()
    if source == "local":
        return _local_prompt_kwargs(agent_key, dynamic=dynamic)

    if not _uses_openai_platform_base_url():
        if source == "remote" or _remote_prompt_strict():
            raise PromptResolutionError(
                "OpenAI remote prompts require OPENAI_BASE_URL or AI_PROVIDER_OPENAI_BASE_URL to point to api.openai.com"
            )
        return _local_prompt_kwargs(agent_key, dynamic=dynamic)

    prompt = _remote_prompt(agent_key)
    if prompt is None:
        if source == "remote" or _remote_prompt_strict():
            raise PromptResolutionError(f"remote prompt is not configured for agent key: {agent_key}")
        return _local_prompt_kwargs(agent_key, dynamic=dynamic)

    return {"prompt": prompt}


def remote_prompt_env_names(agent_key: str) -> tuple[str, str, str]:
    normalized = _normalize_agent_key(agent_key)
    return (
        f"AI_PROMPT_{normalized}_ID",
        f"AI_PROMPT_{normalized}_VERSION",
        f"AI_PROMPT_{normalized}_VARIABLES_JSON",
    )


def _local_prompt_kwargs(agent_key: str, *, dynamic: bool = False) -> dict[str, Any]:
    from .agents import load_agent_instructions
    from .agents import load_dynamic_agent_instructions

    if dynamic:
        return {"instructions": load_dynamic_agent_instructions(agent_key)}
    return {"instructions": load_agent_instructions(agent_key)}


def _prompt_source() -> str:
    source = os.getenv("AI_ASSISTANT_PROMPT_SOURCE", "local").strip().lower() or "local"
    if source not in _VALID_PROMPT_SOURCES:
        raise PromptResolutionError(
            "AI_ASSISTANT_PROMPT_SOURCE must be one of: local, hybrid, remote"
        )
    return source


def _remote_prompt_strict() -> bool:
    return os.getenv("AI_ASSISTANT_REMOTE_PROMPT_STRICT", "false").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


def _uses_openai_platform_base_url() -> bool:
    base_url = (
        os.getenv("OPENAI_BASE_URL", "").strip()
        or os.getenv("AI_PROVIDER_OPENAI_BASE_URL", "").strip()
        or "https://api.openai.com"
    )
    hostname = urlparse(base_url).hostname
    return hostname in _OPENAI_PLATFORM_HOSTS


def _remote_prompt(agent_key: str) -> dict[str, Any] | None:
    id_env, version_env, variables_env = remote_prompt_env_names(agent_key)
    prompt_id = os.getenv(id_env, "").strip()
    if not prompt_id:
        return None

    prompt: dict[str, Any] = {"id": prompt_id}
    version = os.getenv(version_env, "").strip()
    if version:
        prompt["version"] = version

    variables = _remote_prompt_variables(variables_env)
    if variables:
        prompt["variables"] = variables

    return prompt


def _remote_prompt_variables(env_name: str) -> dict[str, Any] | None:
    raw = os.getenv(env_name, "").strip()
    if not raw:
        return None
    try:
        variables = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise PromptResolutionError(f"{env_name} must be valid JSON") from exc
    if not isinstance(variables, dict):
        raise PromptResolutionError(f"{env_name} must be a JSON object")
    return variables


def _normalize_agent_key(agent_key: str) -> str:
    return "".join(char if char.isalnum() else "_" for char in agent_key.upper()).strip("_")
