import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from python.ai_orchestrator.app import app
from python.ai_orchestrator.schemas.assistant_request import AssistantRunMetadata
from python.ai_orchestrator.schemas.chat import AssistantReply
from python.ai_orchestrator.schemas.routing import RoutingDecision


class CapturingRunService:
    def __init__(self) -> None:
        self.received = None

    def is_configured(self) -> bool:
        return True

    async def run_assistant_request(self, request, authorization=None) -> AssistantReply:
        self.received = {"request": request, "authorization": authorization}
        return AssistantReply(
            reply="ok",
            agent_name="Translation Agent",
            run=AssistantRunMetadata(
                runId="run-1",
                traceId="trace-1",
                agentName="Translation Agent",
                model="test-model",
                mode=request.mode,
                intent=request.intent,
                scope=request.scope or "message_only",
            ),
        )

    async def stream_assistant_request(self, request, authorization=None):
        self.received = {"request": request, "authorization": authorization}
        yield {"type": "run.started", "runId": "run-1", "traceId": "trace-1", "agentName": "Translation Agent", "model": "test-model"}
        yield {"type": "message.created", "runId": "run-1", "messageId": "msg-1", "role": "assistant"}
        yield {"type": "message.delta", "runId": "run-1", "messageId": "msg-1", "delta": "he"}
        yield {"type": "message.delta", "runId": "run-1", "messageId": "msg-1", "delta": "llo"}
        yield {"type": "message.completed", "runId": "run-1", "messageId": "msg-1", "content": "hello"}
        yield {"type": "run.completed", "runId": "run-1"}

    async def route_assistant_request(self, request, authorization=None) -> RoutingDecision:
        self.received = {"request": request, "authorization": authorization}
        return RoutingDecision(
            intent="writing_evaluation",
            route_type="run_workflow",
            workflow="writing_evaluation",
            target_agent="writing_evaluation",
            confidence=0.91,
            required_inputs=["essay_text", "topic_prompt"],
            missing_inputs=[],
            reason="Debug route decision.",
        )


class AssistantRunEndpointTest(unittest.TestCase):
    def test_assistant_run_accepts_json_request_and_returns_metadata(self) -> None:
        client = TestClient(app)
        fake_service = CapturingRunService()

        with patch("python.ai_orchestrator.app.service", fake_service):
            response = client.post(
                "/assistant/run",
                json={
                    "appConversationId": "conv-1",
                    "clientMessageId": "client-1",
                    "mode": "daily_explain",
                    "intent": "translate",
                    "scope": "message_only",
                    "message": {"text": "翻译 hello"},
                },
                headers={"Authorization": "Bearer token"},
            )

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["reply"], "ok")
        self.assertEqual(body["conversationId"], "conv-1")
        self.assertEqual(body["agentName"], "Translation Agent")
        self.assertEqual(body["run"]["runId"], "run-1")
        self.assertEqual(body["run"]["agentName"], "Translation Agent")
        self.assertEqual(body["run"]["intent"], "translate")
        self.assertEqual(fake_service.received["authorization"], "Bearer token")

    def test_assistant_run_rejects_missing_input(self) -> None:
        client = TestClient(app)

        response = client.post(
            "/assistant/run",
            json={
                "clientMessageId": "client-1",
                "mode": "daily_explain",
                "intent": "free_chat",
                "message": {},
            },
        )

        self.assertEqual(response.status_code, 400)

    def test_assistant_run_stream_returns_sse_events(self) -> None:
        client = TestClient(app)
        fake_service = CapturingRunService()

        with patch("python.ai_orchestrator.app.service", fake_service):
            with client.stream(
                "POST",
                "/assistant/run/stream",
                json={
                    "appConversationId": "conv-1",
                    "clientMessageId": "client-1",
                    "mode": "daily_explain",
                    "intent": "translate",
                    "scope": "message_only",
                    "message": {"text": "翻译 hello"},
                },
                headers={"Authorization": "Bearer token"},
            ) as response:
                body = "".join(response.iter_text())

        self.assertEqual(response.status_code, 200)
        self.assertIn("data: {", body)
        self.assertIn('"type": "run.started"', body)
        self.assertIn('"type": "message.delta"', body)
        self.assertIn('"delta": "he"', body)
        self.assertIn('"type": "message.completed"', body)
        self.assertEqual(fake_service.received["authorization"], "Bearer token")

    def test_assistant_route_debug_returns_routing_decision_json(self) -> None:
        client = TestClient(app)
        fake_service = CapturingRunService()

        with patch("python.ai_orchestrator.app.service", fake_service):
            response = client.post(
                "/assistant/route/debug",
                json={
                    "appConversationId": "conv-1",
                    "clientMessageId": "client-1",
                    "mode": "exam_boost",
                    "intent": "grade_writing",
                    "scope": "message_only",
                    "message": {"text": "帮我看看这篇作文是否跑题"},
                },
                headers={"Authorization": "Bearer token"},
            )

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["intent"], "writing_evaluation")
        self.assertEqual(body["route_type"], "run_workflow")
        self.assertEqual(body["workflow"], "writing_evaluation")
        self.assertEqual(body["target_agent"], "writing_evaluation")
        self.assertEqual(body["confidence"], 0.91)
        self.assertEqual(fake_service.received["authorization"], "Bearer token")


if __name__ == "__main__":
    unittest.main()
