import inspect
import os
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.assistant_service import AssistantAgentService, AssistantConfigError
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.routing_state import ActiveTaskState
from python.ai_orchestrator.schemas.routing_state import ContinuationDecision
from python.ai_orchestrator.services.active_task_state import InMemoryActiveTaskStateStore
from python.ai_orchestrator.services.agent_session_runner import (
    AgentSessionResult,
    AgentSessionRunItems,
    AgentSessionUsage,
)


def _logged_messages(log) -> list[str]:
    return [call.args[0] for call in log.info.call_args_list + log.error.call_args_list]


def _has_logged(log, marker: str) -> bool:
    return any(marker in message for message in _logged_messages(log))


def _info_call_with(log, marker: str):
    return next(call for call in log.info.call_args_list if marker in call.args[0])


class AssistantAgentServiceTest(unittest.IsolatedAsyncioTestCase):
    def test_from_env_defaults_to_gpt_5_4_mini(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            service = AssistantAgentService.from_env()

        self.assertEqual(service.model, "gpt-5.4-mini")

    def test_from_env_uses_existing_openai_provider_model_when_assistant_model_is_unset(self) -> None:
        with patch.dict(os.environ, {"AI_PROVIDER_OPENAI_MODEL": "provider-model"}, clear=True):
            service = AssistantAgentService.from_env()

        self.assertEqual(service.model, "provider-model")

    def test_from_env_prefers_assistant_model_over_provider_model(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_MODEL": "assistant-model",
                "AI_PROVIDER_OPENAI_MODEL": "provider-model",
            },
            clear=True,
        ):
            service = AssistantAgentService.from_env()

        self.assertEqual(service.model, "assistant-model")

    async def test_chat_runs_router_through_session_runner_and_returns_agent_name(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        router_agent = object()
        service._router_agent = router_agent

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="Here is feedback.", agent_name="Evaluation Agent"),
        ) as run_agent_session:
            reply = await service.chat(
                message="Evaluate this sentence.",
                conversation_id="conv-1",
                attachments=[],
            )

        run_agent_session.assert_awaited_once()
        self.assertIs(run_agent_session.await_args.kwargs["agent"], router_agent)
        self.assertEqual(run_agent_session.await_args.kwargs["conversation_id"], "conv-1")
        self.assertEqual(run_agent_session.await_args.kwargs["session_db_path"], "unused.db")
        self.assertEqual(run_agent_session.await_args.kwargs["agent_input"], "Evaluate this sentence.")
        self.assertTrue(run_agent_session.await_args.kwargs["use_session"])
        self.assertEqual(run_agent_session.await_args.kwargs["run_context"].conversation_id, "conv-1")
        self.assertEqual(reply.reply, "Here is feedback.")
        self.assertEqual(reply.agent_name, "Evaluation Agent")

    async def test_chat_saves_active_task_state_after_specialist_result(self) -> None:
        store = InMemoryActiveTaskStateStore()
        service = AssistantAgentService(model="test-model", session_db_path="unused.db", active_task_store=store)
        service._router_agent = object()

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="Here is a writing plan.", agent_name="Learning Planner Agent"),
        ):
            await service.chat(
                message="你可以给我规划一下如何学好英语作文？",
                conversation_id="conv-plan-1",
                attachments=[],
            )

        state = store.get("conv-plan-1")
        self.assertIsNotNone(state)
        assert state is not None
        self.assertEqual(state.active_intent, "learning_planner")
        self.assertEqual(state.active_agent, "Learning Planner Agent")
        self.assertEqual(state.last_output_type, "plan")
        self.assertIn("more_options", state.continuation_capabilities)

    async def test_chat_runs_continuation_classifier_and_injects_decision_context(self) -> None:
        class FakeContinuationClassifier:
            def __init__(self) -> None:
                self.payload = None

            async def classify(self, payload):
                self.payload = payload
                return ContinuationDecision(
                    relation="continue_previous_task",
                    resolved_intent="learning_planner",
                    continuation_action="more_options",
                    target_task_title="英语作文学习规划",
                    reason="用户要求更多方案，延续上一轮学习规划。",
                    confidence=0.91,
                )

        store = InMemoryActiveTaskStateStore()
        store.save(
            ActiveTaskState(
                conversation_id="conv-plan-2",
                active_intent="learning_planner",
                active_agent="Learning Planner Agent",
                task_title="英语作文学习规划",
                task_summary="用户想获得学好英语作文的可执行学习方案。",
                last_user_message="你可以给我规划一下如何学好英语作文？",
                last_output_type="plan",
                continuation_capabilities={"more_options", "expand_detail"},
                turn_id="turn-1",
            )
        )
        classifier = FakeContinuationClassifier()
        service = AssistantAgentService(
            model="test-model",
            session_db_path="unused.db",
            active_task_store=store,
            continuation_classifier=classifier,
        )
        service._router_agent = object()

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="Another plan.", agent_name="Learning Planner Agent"),
        ) as run_agent_session:
            await service.chat(message="还有其他方案吗？", conversation_id="conv-plan-2", attachments=[])

        self.assertIsNotNone(classifier.payload)
        self.assertEqual(classifier.payload.active_task_state.active_intent, "learning_planner")
        agent_input = run_agent_session.await_args.kwargs["agent_input"]
        self.assertIn("[续问判定上下文]", agent_input)
        self.assertIn("- 判定关系: continue_previous_task", agent_input)
        self.assertIn("- 目标 intent: learning_planner", agent_input)
        self.assertIn("还有其他方案吗？", agent_input)

    async def test_chat_logs_start_done_cache_and_run_items_for_conversation(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()
        session_result = AgentSessionResult(
            final_output="Here is feedback.",
            agent_name="Polish Agent",
            usage=AgentSessionUsage(
                requests=2,
                input_tokens=1200,
                cached_input_tokens=768,
                output_tokens=300,
                reasoning_tokens=42,
                total_tokens=1500,
            ),
            run_items=AgentSessionRunItems(
                new_items_count=4,
                tool_call_count=2,
                tool_names=("polish_text", "translate_text"),
                handoff_count=1,
                raw_response_count=2,
                last_response_id="resp-123",
                response_ids=("resp-122", "resp-123"),
                response_models=("test-model",),
            ),
        )

        with (
            patch("python.ai_orchestrator.assistant_service.time.perf_counter", side_effect=[10.0, 10.25]),
            patch(
                "python.ai_orchestrator.assistant_service.run_agent_session",
                new_callable=AsyncMock,
                return_value=session_result,
            ),
            patch("python.ai_orchestrator.assistant_service.log") as log,
        ):
            await service.chat(
                message="Polish this sentence.",
                conversation_id="conv-cache-1",
                attachments=[],
            )

        self.assertTrue(_has_logged(log, "[ASSISTANT_CHAT_START]"))
        self.assertTrue(_has_logged(log, "[ASSISTANT_RUN_ITEMS]"))
        self.assertTrue(_has_logged(log, "[ASSISTANT_PROMPT_CACHE]"))
        self.assertTrue(_has_logged(log, "[OPENAI_AGENTS_RUN]"))
        self.assertTrue(_has_logged(log, "[ASSISTANT_CHAT_DONE]"))

        start_call = _info_call_with(log, "ASSISTANT_CHAT_START")
        self.assertIn("conversation_id=%s", start_call.args[0])
        self.assertIn("message_chars=%s", start_call.args[0])
        self.assertIn("conv-cache-1", start_call.args)
        self.assertIn("test-model", start_call.args)
        self.assertEqual(start_call.args[4], 21)
        self.assertEqual(start_call.args[5], "")

        run_items_call = _info_call_with(log, "ASSISTANT_RUN_ITEMS")
        self.assertIn("tool_names=%s", run_items_call.args[0])
        self.assertIn("response_ids=%s", run_items_call.args[0])
        self.assertIn(("polish_text", "translate_text"), run_items_call.args)
        self.assertIn(("resp-122", "resp-123"), run_items_call.args)
        self.assertIn("resp-123", run_items_call.args)

        cache_call = _info_call_with(log, "ASSISTANT_PROMPT_CACHE")
        self.assertIn("cached_input_tokens=%s", cache_call.args[0])
        self.assertIn("prompt_cache_hit=%s", cache_call.args[0])
        self.assertIn("prompt_cache_hit_rate=%.2f", cache_call.args[0])
        self.assertIn(768, cache_call.args)
        self.assertIn(True, cache_call.args)
        self.assertIn(0.64, cache_call.args)

        openai_call = _info_call_with(log, "OPENAI_AGENTS_RUN")
        self.assertIn("workflow=assistant", openai_call.args[0])
        self.assertIn("input_cached_tokens=%s", openai_call.args[0])
        self.assertIn("tool_calls=%s", openai_call.args[0])
        self.assertIn("response_models=%s", openai_call.args[0])
        self.assertIn(("test-model",), openai_call.args)

        done_call = _info_call_with(log, "ASSISTANT_CHAT_DONE")
        self.assertIn("duration_ms=%.1f", done_call.args[0])
        self.assertIn(250.0, done_call.args)
        self.assertIn(17, done_call.args)

    async def test_chat_logs_error_when_session_runner_fails(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()

        with (
            patch("python.ai_orchestrator.assistant_service.time.perf_counter", side_effect=[20.0, 20.5]),
            patch(
                "python.ai_orchestrator.assistant_service.run_agent_session",
                new_callable=AsyncMock,
                side_effect=RuntimeError("sdk failed"),
            ),
            patch("python.ai_orchestrator.assistant_service.log") as log,
        ):
            with self.assertRaisesRegex(RuntimeError, "sdk failed"):
                await service.chat(
                    message="Polish this sentence.",
                    conversation_id="conv-error-1",
                    attachments=[],
                )

        error_call = log.error.call_args
        self.assertIn("ASSISTANT_CHAT_ERROR", error_call.args[0])
        self.assertIn("conversation_id=%s", error_call.args[0])
        self.assertIn("error_type=%s", error_call.args[0])
        self.assertIn("duration_ms=%.1f", error_call.args[0])
        self.assertIn("conv-error-1", error_call.args)
        self.assertIn("RuntimeError", error_call.args)
        self.assertIn(500.0, error_call.args)
        self.assertTrue(error_call.kwargs["exc_info"])

    async def test_chat_injects_study_stage_context_into_text_input(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="Here is feedback.", agent_name="Evaluation Agent"),
        ) as run_agent_session:
            await service.chat(
                message="Evaluate this sentence.",
                conversation_id="conv-1",
                attachments=[],
                study_stage="postgrad",
                assistant_mode="exam",
            )

        agent_input = run_agent_session.await_args.kwargs["agent_input"]
        self.assertIn("[用户画像上下文]", agent_input)
        self.assertIn("- 学段: 考研", agent_input)
        self.assertIn("[对话模式上下文]", agent_input)
        self.assertIn("- 当前模式: 考试模式", agent_input)
        self.assertIn("评分口径", agent_input)
        self.assertIn("[用户消息]", agent_input)
        self.assertIn("Evaluate this sentence.", agent_input)

    async def test_chat_disables_session_for_attachment_input_items(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()
        service._attachment_agent = SimpleNamespace(name="Attachment Agent")

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="Read the image.", agent_name="Evaluation Agent"),
        ) as run_agent_session:
            await service.chat(
                message="Evaluate this image.",
                conversation_id="conv-1",
                attachments=[
                    {
                        "filename": "draft.png",
                        "content_type": "image/png",
                        "content": b"fake-image",
                    }
                ],
            )

        self.assertFalse(run_agent_session.await_args.kwargs["use_session"])
        self.assertEqual(run_agent_session.await_args.kwargs["agent_input"][0]["role"], "user")

    async def test_chat_uses_attachment_agent_for_multimodal_inputs(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()
        service._attachment_agent = SimpleNamespace(name="Attachment Agent")

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="The image says hello.", agent_name="Attachment Agent"),
        ) as run_agent_session:
            await service.chat(
                message="翻译成中文。",
                conversation_id="conv-image-1",
                attachments=[
                    {
                        "filename": "screenshot.png",
                        "content_type": "image/png",
                        "content": b"fake-image",
                    }
                ],
            )

        agent = run_agent_session.await_args.kwargs["agent"]
        self.assertIsNot(agent, service._router_agent)
        self.assertIs(agent, service._attachment_agent)
        self.assertEqual(agent.name, "Attachment Agent")
        self.assertFalse(run_agent_session.await_args.kwargs["use_session"])

    async def test_chat_injects_study_stage_context_into_attachment_text_item(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()
        service._attachment_agent = SimpleNamespace(name="Attachment Agent")

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="Read the image.", agent_name="Evaluation Agent"),
        ) as run_agent_session:
            await service.chat(
                message="Evaluate this image.",
                conversation_id="conv-1",
                attachments=[
                    {
                        "filename": "draft.png",
                        "content_type": "image/png",
                        "content": b"fake-image",
                    }
                ],
                study_stage="ielts",
            )

        content = run_agent_session.await_args.kwargs["agent_input"][0]["content"]
        self.assertIn("[用户画像上下文]", content[0]["text"])
        self.assertIn("- 学段: 雅思", content[0]["text"])
        self.assertEqual(content[1]["type"], "input_image")

    async def test_run_assistant_request_disables_session_for_structured_input_items(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()

        request = AssistantRequest.model_validate(
            {
                "appConversationId": "conv-p0-1",
                "clientMessageId": "client-p0-1",
                "mode": "daily_explain",
                "intent": "free_chat",
                "scope": "message_only",
                "message": {"text": "ping"},
            }
        )

        with patch(
            "python.ai_orchestrator.assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="pong", agent_name="Router Agent"),
        ) as run_agent_session:
            reply = await service.run_assistant_request(request)

        run_agent_session.assert_awaited_once()
        self.assertFalse(run_agent_session.await_args.kwargs["use_session"])
        self.assertIsInstance(run_agent_session.await_args.kwargs["agent_input"], list)
        self.assertEqual(reply.reply, "pong")
        self.assertEqual(reply.run.scope, "message_only")


    async def test_chat_rejects_empty_model_output(self) -> None:
        service = AssistantAgentService(model="test-model", session_db_path="unused.db")
        service._router_agent = object()

        with (
            patch(
                "python.ai_orchestrator.assistant_service.run_agent_session",
                new_callable=AsyncMock,
                return_value=AgentSessionResult(final_output="", agent_name=None),
            ),
            patch("python.ai_orchestrator.assistant_service.log"),
        ):
            with self.assertRaisesRegex(AssistantConfigError, "没有返回内容"):
                await service.chat(message="Hi", conversation_id="conv-1", attachments=[])

    def test_service_does_not_own_sdk_session_execution(self) -> None:
        source = inspect.getsource(AssistantAgentService.chat)

        self.assertIn("run_agent_session", source)
        self.assertNotIn("SQLiteSession", source)
        self.assertNotIn("Runner.run", source)


if __name__ == "__main__":
    unittest.main()
