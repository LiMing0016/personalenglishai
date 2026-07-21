from __future__ import annotations

from importlib.resources import files
from typing import Any


_PROMPT_FILES = {
    "ability_profile": "agent_instructions/ability_profile.md",
    "attachment": "agent_instructions/attachment.md",
    "evaluation": "agent_instructions/evaluation.md",
    "learning_planner": "agent_instructions/learning_planner.md",
    "learning_asset_copilot": "agent_instructions/learning_asset_copilot.md",
    "polish": "agent_instructions/polish.md",
    "prompt_design": "agent_instructions/prompt_design.md",
    "prompt_sheet_canvas": "agent_instructions/prompt_sheet_canvas.md",
    "prompt_sheet_chat": "agent_instructions/prompt_sheet_chat.md",
    "route_decision": "agent_instructions/route_decision.md",
    "sentence_reorder": "agent_instructions/sentence_reorder.md",
    "router": "agent_instructions/router.md",
    "scoring": "agent_instructions/scoring.md",
    "sentence_structure": "agent_instructions/sentence_structure.md",
    "translation": "agent_instructions/translation.md",
    "translate_vocab": "agent_instructions/translate_vocab.md",
    "vocab": "agent_instructions/vocab.md",
    "writing_coach_stage": "agent_instructions/writing_coach_stage.md",
    "writing_coach_route": "agent_instructions/writing_coach_route.md",
}

_STRUCTURED_OUTPUT_ONLY_AGENT_KEYS = frozenset(
    {
        "prompt_sheet_canvas",
        "route_decision",
        "sentence_reorder",
        "writing_coach_stage",
        "writing_coach_route",
    }
)

_AGENTS_SDK_HANDOFF_PROMPT_PREFIX = """
# 系统上下文
你是一个基于 OpenAI Agents SDK 的多智能体系统中的一部分。这个系统用于让多个智能体的协调和执行更容易。

Agents SDK 使用两个主要抽象：智能体和转交。智能体包含指令和工具，并且可以在合适的时候把对话转交给另一个智能体。转交通过调用转交函数完成，函数名通常类似 `transfer_to_<agent_name>`。

智能体之间的转交通常在后台自动完成；与用户对话时，不要提及这些转交，也不要让用户注意到内部转交流程。
""".strip()

_USER_CONTEXT_POLICY = """
用户画像上下文：
- 如果用户输入中包含「用户画像上下文」，必须将其作为个性化依据。
- 学段会影响讲解深度、例句难度、评分口径、训练建议和术语解释方式。
- 不要在回答中显式复述「用户画像上下文」这个标签。
""".strip()


def _load_shared_markdown_output_policy() -> str:
    return files(__package__).joinpath("shared/assistant_markdown_output.md").read_text(encoding="utf-8").strip()


def _should_include_markdown_output_policy(agent_key: str) -> bool:
    return agent_key not in _STRUCTURED_OUTPUT_ONLY_AGENT_KEYS


def load_agent_instructions(agent_key: str) -> str:
    try:
        prompt_path = _PROMPT_FILES[agent_key]
    except KeyError as exc:
        raise ValueError(f"unknown agent prompt: {agent_key}") from exc

    prompt_body = files(__package__).joinpath(prompt_path).read_text(encoding="utf-8").strip()
    sections = [_AGENTS_SDK_HANDOFF_PROMPT_PREFIX, _USER_CONTEXT_POLICY]
    if _should_include_markdown_output_policy(agent_key):
        sections.append(_load_shared_markdown_output_policy())
    sections.append(prompt_body)
    return "\n\n".join(sections)


def load_dynamic_agent_instructions(agent_key: str):
    def dynamic_instructions(context_wrapper: Any, agent: Any) -> str:
        instructions = load_agent_instructions(agent_key)
        runtime_context = _render_runtime_learning_context(getattr(context_wrapper, "context", None))
        if not runtime_context:
            return instructions
        return f"{instructions}\n\n# Runtime Learning Context\n\n{runtime_context}"

    return dynamic_instructions


def _render_runtime_learning_context(run_context: Any) -> str:
    if run_context is None:
        return ""

    from .user_context import build_runtime_learning_context

    return build_runtime_learning_context(
        study_stage=getattr(run_context, "study_stage", None),
        assistant_mode=getattr(run_context, "assistant_mode", None),
    )
