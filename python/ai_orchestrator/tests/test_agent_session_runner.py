import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.services.agent_session_runner import (
    AgentSessionRunItems,
    AgentSessionUsage,
    run_agent_session,
    stream_agent_session,
)


class AgentSessionRunnerTest(unittest.IsolatedAsyncioTestCase):
    async def test_run_agent_session_creates_sqlite_session_and_returns_output_metadata(self) -> None:
        agent = object()
        agent_input = "Hi"
        fake_session = object()
        fake_result = SimpleNamespace(
            final_output="Feedback",
            last_agent=SimpleNamespace(name="Evaluation Agent"),
        )

        session_db_path = str(Path("data") / "assistant.db")
        with (
            patch.object(Path, "mkdir") as mkdir,
            patch("agents.SQLiteSession", return_value=fake_session) as sqlite_session,
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=fake_result) as runner_run,
        ):
            result = await run_agent_session(
                agent=agent,
                agent_input=agent_input,
                conversation_id="conv-1",
                session_db_path=session_db_path,
            )

        mkdir.assert_called_once_with(parents=True, exist_ok=True)
        sqlite_session.assert_called_once_with("conv-1", session_db_path)
        runner_run.assert_awaited_once_with(agent, agent_input, session=fake_session)
        self.assertEqual(result.final_output, "Feedback")
        self.assertEqual(result.agent_name, "Evaluation Agent")
        self.assertEqual(result.usage, AgentSessionUsage())
        self.assertEqual(result.run_items, AgentSessionRunItems())

    async def test_run_agent_session_returns_prompt_cache_usage_metadata(self) -> None:
        agent = object()
        fake_usage = SimpleNamespace(
            requests=2,
            input_tokens=1200,
            output_tokens=300,
            total_tokens=1500,
            input_tokens_details=SimpleNamespace(cached_tokens=768),
            output_tokens_details=SimpleNamespace(reasoning_tokens=42),
        )
        fake_result = SimpleNamespace(
            final_output="Feedback",
            last_agent=SimpleNamespace(name="Polish Agent"),
            context_wrapper=SimpleNamespace(usage=fake_usage),
        )

        with (
            patch.object(Path, "mkdir"),
            patch("agents.SQLiteSession", return_value=object()),
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=fake_result),
        ):
            result = await run_agent_session(
                agent=agent,
                agent_input="Hi",
                conversation_id="conv-1",
                session_db_path="data/assistant.db",
            )

        self.assertEqual(result.usage.requests, 2)
        self.assertEqual(result.usage.input_tokens, 1200)
        self.assertEqual(result.usage.cached_input_tokens, 768)
        self.assertTrue(result.usage.prompt_cache_hit)
        self.assertEqual(result.usage.prompt_cache_hit_rate, 0.64)
        self.assertEqual(result.usage.output_tokens, 300)
        self.assertEqual(result.usage.reasoning_tokens, 42)
        self.assertEqual(result.usage.total_tokens, 1500)

    async def test_run_agent_session_returns_run_item_observability_metadata(self) -> None:
        agent = object()
        fake_result = SimpleNamespace(
            final_output="Feedback",
            last_agent=SimpleNamespace(name="Router Agent"),
            new_items=[
                SimpleNamespace(type="tool_call_item", raw_item=SimpleNamespace(name="polish_text")),
                SimpleNamespace(type="tool_call_item", raw_item=SimpleNamespace(name="translate_text")),
                SimpleNamespace(type="handoff_output_item"),
            ],
            raw_responses=[
                SimpleNamespace(response_id="resp-1", model="gpt-test"),
                SimpleNamespace(response_id="resp-2", model="gpt-test"),
            ],
        )

        with (
            patch.object(Path, "mkdir"),
            patch("agents.SQLiteSession", return_value=object()),
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=fake_result),
        ):
            result = await run_agent_session(
                agent=agent,
                agent_input="Hi",
                conversation_id="conv-1",
                session_db_path="data/assistant.db",
            )

        self.assertEqual(result.run_items.new_items_count, 3)
        self.assertEqual(result.run_items.tool_call_count, 2)
        self.assertEqual(result.run_items.tool_names, ("polish_text", "translate_text"))
        self.assertEqual(result.run_items.handoff_count, 1)
        self.assertEqual(result.run_items.raw_response_count, 2)
        self.assertEqual(result.run_items.last_response_id, "resp-2")
        self.assertEqual(result.run_items.response_ids, ("resp-1", "resp-2"))
        self.assertEqual(result.run_items.response_models, ("gpt-test", "gpt-test"))

    async def test_run_agent_session_passes_context_to_runner(self) -> None:
        agent = object()
        run_context = SimpleNamespace(conversation_id="conv-ctx")
        fake_result = SimpleNamespace(final_output="Feedback", last_agent=None)

        with (
            patch.object(Path, "mkdir"),
            patch("agents.SQLiteSession", return_value=object()) as sqlite_session,
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=fake_result) as runner_run,
        ):
            await run_agent_session(
                agent=agent,
                agent_input="Hi",
                conversation_id="conv-1",
                session_db_path="data/assistant.db",
                run_context=run_context,
            )

        sqlite_session.assert_called_once()
        self.assertIs(runner_run.await_args.kwargs["context"], run_context)

    async def test_run_agent_session_can_skip_session_for_list_input_items(self) -> None:
        agent = object()
        agent_input = [{"role": "user", "content": [{"type": "input_text", "text": "Hi"}]}]
        fake_result = SimpleNamespace(final_output="Feedback", last_agent=None)

        with (
            patch.object(Path, "mkdir") as mkdir,
            patch("agents.SQLiteSession") as sqlite_session,
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=fake_result) as runner_run,
        ):
            result = await run_agent_session(
                agent=agent,
                agent_input=agent_input,
                conversation_id="conv-1",
                session_db_path="data/assistant.db",
                use_session=False,
            )

        mkdir.assert_not_called()
        sqlite_session.assert_not_called()
        runner_run.assert_awaited_once_with(agent, agent_input)
        self.assertEqual(result.final_output, "Feedback")

    async def test_stream_agent_session_yields_text_deltas_and_completed_result(self) -> None:
        from agents.stream_events import RawResponsesStreamEvent
        from openai.types.responses.response_text_delta_event import ResponseTextDeltaEvent

        agent = object()
        fake_stream_result = SimpleNamespace(
            final_output="hello",
            last_agent=SimpleNamespace(name="Router Agent"),
        )

        async def stream_events():
            yield RawResponsesStreamEvent(
                data=ResponseTextDeltaEvent(
                    content_index=0,
                    delta="he",
                    item_id="msg-1",
                    logprobs=[],
                    output_index=0,
                    sequence_number=1,
                    type="response.output_text.delta",
                )
            )
            yield RawResponsesStreamEvent(
                data=ResponseTextDeltaEvent(
                    content_index=0,
                    delta="llo",
                    item_id="msg-1",
                    logprobs=[],
                    output_index=0,
                    sequence_number=2,
                    type="response.output_text.delta",
                )
            )

        fake_stream_result.stream_events = stream_events

        with (
            patch.object(Path, "mkdir") as mkdir,
            patch("agents.SQLiteSession") as sqlite_session,
            patch("agents.Runner.run_streamed", return_value=fake_stream_result) as run_streamed,
        ):
            events = [
                event
                async for event in stream_agent_session(
                    agent=agent,
                    agent_input=[{"role": "user", "content": [{"type": "input_text", "text": "Hi"}]}],
                    conversation_id="conv-1",
                    session_db_path="data/assistant.db",
                    use_session=False,
                )
            ]

        mkdir.assert_not_called()
        sqlite_session.assert_not_called()
        run_streamed.assert_called_once_with(
            agent,
            [{"role": "user", "content": [{"type": "input_text", "text": "Hi"}]}],
        )
        self.assertEqual(events[0].type, "delta")
        self.assertEqual(events[0].delta, "he")
        self.assertEqual(events[1].delta, "llo")
        self.assertEqual(events[2].type, "completed")
        self.assertEqual(events[2].result.final_output, "hello")
        self.assertEqual(events[2].result.agent_name, "Router Agent")


if __name__ == "__main__":
    unittest.main()
