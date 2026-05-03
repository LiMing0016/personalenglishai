import unittest

from python.ai_orchestrator.schemas.chat import (
    MessageDeltaEvent,
    RunFailedEvent,
    RunStartedEvent,
)


class AssistantStreamEventTest(unittest.TestCase):
    def test_run_started_event_serializes_camel_case_metadata(self) -> None:
        event = RunStartedEvent(
            runId="run-1",
            traceId="trace-1",
            agentName="Daily Explain Agent",
            model="gpt-test",
        )

        self.assertEqual(
            event.model_dump(by_alias=True),
            {
                "type": "run.started",
                "runId": "run-1",
                "traceId": "trace-1",
                "agentName": "Daily Explain Agent",
                "model": "gpt-test",
            },
        )

    def test_message_delta_event_includes_run_and_message_ids(self) -> None:
        event = MessageDeltaEvent(runId="run-1", messageId="msg-1", delta="hello")

        self.assertEqual(event.model_dump(by_alias=True)["type"], "message.delta")
        self.assertEqual(event.model_dump(by_alias=True)["runId"], "run-1")
        self.assertEqual(event.model_dump(by_alias=True)["messageId"], "msg-1")

    def test_run_failed_event_uses_standard_error_payload(self) -> None:
        event = RunFailedEvent(
            runId="run-1",
            error={
                "code": "OPENAI_RUN_FAILED",
                "message": "模型调用失败",
            },
        )

        body = event.model_dump(by_alias=True)
        self.assertEqual(body["type"], "run.failed")
        self.assertEqual(body["runId"], "run-1")
        self.assertEqual(body["error"]["code"], "OPENAI_RUN_FAILED")


if __name__ == "__main__":
    unittest.main()
