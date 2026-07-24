from __future__ import annotations

import logging
import os
import time
from pathlib import Path
from uuid import uuid4

from .adapters.raw_openai_input_items import build_raw_assistant_input
from .agents.raw_single import create_raw_single_agent
from .schemas.assistant_request import AssistantOpenAIState
from .schemas.assistant_request import AssistantRequest
from .schemas.assistant_request import AssistantRunMetadata
from .schemas.assistant_request import AssistantUsage
from .schemas.chat import AssistantReply
from .services.agent_session_runner import run_agent_session
from .services.agent_session_runner import stream_agent_session
from .services.assistant_request_validator import validate_assistant_request
from .services.assistant_runtime_mode import build_session_key
from .services.raw_fetch_mcp import RawFetchMcpConfig
from .services.raw_fetch_mcp import connected_raw_fetch_mcp_servers


log = logging.getLogger("uvicorn.error")


class RawAssistantConfigError(RuntimeError):
    pass


class RawSingleAgentService:
    def __init__(
        self,
        *,
        model: str,
        session_db_path: str,
        fetch_mcp_config: RawFetchMcpConfig | None = None,
    ) -> None:
        self.model = model
        self.session_db_path = session_db_path
        self.fetch_mcp_config = fetch_mcp_config or RawFetchMcpConfig(enabled=False)
        self._agent = None

    @classmethod
    def from_env(cls) -> "RawSingleAgentService":
        model = (
            os.getenv("AI_ASSISTANT_MODEL", "").strip()
            or os.getenv("AI_PROVIDER_OPENAI_MODEL", "").strip()
            or "gpt-5.4-mini"
        )
        session_db_path = os.getenv(
            "AI_ASSISTANT_SESSION_DB_PATH",
            str(Path(__file__).resolve().parent / "data" / "assistant_sessions.db"),
        )
        return cls(
            model=model,
            session_db_path=session_db_path,
            fetch_mcp_config=RawFetchMcpConfig.from_env(),
        )

    def is_configured(self) -> bool:
        return bool(os.getenv("OPENAI_API_KEY", "").strip())

    def _get_agent(self, *, mcp_servers=()):
        if not self.is_configured() and self._agent is None:
            raise RawAssistantConfigError("OPENAI_API_KEY 未配置，原始模型暂时不可用。")
        if mcp_servers:
            return create_raw_single_agent(self.model, mcp_servers=mcp_servers)
        if self._agent is not None:
            return self._agent
        self._agent = create_raw_single_agent(self.model)
        return self._agent

    @staticmethod
    def _with_sources(content: str, sources) -> str:
        source_lines = []
        for source in sources:
            if source.url in content:
                continue
            title = source.title.replace("[", r"\[").replace("]", r"\]")
            source_lines.append(f"- [{title}]({source.url})")
        if not source_lines:
            return content
        return f"{content}\n\n### 来源\n\n" + "\n".join(source_lines)

    async def run_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ) -> AssistantReply:
        started_at = time.perf_counter()
        validated = validate_assistant_request(request)
        conversation_id = request.app_conversation_id or request.client_message_id
        session_key = build_session_key("single_agent_raw", conversation_id)
        run_id = f"run_{uuid4().hex}"
        trace_id = f"trace_{uuid4().hex}"
        raw_input = build_raw_assistant_input(request)

        log.info(
            "[RAW_ASSISTANT_RUN_START] run_id=%s trace_id=%s conversation_id=%s "
            "model=%s scope=%s streaming=false authorization_present=%s",
            run_id,
            trace_id,
            conversation_id,
            self.model,
            validated.scope,
            bool(authorization),
        )
        try:
            async with connected_raw_fetch_mcp_servers(
                self.fetch_mcp_config
            ) as mcp_servers:
                result = await run_agent_session(
                    agent=self._get_agent(mcp_servers=mcp_servers),
                    agent_input=raw_input.agent_input,
                    conversation_id=session_key,
                    session_db_path=self.session_db_path,
                    use_session=raw_input.use_session,
                    trace_workflow_name="PEAI Raw Single Agent",
                    trace_metadata={
                        "agent_mode": "single_agent_raw",
                        "conversation_id": conversation_id,
                        "run_id": run_id,
                        "trace_id": trace_id,
                        "input_scope": validated.scope,
                        "streaming": "false",
                    },
                )
            if not result.final_output:
                raise RawAssistantConfigError("原始模型没有返回内容。")
            metadata = self._build_run_metadata(
                request,
                run_id=run_id,
                trace_id=trace_id,
                scope=validated.scope,
                usage=result.usage,
                run_items=result.run_items,
                latency_ms=(time.perf_counter() - started_at) * 1000,
            )
            return AssistantReply(
                reply=self._with_sources(result.final_output, result.sources),
                agent_name=result.agent_name or "Raw Single Agent",
                run=metadata,
                parts=[],
            )
        except Exception:
            log.error(
                "[RAW_ASSISTANT_RUN_ERROR] run_id=%s trace_id=%s conversation_id=%s",
                run_id,
                trace_id,
                conversation_id,
                exc_info=True,
            )
            raise

    async def stream_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ):
        started_at = time.perf_counter()
        validated = validate_assistant_request(request)
        conversation_id = request.app_conversation_id or request.client_message_id
        session_key = build_session_key("single_agent_raw", conversation_id)
        run_id = f"run_{uuid4().hex}"
        trace_id = f"trace_{uuid4().hex}"
        message_id = f"msg_{uuid4().hex}"
        raw_input = build_raw_assistant_input(request)

        yield {
            "type": "run.started",
            "runId": run_id,
            "traceId": trace_id,
            "agentName": "Raw Single Agent",
            "model": self.model,
        }
        yield {
            "type": "message.created",
            "runId": run_id,
            "messageId": message_id,
            "role": "assistant",
        }

        try:
            final_result = None
            async with connected_raw_fetch_mcp_servers(
                self.fetch_mcp_config
            ) as mcp_servers:
                async for event in stream_agent_session(
                    agent=self._get_agent(mcp_servers=mcp_servers),
                    agent_input=raw_input.agent_input,
                    conversation_id=session_key,
                    session_db_path=self.session_db_path,
                    use_session=raw_input.use_session,
                    trace_workflow_name="PEAI Raw Single Agent",
                    trace_metadata={
                        "agent_mode": "single_agent_raw",
                        "conversation_id": conversation_id,
                        "run_id": run_id,
                        "trace_id": trace_id,
                        "input_scope": validated.scope,
                        "streaming": "true",
                    },
                ):
                    if event.type == "delta":
                        yield {
                            "type": "message.delta",
                            "runId": run_id,
                            "messageId": message_id,
                            "delta": event.delta,
                        }
                    else:
                        final_result = event.result

            if final_result is None or not final_result.final_output:
                raise RawAssistantConfigError("原始模型没有返回内容。")
            metadata = self._build_run_metadata(
                request,
                run_id=run_id,
                trace_id=trace_id,
                scope=validated.scope,
                usage=final_result.usage,
                run_items=final_result.run_items,
                latency_ms=(time.perf_counter() - started_at) * 1000,
            )
            final_content = self._with_sources(final_result.final_output, final_result.sources)
            yield {
                "type": "message.completed",
                "runId": run_id,
                "messageId": message_id,
                "content": final_content,
                "parts": [],
            }
            yield {
                "type": "run.completed",
                "runId": run_id,
                "run": metadata.model_dump(by_alias=True),
            }
        except Exception as exc:
            log.error(
                "[RAW_ASSISTANT_STREAM_ERROR] run_id=%s trace_id=%s conversation_id=%s",
                run_id,
                trace_id,
                conversation_id,
                exc_info=True,
            )
            yield {
                "type": "run.failed",
                "runId": run_id,
                "error": {"code": "OPENAI_RUN_FAILED", "message": str(exc)},
            }

    def _build_run_metadata(
        self,
        request: AssistantRequest,
        *,
        run_id: str,
        trace_id: str,
        scope: str,
        usage,
        run_items,
        latency_ms: float,
    ) -> AssistantRunMetadata:
        steps = [
            {
                "stepType": "target_agent",
                "agentName": "Raw Single Agent",
                "usage": {
                    "requests": usage.requests,
                    "inputTokens": usage.input_tokens,
                    "cachedInputTokens": usage.cached_input_tokens,
                    "outputTokens": usage.output_tokens,
                    "totalTokens": usage.total_tokens,
                },
                "responseId": run_items.last_response_id,
            }
        ]
        if run_items.tool_call_count:
            steps.append(
                {
                    "stepType": "tool_calls",
                    "toolCallCount": run_items.tool_call_count,
                    "toolNames": list(run_items.tool_names),
                }
            )

        return AssistantRunMetadata(
            runId=run_id,
            traceId=trace_id,
            agentName="Raw Single Agent",
            model=self.model,
            agentMode="single_agent_raw",
            mode=request.mode,
            intent=request.intent,
            scope=scope,
            latencyMs=int(latency_ms),
            usage=AssistantUsage(
                requests=usage.requests,
                inputTokens=usage.input_tokens,
                cachedInputTokens=usage.cached_input_tokens,
                outputTokens=usage.output_tokens,
                totalTokens=usage.total_tokens,
            ),
            openai=AssistantOpenAIState(responseId=run_items.last_response_id),
            routeRequest=None,
            routingDecision=None,
            steps=steps,
            promptSnapshots=[],
        )
