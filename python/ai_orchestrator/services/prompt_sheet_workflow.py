from __future__ import annotations

import json
import logging
import os
import time
from functools import lru_cache
from importlib.resources import files

from agents import Agent, Runner, RunContextWrapper, function_tool

from ..prompts.agents import load_agent_instructions
from ..services.agent_session_runner import extract_run_items
from ..services.agent_session_runner import extract_usage
from ..schemas.prompt_sheet import GenerateExamPromptRequest
from ..schemas.prompt_sheet import GenerateExamPromptResponse
from ..schemas.prompt_sheet import ChartSpec
from ..schemas.prompt_sheet import PromptSheetChatRequest
from ..schemas.prompt_sheet import PromptSheetChatResponse
from ..schemas.prompt_sheet import PromptSheetCanvasToolInput
from ..tools.exam_prompt_style_reference import ExamPromptStyleReferenceBuilder


log = logging.getLogger("uvicorn.error")

_EXAM_STANDARD_ASSET = "shared/prompt_sheet_exam_standards.md"
_PROMPT_TYPE_STANDARD_ASSET = "shared/prompt_sheet_prompt_type_standards.md"

_STAGE_ALIASES = {
    "primary": "primary",
    "小学": "primary",
    "junior": "junior",
    "初中": "junior",
    "senior": "senior",
    "高中": "senior",
    "cet4": "cet4",
    "四级": "cet4",
    "大学英语四级": "cet4",
    "cet6": "cet6",
    "六级": "cet6",
    "大学英语六级": "cet6",
    "postgrad": "postgrad",
    "考研": "postgrad",
    "考研英语": "postgrad",
    "ielts": "ielts",
    "雅思": "ielts",
    "toefl": "toefl",
    "托福": "toefl",
}


@lru_cache(maxsize=32)
def _load_shared_prompt_asset(asset_path: str) -> str:
    return files("python.ai_orchestrator.prompts").joinpath(asset_path).read_text(encoding="utf-8")


@lru_cache(maxsize=128)
def _load_shared_prompt_section(asset_path: str, section_key: str | None) -> str | None:
    key = (section_key or "").strip().lower()
    if not key:
        return None
    current_key: str | None = None
    lines: list[str] = []
    for raw_line in _load_shared_prompt_asset(asset_path).splitlines():
        if raw_line.startswith("## "):
            if current_key == key:
                break
            current_key = raw_line[3:].strip().lower()
            lines = []
            continue
        if current_key == key:
            lines.append(raw_line)
    section = "\n".join(lines).strip()
    if not section:
        return None
    return f"[{key} 题单标准]\n{section}"


class PromptSheetWorkflowConfigError(RuntimeError):
    pass


class PromptSheetWorkflowService:
    def __init__(self, *, model: str, style_reference_builder: ExamPromptStyleReferenceBuilder | None = None) -> None:
        self.model = model
        self._style_reference_builder = style_reference_builder or ExamPromptStyleReferenceBuilder()
        self._canvas_agent = None
        self._chat_agent = None

    @classmethod
    def from_env(cls) -> "PromptSheetWorkflowService":
        model = (
            os.getenv("AI_ASSISTANT_MODEL", "").strip()
            or os.getenv("AI_PROVIDER_OPENAI_MODEL", "").strip()
            or "gpt-5.4-mini"
        )
        return cls(model=model)

    def is_configured(self) -> bool:
        return bool(os.getenv("OPENAI_API_KEY", "").strip())

    def _require_configured(self) -> None:
        if not self.is_configured():
            raise PromptSheetWorkflowConfigError("OPENAI_API_KEY 未配置，题单 Agent 暂时不可用。")

    def _get_canvas_agent(self):
        self._require_configured()
        if self._canvas_agent is None:
            self._canvas_agent = Agent(
                name="Prompt Sheet Canvas Agent",
                model=self.model,
                instructions=load_agent_instructions("prompt_sheet_canvas"),
                output_type=GenerateExamPromptResponse,
            )
        return self._canvas_agent

    def _get_chat_agent(self):
        self._require_configured()
        if self._chat_agent is None:
            self._chat_agent = Agent(
                name="Prompt Sheet Chat Agent",
                model=self.model,
                instructions=load_agent_instructions("prompt_sheet_chat"),
                tools=[self._get_canvas_tool()],
                output_type=PromptSheetChatResponse,
            )
        return self._chat_agent

    def _get_canvas_tool(self):
        @function_tool(
            name_override="generate_prompt_sheet_canvas",
            description_override="Generate or update the structured writing prompt sheet shown on the right canvas.",
        )
        async def generate_prompt_sheet_canvas(
            context: RunContextWrapper[PromptSheetChatRequest],
            request: PromptSheetCanvasToolInput,
        ) -> str:
            chat_request = context.context
            if isinstance(chat_request, PromptSheetChatRequest):
                canvas_request = self._build_canvas_request_from_chat_tool_input(chat_request, request)
            else:
                canvas_request = GenerateExamPromptRequest(
                    originalInput=request.instruction,
                    topic=request.topic or request.instruction,
                    promptType=self._infer_prompt_type_from_tool_input(request, None),
                    taskType=request.task_type,
                    genre=request.genre,
                    wordRange=request.word_range,
                    requirements=request.requirements,
                    preserveDetails=request.preserve_details,
                )
            started_at = time.perf_counter()
            result = await Runner.run(self._get_canvas_agent(), self._build_agent_input(canvas_request))
            self._log_openai_agents_run(
                workflow="prompt_sheet_canvas_tool",
                result=result,
                started_at=started_at,
                request_context={
                    "study_stage": canvas_request.study_stage or "",
                    "task_type": canvas_request.task_type or "",
                    "prompt_type": canvas_request.prompt_type or "",
                },
            )
            response = self._normalize_generate_response(
                self._coerce_generate_response(getattr(result, "final_output", None)),
                canvas_request,
            )
            return response.model_dump_json(by_alias=True)

        return generate_prompt_sheet_canvas

    async def chat(self, request: PromptSheetChatRequest) -> PromptSheetChatResponse:
        started_at = time.perf_counter()
        agent_input = self._build_agent_input(request)
        result = await Runner.run(self._get_chat_agent(), agent_input, context=request)
        self._log_openai_agents_run(
            workflow="prompt_sheet_chat",
            result=result,
            started_at=started_at,
            request_context={
                "study_stage": request.study_stage or "",
                "task_type": request.task_type or "",
                "prompt_type": request.prompt_type or "",
                "has_canvas": bool(request.has_canvas),
            },
        )
        response = self._coerce_chat_response(getattr(result, "final_output", None))
        response = self._normalize_chat_response(response)
        log.info(
            "[PROMPT_SHEET_CHAT] action=%s canvas_update=%s has_prompt_sheet=%s",
            response.action,
            response.needs_canvas_update,
            response.prompt_sheet is not None,
        )
        return response

    async def generate(self, request: GenerateExamPromptRequest) -> GenerateExamPromptResponse:
        started_at = time.perf_counter()
        agent_input = self._build_agent_input(request)
        result = await Runner.run(self._get_canvas_agent(), agent_input)
        self._log_openai_agents_run(
            workflow="prompt_sheet_generate",
            result=result,
            started_at=started_at,
            request_context={
                "study_stage": request.study_stage or "",
                "task_type": request.task_type or "",
                "prompt_type": request.prompt_type or "",
            },
        )
        response = self._coerce_generate_response(getattr(result, "final_output", None))
        response = self._normalize_generate_response(response, request)
        log.info(
            "[PROMPT_SHEET_GENERATE] prompt_type=%s task_type=%s topic_chars=%s",
            response.prompt_type,
            response.task_type or "",
            len(response.topic or ""),
        )
        return response

    def _build_agent_input(self, request: PromptSheetChatRequest | GenerateExamPromptRequest) -> str:
        return self._render_agent_input(self._build_agent_payload(request))

    def _build_agent_payload(self, request: PromptSheetChatRequest | GenerateExamPromptRequest) -> dict[str, object]:
        payload = request.model_dump(mode="json", by_alias=True)
        exam_prompt_standard = self._exam_prompt_standard(request)
        if exam_prompt_standard:
            payload["examPromptStandard"] = exam_prompt_standard
        prompt_type_standard = self._prompt_type_standard(request)
        if prompt_type_standard:
            payload["promptTypeStandard"] = prompt_type_standard
        style_reference = self._style_reference_builder.build(
            study_stage=getattr(request, "study_stage", None),
            task_type=getattr(request, "task_type", None),
            prompt_type=getattr(request, "prompt_type", None),
            topic=self._style_reference_topic(request),
        )
        if style_reference is not None:
            payload["examStyleReference"] = style_reference.render()
        return payload

    def _render_agent_input(self, payload: dict[str, object]) -> str:
        runtime_sections = {
            "examPromptStandard",
            "promptTypeStandard",
            "examStyleReference",
        }
        request_payload = {
            key: value
            for key, value in payload.items()
            if key not in runtime_sections and value not in (None, "", [], {})
        }
        sections = [
            "# Prompt Sheet Agent Input",
            "",
            "## Request",
            "",
            "```json",
            json.dumps(request_payload, ensure_ascii=False, indent=2),
            "```",
        ]
        for key in ["examPromptStandard", "promptTypeStandard", "examStyleReference"]:
            value = payload.get(key)
            if not value:
                continue
            sections.extend(["", f"## {key}", "", str(value)])
        return "\n".join(sections)

    def _build_canvas_request_from_chat_tool_input(
        self,
        chat_request: PromptSheetChatRequest,
        tool_input: PromptSheetCanvasToolInput,
    ) -> GenerateExamPromptRequest:
        topic = tool_input.topic or chat_request.current_topic or chat_request.message or tool_input.instruction
        prompt_type = self._infer_prompt_type_from_tool_input(tool_input, chat_request)
        return GenerateExamPromptRequest(
            originalInput=tool_input.instruction,
            topic=topic,
            studyStage=chat_request.study_stage,
            promptType=prompt_type,
            taskType=tool_input.task_type or chat_request.task_type,
            genre=tool_input.genre or chat_request.genre,
            wordRange=tool_input.word_range or chat_request.word_range,
            requirements=tool_input.requirements or chat_request.requirements,
            aiProvider=chat_request.ai_provider,
            userId=chat_request.user_id,
            preserveDetails=tool_input.preserve_details,
        )

    def _infer_prompt_type_from_tool_input(
        self,
        tool_input: PromptSheetCanvasToolInput,
        chat_request: PromptSheetChatRequest | None,
    ) -> str:
        signal_text = " ".join(
            value
            for value in [
                tool_input.genre,
                tool_input.topic,
                tool_input.instruction,
                " ".join(tool_input.preserve_details),
            ]
            if value
        )
        inferred = self._infer_prompt_type(signal_text)
        if inferred != "general":
            return inferred
        if self._has_general_prompt_signal(signal_text):
            return "general"
        if chat_request is not None and chat_request.prompt_type:
            return chat_request.prompt_type
        return "general"

    def _has_general_prompt_signal(self, text: str) -> bool:
        normalized = text.lower()
        return any(
            keyword in normalized
            for keyword in [
                "general",
                "argumentative",
                "expository",
                "essay",
                "letter",
                "email",
                "议论文",
                "观点作文",
                "说明文",
                "应用文",
                "书信",
                "普通作文",
            ]
        )

    def _infer_prompt_type(self, text: str) -> str:
        normalized = text.lower()
        if any(keyword in normalized for keyword in ["chart", "graph", "table", "图表", "折线", "柱状", "饼图", "数据"]):
            return "chart"
        if any(keyword in normalized for keyword in ["comic", "cartoon", "picture", "image", "漫画", "图画", "图片"]):
            return "comic"
        if any(keyword in normalized for keyword in ["material", "reading", "passage", "材料", "素材", "阅读"]):
            return "material"
        return "general"

    def _exam_prompt_standard(self, request: PromptSheetChatRequest | GenerateExamPromptRequest) -> str | None:
        study_stage = self._normalize_study_stage(getattr(request, "study_stage", None))
        if study_stage is None:
            return None
        return _load_shared_prompt_section(_EXAM_STANDARD_ASSET, study_stage)

    def _prompt_type_standard(self, request: PromptSheetChatRequest | GenerateExamPromptRequest) -> str | None:
        prompt_type = (getattr(request, "prompt_type", None) or "").strip().lower()
        if not prompt_type:
            return None
        parts = [
            section
            for section in [
                _load_shared_prompt_section(_PROMPT_TYPE_STANDARD_ASSET, prompt_type),
                _load_shared_prompt_section(
                    _PROMPT_TYPE_STANDARD_ASSET,
                    f"{prompt_type}:{self._normalize_study_stage(getattr(request, 'study_stage', None))}",
                ),
            ]
            if section
        ]
        return "\n\n".join(parts) if parts else None

    def _normalize_study_stage(self, study_stage: str | None) -> str | None:
        normalized = (study_stage or "").strip().lower()
        return _STAGE_ALIASES.get(normalized)

    def _style_reference_topic(self, request: PromptSheetChatRequest | GenerateExamPromptRequest) -> str:
        if isinstance(request, GenerateExamPromptRequest):
            return " ".join(value for value in [request.topic, request.original_input] if value)
        return " ".join(
            value
            for value in [
                request.current_topic,
                request.current_prompt_text,
                request.message,
            ]
            if value
        )

    def _log_openai_agents_run(
        self,
        *,
        workflow: str,
        result: object,
        started_at: float,
        request_context: dict[str, object],
    ) -> None:
        usage = extract_usage(result)
        run_items = extract_run_items(result)
        final_agent = getattr(result, "last_agent", None)
        agent_name = getattr(final_agent, "name", None) or ""
        duration_ms = (time.perf_counter() - started_at) * 1000
        log.info(
            "[OPENAI_AGENTS_RUN] workflow=%s model=%s agent=%s duration_ms=%.1f "
            "requests=%s input_tokens=%s input_cached_tokens=%s prompt_cache_hit=%s "
            "prompt_cache_hit_rate=%.2f output_tokens=%s reasoning_tokens=%s total_tokens=%s "
            "tool_calls=%s tool_names=%s handoffs=%s raw_responses=%s response_ids=%s "
            "response_models=%s last_response_id=%s context=%s",
            workflow,
            self.model,
            agent_name,
            duration_ms,
            usage.requests,
            usage.input_tokens,
            usage.cached_input_tokens,
            usage.prompt_cache_hit,
            usage.prompt_cache_hit_rate,
            usage.output_tokens,
            usage.reasoning_tokens,
            usage.total_tokens,
            run_items.tool_call_count,
            run_items.tool_names,
            run_items.handoff_count,
            run_items.raw_response_count,
            run_items.response_ids,
            run_items.response_models,
            run_items.last_response_id,
            request_context,
        )

    def _coerce_chat_response(self, raw: object) -> PromptSheetChatResponse:
        if isinstance(raw, PromptSheetChatResponse):
            return raw
        if isinstance(raw, str):
            return PromptSheetChatResponse.model_validate_json(raw)
        return PromptSheetChatResponse.model_validate(raw)

    def _coerce_generate_response(self, raw: object) -> GenerateExamPromptResponse:
        if isinstance(raw, GenerateExamPromptResponse):
            return raw
        if isinstance(raw, str):
            return GenerateExamPromptResponse.model_validate_json(raw)
        return GenerateExamPromptResponse.model_validate(raw)

    def _normalize_chat_response(self, response: PromptSheetChatResponse) -> PromptSheetChatResponse:
        update_actions = {"create_prompt_sheet", "update_prompt_sheet", "replace_prompt_sheet"}
        if response.needs_confirmation:
            return response.model_copy(update={"needs_canvas_update": False, "prompt_sheet": None})
        if response.action in update_actions:
            updates: dict[str, object] = {"needs_canvas_update": True}
            if response.prompt_sheet is not None:
                updates["prompt_sheet"] = self._normalize_generate_response(
                    response.prompt_sheet,
                    GenerateExamPromptRequest(
                        originalInput=response.canvas_instruction or response.reply,
                        topic=response.prompt_sheet.topic or response.prompt_sheet.prompt_text,
                        studyStage=None,
                        promptType=response.prompt_sheet.prompt_type,
                        taskType=response.prompt_sheet.task_type,
                        genre=response.prompt_sheet.genre,
                        wordRange=response.prompt_sheet.word_range,
                        requirements=response.prompt_sheet.requirements,
                        maxScore=response.prompt_sheet.max_score,
                    ),
                )
            return response.model_copy(update=updates)
        return response.model_copy(update={"needs_canvas_update": False, "prompt_sheet": None})

    def _normalize_generate_response(
        self,
        response: GenerateExamPromptResponse,
        request: GenerateExamPromptRequest,
    ) -> GenerateExamPromptResponse:
        updates = {
            "source_type": "ai_generated",
            "task_type": response.task_type or request.task_type or "task1",
            "word_range": response.word_range or request.word_range,
            "max_score": response.max_score if response.max_score is not None else request.max_score,
            "comic_scenes": response.comic_scenes or [],
        }
        prompt_type = response.prompt_type or request.prompt_type or "general"
        updates["prompt_type"] = prompt_type
        if prompt_type == "material" and response.attachment_type is None:
            updates["attachment_type"] = "material"
        elif prompt_type in {"chart", "comic"} and response.attachment_type is None:
            updates["attachment_type"] = "visual"
        elif response.attachment_type is None:
            updates["attachment_type"] = "none"
        if prompt_type == "chart":
            chart_spec = self._ensure_chart_spec(response, request)
            updates["chart_spec"] = chart_spec
            updates["visual_kind"] = "table" if chart_spec.display_type == "table" else "chart"
            updates["attachment_type"] = "visual"
        return response.model_copy(update=updates)

    def _ensure_chart_spec(
        self,
        response: GenerateExamPromptResponse,
        request: GenerateExamPromptRequest,
    ) -> ChartSpec:
        chart_spec = response.chart_spec
        if self._is_renderable_chart_spec(chart_spec):
            assert chart_spec is not None
            if chart_spec.display_type is None:
                chart_spec = chart_spec.model_copy(update={"display_type": "chart"})
            return chart_spec

        topic_text = " ".join(
            value
            for value in [
                response.topic,
                response.prompt_text,
                response.attachment_title,
                response.attachment_content,
                response.requirements,
                request.topic,
                request.original_input,
            ]
            if value
        )
        columns, rows, summary = self._build_fallback_chart_data(topic_text)
        return ChartSpec(
            title=response.attachment_title or response.topic or request.topic,
            displayType="chart",
            columns=columns,
            rows=rows,
            summary=response.attachment_content or response.requirements or summary,
        )

    def _is_renderable_chart_spec(self, chart_spec: ChartSpec | None) -> bool:
        if chart_spec is None:
            return False
        return len(chart_spec.columns) >= 2 and len(chart_spec.rows) >= 2

    def _build_fallback_chart_data(self, topic_text: str) -> tuple[list[str], list[list[str]], str]:
        labels = self._infer_chart_labels(topic_text)
        normalized = topic_text.lower()

        if "engel" in normalized:
            return (
                ["Year", "GDP (trillion USD)", "Engel coefficient (%)"],
                [[label, gdp, engel] for label, gdp, engel in zip(
                    labels,
                    ["10.5", "11.8", "13.6", "14.7", "16.2", "17.8"],
                    ["31.2", "30.1", "28.8", "27.5", "26.2", "25.1"],
                    strict=False,
                )],
                "Practice data showing GDP rising while the Engel coefficient declines.",
            )

        if "ranking" in normalized and "inflation" in normalized:
            return (
                ["Year", "Global GDP ranking", "Inflation ranking"],
                [[label, gdp_rank, inflation_rank] for label, gdp_rank, inflation_rank in zip(
                    labels,
                    ["2", "2", "2", "2", "2", "2"],
                    ["78", "85", "91", "88", "94", "90"],
                    strict=False,
                )],
                "Practice data comparing a stable GDP ranking with a fluctuating inflation ranking.",
            )

        if "ranking" in normalized:
            return (
                ["Year", "Ranking A", "Ranking B"],
                [[label, first, second] for label, first, second in zip(
                    labels,
                    ["8", "7", "6", "5", "4", "3"],
                    ["12", "11", "9", "8", "7", "6"],
                    strict=False,
                )],
                "Practice data showing two rankings improving over time.",
            )

        return (
            ["Year", "Value A", "Value B"],
            [[label, first, second] for label, first, second in zip(
                labels,
                ["35", "42", "50", "58", "66", "74"],
                ["28", "31", "39", "45", "53", "61"],
                strict=False,
            )],
            "Practice data showing two comparable trends over time.",
        )

    def _infer_chart_labels(self, topic_text: str) -> list[str]:
        import re

        match = re.search(r"(20\d{2})\s*[-–—]\s*(20\d{2})", topic_text)
        if match:
            start = int(match.group(1))
            end = int(match.group(2))
            if start < end:
                span = end - start
                return [
                    str(start),
                    str(start + max(1, span // 5)),
                    str(start + max(2, (span * 2) // 5)),
                    str(start + max(3, (span * 3) // 5)),
                    str(start + max(4, (span * 4) // 5)),
                    str(end),
                ]
        return ["2014", "2016", "2018", "2020", "2022", "2023"]
