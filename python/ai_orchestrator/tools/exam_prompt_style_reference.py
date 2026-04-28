from __future__ import annotations

import re
from dataclasses import dataclass
from functools import cached_property
from pathlib import Path
from typing import Protocol


@dataclass(frozen=True, slots=True)
class EssayPromptReference:
    paper: str
    title: str
    prompt_text: str
    exam_year: int | None
    image_description: str | None
    material_text: str | None
    task_type: str


class ExamPromptReferenceRepository(Protocol):
    def list_postgrad_references(self) -> tuple[EssayPromptReference, ...]:
        pass


@dataclass(frozen=True, slots=True)
class ExamStyleReference:
    study_stage: str
    display_name: str
    source_label: str
    task_type: str
    prompt_type: str
    sample_count: int
    years: tuple[int, ...]
    genre_notes: tuple[str, ...]
    prompt_structure: tuple[str, ...]
    writing_requirements: tuple[str, ...]
    style_notes: tuple[str, ...]

    def render(self) -> str:
        years = ", ".join(str(year) for year in self.years[:5]) or "未提取"
        return "\n".join(
            [
                f"[{self.display_name}题库风格参考]",
                f"- 来源: 基于{self.source_label}抽象出的风格信号，不是可复制题干。",
                f"- 匹配样本: {self.sample_count} 条；任务: {self.task_type}；题型: {self.prompt_type}；参考年份: {years}",
                "- 题材/体裁特征: " + "；".join(self.genre_notes),
                "- 常见题干结构: " + "；".join(self.prompt_structure),
                "- 写作要求特征: " + "；".join(self.writing_requirements),
                "- 语言风格: " + "；".join(self.style_notes),
                "- 生成边界: 生成原创题单；不要复制、改写或声称来自真实题库样本；不要输出范文、答案或提纲。",
            ]
        )


class PostgradPromptSeedRepository:
    def __init__(self, sql_path: Path | None = None) -> None:
        self.sql_path = sql_path or _default_postgrad_seed_path()

    def list_postgrad_references(self) -> tuple[EssayPromptReference, ...]:
        return self._references

    @cached_property
    def _references(self) -> tuple[EssayPromptReference, ...]:
        if not self.sql_path.exists():
            return ()
        text = self.sql_path.read_text(encoding="utf-8")
        references: list[EssayPromptReference] = []
        for row in _iter_insert_rows(text):
            values = _split_sql_values(row)
            if len(values) < 11 or values[0] != "4":
                continue
            task_type = values[8].strip().lower()
            if task_type not in {"task1", "task2"}:
                continue
            references.append(
                EssayPromptReference(
                    paper=values[1],
                    title=values[2],
                    prompt_text=values[3],
                    exam_year=_to_int(values[4]),
                    image_description=_none_if_null(values[6]),
                    material_text=_none_if_null(values[7]),
                    task_type=task_type,
                )
            )
        return tuple(references)


class ExamPromptStyleReferenceBuilder:
    def __init__(self, repository: ExamPromptReferenceRepository | None = None) -> None:
        self.repository = repository or PostgradPromptSeedRepository()

    def build(
        self,
        *,
        study_stage: str | None,
        task_type: str | None,
        prompt_type: str | None,
        topic: str | None,
    ) -> ExamStyleReference | None:
        if (study_stage or "").strip().lower() != "postgrad":
            return None

        requested_task_type = (task_type or "task1").strip().lower()
        requested_prompt_type = (prompt_type or "general").strip().lower()
        references = self.repository.list_postgrad_references()
        matches = self._match_references(
            references,
            task_type=requested_task_type,
            prompt_type=requested_prompt_type,
            topic=topic or "",
        )
        if not matches:
            return None

        inferred_prompt_type = requested_prompt_type
        return ExamStyleReference(
            study_stage="postgrad",
            display_name=_stage_display_name("postgrad"),
            source_label="本地考研英语真题题库",
            task_type=requested_task_type,
            prompt_type=inferred_prompt_type,
            sample_count=len(matches),
            years=tuple(year for item in matches if (year := item.exam_year) is not None),
            genre_notes=self._genre_notes(requested_task_type, inferred_prompt_type),
            prompt_structure=self._prompt_structure(requested_task_type, inferred_prompt_type),
            writing_requirements=self._writing_requirements(requested_task_type, inferred_prompt_type),
            style_notes=(
                "正式、简洁、考试化",
                "题干只说明任务和要求，不展开范文思路",
                "保留用户给定主题、指标、时间范围和材料设定",
            ),
        )

    def _match_references(
        self,
        references: tuple[EssayPromptReference, ...],
        *,
        task_type: str,
        prompt_type: str,
        topic: str,
    ) -> tuple[EssayPromptReference, ...]:
        candidates = [item for item in references if item.task_type == task_type]
        if prompt_type == "chart":
            candidates = [item for item in candidates if _looks_like_chart_reference(item)]
        elif prompt_type == "comic":
            candidates = [item for item in candidates if _looks_like_visual_reference(item)]
        elif prompt_type == "material":
            candidates = [item for item in candidates if item.material_text]

        topic_tokens = _topic_tokens(topic)
        scored = sorted(
            candidates,
            key=lambda item: (
                _topic_score(item, topic_tokens),
                item.exam_year or 0,
            ),
            reverse=True,
        )
        return tuple(scored[:5])

    def _genre_notes(self, task_type: str, prompt_type: str) -> tuple[str, ...]:
        if task_type == "task1":
            return ("应用文为主", "常见 email / letter / notice / reply", "强调写作对象、目的和必要细节")
        if prompt_type == "chart":
            return ("图表作文", "围绕数据变化、比较关系和社会现象评论", "可使用表格、柱状图、折线图或饼图")
        if prompt_type == "comic":
            return ("图画/漫画作文", "先描述画面，再解释寓意，最后给出评论")
        return ("议论或说明类作文", "围绕社会、校园、文化、成长等主题展开评论")

    def _prompt_structure(self, task_type: str, prompt_type: str) -> tuple[str, ...]:
        if task_type == "task1":
            return ("情境设定", "写作对象", "两点左右的明确任务", "约 100 words 的小作文要求")
        if prompt_type == "chart":
            return ("图表说明", "描述或解读图表", "结合趋势或现象给出评论", "160-200 words")
        if prompt_type == "comic":
            return ("图画/漫画说明", "简要描述画面", "解释寓意", "给出评论", "160-200 words")
        return ("主题说明", "明确写作任务", "要求解释意义并给出评论", "160-200 words")

    def _writing_requirements(self, task_type: str, prompt_type: str) -> tuple[str, ...]:
        if task_type == "task1":
            return ("信息完整", "语气符合收信人/通知对象", "目的清楚", "不写成议论文")
        if prompt_type == "chart":
            return ("概括主要数据特征", "解释趋势或对比", "发表简短评论", "不要写成 IELTS Task 1 纯客观报告")
        if prompt_type == "comic":
            return ("先描述画面", "再解释隐含意义", "最后表达评论或建议")
        return ("围绕主题解释意义", "观点明确", "用理由或例子支撑评论")


def _default_postgrad_seed_path() -> Path:
    return Path(__file__).resolve().parents[3] / "backend" / "src" / "main" / "resources" / "db" / "postgrad_prompt_seed.sql"


def _stage_display_name(study_stage: str) -> str:
    return {
        "primary": "小学英语",
        "junior": "初中英语",
        "senior": "高中英语",
        "highschool": "高中英语",
        "cet4": "大学英语四级",
        "cet6": "大学英语六级",
        "postgrad": "考研英语",
        "ielts": "IELTS",
        "toefl": "TOEFL",
    }.get(study_stage, study_stage)


def _iter_insert_rows(sql_text: str) -> list[str]:
    rows: list[str] = []
    for line in sql_text.splitlines():
        stripped = line.strip()
        if stripped.startswith("(4, "):
            rows.append(stripped.rstrip(",;"))
    return rows


def _split_sql_values(row: str) -> list[str]:
    content = row.strip()
    if content.startswith("(") and content.endswith(")"):
        content = content[1:-1]

    values: list[str] = []
    current: list[str] = []
    in_quote = False
    index = 0
    while index < len(content):
        char = content[index]
        if char == "'":
            if in_quote and index + 1 < len(content) and content[index + 1] == "'":
                current.append("'")
                index += 2
                continue
            in_quote = not in_quote
            index += 1
            continue
        if char == "," and not in_quote:
            values.append(_normalize_sql_value("".join(current)))
            current = []
            index += 1
            continue
        current.append(char)
        index += 1
    values.append(_normalize_sql_value("".join(current)))
    return values


def _normalize_sql_value(raw: str) -> str:
    return raw.strip()


def _none_if_null(value: str) -> str | None:
    if value.upper() == "NULL":
        return None
    return value


def _to_int(value: str) -> int | None:
    try:
        return int(value)
    except ValueError:
        return None


def _looks_like_chart_reference(item: EssayPromptReference) -> bool:
    text = f"{item.prompt_text} {item.image_description or ''}".lower()
    return any(keyword in text for keyword in ["chart", "table", "折线", "柱状", "饼图", "表格", "数据"])


def _looks_like_visual_reference(item: EssayPromptReference) -> bool:
    text = item.prompt_text.lower()
    return any(keyword in text for keyword in ["drawing", "picture", "photo", "pictures"])


def _topic_tokens(topic: str) -> set[str]:
    return {token.lower() for token in re.findall(r"[A-Za-z0-9]+|[\u4e00-\u9fff]{2,}", topic)}


def _topic_score(item: EssayPromptReference, topic_tokens: set[str]) -> int:
    if not topic_tokens:
        return 0
    text = f"{item.title} {item.prompt_text} {item.image_description or ''} {item.material_text or ''}".lower()
    return sum(1 for token in topic_tokens if token in text)
