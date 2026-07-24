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


log = logging.getLogger("uvicorn.error")


class RawAssistantConfigError(RuntimeError):
    pass


class RawSingleAgentService:
    def __init__(self, *, model: str, session_db_path: str) -> None:
        self.model = model
        self.session_db_path = session_db_path
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
        return cls(model=model, session_db_path=session_db_path)

    def is_configured(self) -> bool:
        return bool(os.getenv("OPENAI_API_KEY", "").strip())

    def _get_agent(self):
        if self._agent is not None:
            return self._agent
        if not self.is_configured():
            raise RawAssistantConfigError("OPENAI_API_KEY 未配置，原始模型暂时不可用。")
        self._agent = create_raw_single_agent(self.model)
        return self._agent

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
            result = await run_agent_session(
                agent=self._get_agent(),
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
                reply=result.final_output,
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
            async for event in stream_agent_session(
                agent=self._get_agent(),
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
            yield {
                "type": "message.completed",
                "runId": run_id,
                "messageId": message_id,
                "content": final_result.final_output,
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
            steps=[
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
            ],
            promptSnapshots=[],
        )
