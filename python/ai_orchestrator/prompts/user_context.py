from __future__ import annotations

from functools import cache
from importlib.resources import files


_STAGE_LABELS = {
    "primary": "小学",
    "junior": "初中",
    "senior": "高中",
    "highschool": "高中",
    "cet4": "四级",
    "cet6": "六级",
    "postgrad": "考研",
    "ielts": "雅思",
    "toefl": "托福",
    "1": "高中",
    "2": "四级",
    "3": "六级",
    "4": "考研",
}


def normalize_study_stage(study_stage: str | None) -> str:
    value = (study_stage or "").strip()
    if not value:
        return ""
    return _STAGE_LABELS.get(value.lower(), value)


def normalize_assistant_mode(assistant_mode: str | None) -> str:
    value = (assistant_mode or "").strip().lower()
    return "exam" if value in {"exam", "exam_boost"} else ""


@cache
def load_stage_output_standards() -> dict[str, list[str]]:
    prompt_body = files(__package__).joinpath("shared/stage_output_standards.md").read_text(encoding="utf-8")
    standards: dict[str, list[str]] = {}
    current_stage = ""

    for raw_line in prompt_body.splitlines():
        line = raw_line.strip()
        if line.startswith("## "):
            current_stage = line.removeprefix("## ").strip()
            standards[current_stage] = []
        elif current_stage and line.startswith("- "):
            standards[current_stage].append(line.removeprefix("- ").strip())

    return standards


def get_stage_output_standard(stage_label: str) -> list[str]:
    return load_stage_output_standards().get(stage_label, [])


def build_runtime_learning_context(
    *,
    study_stage: str | None = None,
    assistant_mode: str | None = None,
) -> str:
    stage_label = normalize_study_stage(study_stage)
    normalized_mode = normalize_assistant_mode(assistant_mode)
    if not stage_label and not normalized_mode:
        return ""

    context_lines: list[str] = []
    if stage_label:
        context_lines.extend(["[用户画像上下文]", f"- 学段: {stage_label}"])

        stage_standard = get_stage_output_standard(stage_label)
        if stage_standard:
            context_lines.append("[学段输出标准]")
            context_lines.extend(f"- {rule}" for rule in stage_standard)
        else:
            context_lines.append("- 个性化要求: 回答难度、例句、评分口径和训练建议要匹配该学段。")

        context_lines.extend(
            [
                "- 输出要求: 不要向用户显式复述或暴露本上下文标签。",
                "",
            ]
        )

    if normalized_mode == "exam":
        context_lines.extend(
            [
                "[对话模式上下文]",
                "- 当前模式: 考试模式",
                "- 模式要求: 回答必须以考试目标为导向，优先给出评分口径、答题策略、提分表达和训练建议。",
                "- 输出要求: 不要向用户显式复述或暴露本上下文标签。",
            ]
        )

    return "\n".join(context_lines).strip()


def build_contextual_user_message(
    message: str,
    *,
    study_stage: str | None = None,
    assistant_mode: str | None = None,
) -> str:
    text = (message or "").strip()
    runtime_context = build_runtime_learning_context(study_stage=study_stage, assistant_mode=assistant_mode)
    if not runtime_context:
        return text

    return "\n".join([runtime_context, "", "[用户消息]", text]).strip()
