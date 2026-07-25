from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Literal


@dataclass(slots=True)
class AgentSessionUsage:
    requests: int = 0
    input_tokens: int = 0
    cached_input_tokens: int = 0
    output_tokens: int = 0
    reasoning_tokens: int = 0
    total_tokens: int = 0

    @property
    def prompt_cache_hit_rate(self) -> float:
        if self.input_tokens <= 0:
            return 0.0
        return self.cached_input_tokens / self.input_tokens

    @property
    def prompt_cache_hit(self) -> bool:
        return self.cached_input_tokens > 0


@dataclass(slots=True)
class AgentSessionRunItems:
    new_items_count: int = 0
    tool_call_count: int = 0
    tool_names: tuple[str, ...] = ()
    handoff_count: int = 0
    raw_response_count: int = 0
    last_response_id: str | None = None
    response_ids: tuple[str, ...] = ()
    response_models: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class AgentSessionSource:
    title: str
    url: str


@dataclass(slots=True)
class AgentSessionResult:
    final_output: str
    agent_name: str | None
    usage: AgentSessionUsage = field(default_factory=AgentSessionUsage)
    run_items: AgentSessionRunItems = field(default_factory=AgentSessionRunItems)
    sources: tuple[AgentSessionSource, ...] = ()


@dataclass(slots=True)
class AgentSessionStreamEvent:
    type: Literal["delta", "completed"]
    delta: str = ""
    result: AgentSessionResult | None = None


def _format_final_output(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()

    to_markdown = getattr(value, "to_markdown", None)
    if callable(to_markdown):
        return str(to_markdown() or "").strip()

    model_dump_json = getattr(value, "model_dump_json", None)
    if callable(model_dump_json):
        try:
            return str(model_dump_json(by_alias=True, exclude_none=True)).strip()
        except TypeError:
            return str(model_dump_json()).strip()

    return str(value or "").strip()


def _as_int(value: Any) -> int:
    if value is None:
        return 0
    return int(value)


def extract_usage(result: Any) -> AgentSessionUsage:
    context_wrapper = getattr(result, "context_wrapper", None)
    usage = getattr(context_wrapper, "usage", None)
    if usage is None:
        return AgentSessionUsage()

    input_token_details = getattr(usage, "input_tokens_details", None)
    output_token_details = getattr(usage, "output_tokens_details", None)
    return AgentSessionUsage(
        requests=_as_int(getattr(usage, "requests", 0)),
        input_tokens=_as_int(getattr(usage, "input_tokens", 0)),
        cached_input_tokens=_as_int(getattr(input_token_details, "cached_tokens", 0)),
        output_tokens=_as_int(getattr(usage, "output_tokens", 0)),
        reasoning_tokens=_as_int(getattr(output_token_details, "reasoning_tokens", 0)),
        total_tokens=_as_int(getattr(usage, "total_tokens", 0)),
    )


def _response_id(raw_response: Any) -> str | None:
    value = getattr(raw_response, "response_id", None) or getattr(raw_response, "id", None)
    return str(value) if value else None


def _response_model(raw_response: Any) -> str | None:
    value = getattr(raw_response, "model", None)
    return str(value) if value else None


def extract_run_items(result: Any) -> AgentSessionRunItems:
    new_items = list(getattr(result, "new_items", []) or [])
    raw_responses = list(getattr(result, "raw_responses", []) or [])
    tool_names: list[str] = []
    tool_call_count = 0
    handoff_count = 0

    for item in new_items:
        item_type = getattr(item, "type", "")
        if item_type == "tool_call_item":
            tool_call_count += 1
            raw_item = getattr(item, "raw_item", None)
            tool_name = getattr(raw_item, "name", None)
            if not tool_name and getattr(raw_item, "type", None) == "web_search_call":
                tool_name = "web_search"
            if tool_name:
                tool_names.append(str(tool_name))
        elif item_type == "handoff_output_item":
            handoff_count += 1

    response_ids = tuple(response_id for raw_response in raw_responses if (response_id := _response_id(raw_response)))
    response_models = tuple(response_model for raw_response in raw_responses if (response_model := _response_model(raw_response)))
    return AgentSessionRunItems(
        new_items_count=len(new_items),
        tool_call_count=tool_call_count,
        tool_names=tuple(tool_names),
        handoff_count=handoff_count,
        raw_response_count=len(raw_responses),
        last_response_id=response_ids[-1] if response_ids else None,
        response_ids=response_ids,
        response_models=response_models,
    )


def extract_sources(result: Any) -> tuple[AgentSessionSource, ...]:
    sources: list[AgentSessionSource] = []
    seen_urls: set[str] = set()
    for raw_response in list(getattr(result, "raw_responses", []) or []):
        for output_item in list(getattr(raw_response, "output", []) or []):
            for content_item in list(getattr(output_item, "content", []) or []):
                for annotation in list(getattr(content_item, "annotations", []) or []):
                    if getattr(annotation, "type", None) != "url_citation":
                        continue
                    url = str(getattr(annotation, "url", "") or "").strip()
                    if not url or url in seen_urls:
                        continue
                    title = str(getattr(annotation, "title", "") or "").strip() or url
                    seen_urls.add(url)
                    sources.append(AgentSessionSource(title=title, url=url))
    return tuple(sources)


def _extract_usage(result: Any) -> AgentSessionUsage:
    return extract_usage(result)


def _extract_run_items(result: Any) -> AgentSessionRunItems:
    return extract_run_items(result)


async def run_agent_session(
    *,
    agent: Any,
    agent_input: str | list[dict],
    conversation_id: str,
    session_db_path: str,
    use_session: bool = True,
    run_context: Any | None = None,
    trace_workflow_name: str | None = None,
    trace_metadata: dict[str, Any] | None = None,
) -> AgentSessionResult:
    from agents import RunConfig, Runner, SQLiteSession

    session = None
    if use_session:
        session_path = Path(session_db_path)
        session_path.parent.mkdir(parents=True, exist_ok=True)
        session = SQLiteSession(conversation_id, str(session_path))

    runner_kwargs = {}
    if use_session:
        runner_kwargs["session"] = session
    if run_context is not None:
        runner_kwargs["context"] = run_context
    if trace_workflow_name is not None or trace_metadata is not None:
        runner_kwargs["run_config"] = RunConfig(
            workflow_name=trace_workflow_name or "PEAI Agent Session",
            group_id=conversation_id,
            trace_metadata=trace_metadata,
        )

    result = await Runner.run(agent, agent_input, **runner_kwargs)

    final_agent = getattr(result, "last_agent", None)
    return AgentSessionResult(
        final_output=_format_final_output(getattr(result, "final_output", None)),
        agent_name=getattr(final_agent, "name", None),
        usage=extract_usage(result),
        run_items=extract_run_items(result),
        sources=extract_sources(result),
    )


async def stream_agent_session(
    *,
    agent: Any,
    agent_input: str | list[dict],
    conversation_id: str,
    session_db_path: str,
    use_session: bool = True,
    run_context: Any | None = None,
    trace_workflow_name: str | None = None,
    trace_metadata: dict[str, Any] | None = None,
):
    from agents import RunConfig, Runner, SQLiteSession
    from agents.stream_events import RawResponsesStreamEvent
    from openai.types.responses.response_text_delta_event import ResponseTextDeltaEvent

    session = None
    if use_session:
        session_path = Path(session_db_path)
        session_path.parent.mkdir(parents=True, exist_ok=True)
        session = SQLiteSession(conversation_id, str(session_path))

    runner_kwargs = {}
    if use_session:
        runner_kwargs["session"] = session
    if run_context is not None:
        runner_kwargs["context"] = run_context
    if trace_workflow_name is not None or trace_metadata is not None:
        runner_kwargs["run_config"] = RunConfig(
            workflow_name=trace_workflow_name or "PEAI Agent Session",
            group_id=conversation_id,
            trace_metadata=trace_metadata,
        )

    result = Runner.run_streamed(agent, agent_input, **runner_kwargs)
    async for event in result.stream_events():
        if isinstance(event, RawResponsesStreamEvent) and isinstance(event.data, ResponseTextDeltaEvent):
            yield AgentSessionStreamEvent(type="delta", delta=event.data.delta)

    final_agent = getattr(result, "last_agent", None)
    yield AgentSessionStreamEvent(
        type="completed",
        result=AgentSessionResult(
            final_output=_format_final_output(getattr(result, "final_output", None)),
            agent_name=getattr(final_agent, "name", None),
            usage=extract_usage(result),
            run_items=extract_run_items(result),
            sources=extract_sources(result),
        ),
    )
