from __future__ import annotations

from importlib.resources import files

from agents.extensions.handoff_prompt import RECOMMENDED_PROMPT_PREFIX


_PROMPT_FILES = {
    "ability_profile": "agent_instructions/ability_profile.md",
    "evaluation": "agent_instructions/evaluation.md",
    "learning_planner": "agent_instructions/learning_planner.md",
    "polish": "agent_instructions/polish.md",
    "prompt_design": "agent_instructions/prompt_design.md",
    "prompt_sheet_canvas": "agent_instructions/prompt_sheet_canvas.md",
    "prompt_sheet_chat": "agent_instructions/prompt_sheet_chat.md",
    "router": "agent_instructions/router.md",
    "scoring": "agent_instructions/scoring.md",
    "sentence_structure": "agent_instructions/sentence_structure.md",
    "translation": "agent_instructions/translation.md",
    "translate_vocab": "agent_instructions/translate_vocab.md",
    "vocab": "agent_instructions/vocab.md",
}

_USER_CONTEXT_POLICY = """
用户画像上下文：
- 如果用户输入中包含「用户画像上下文」，必须将其作为个性化依据。
- 学段会影响讲解深度、例句难度、评分口径、训练建议和术语解释方式。
- 不要在回答中显式复述「用户画像上下文」这个标签。
""".strip()


def load_agent_instructions(agent_key: str) -> str:
    try:
        prompt_path = _PROMPT_FILES[agent_key]
    except KeyError as exc:
        raise ValueError(f"unknown agent prompt: {agent_key}") from exc

    prompt_body = files(__package__).joinpath(prompt_path).read_text(encoding="utf-8").strip()
    return f"{RECOMMENDED_PROMPT_PREFIX}\n{_USER_CONTEXT_POLICY}\n\n{prompt_body}"
