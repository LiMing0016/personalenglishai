import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from python.ai_orchestrator.app import app
from python.ai_orchestrator.schemas.assistant_request import AssistantRunMetadata
from python.ai_orchestrator.schemas.chat import AssistantReply


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


if __name__ == "__main__":
    unittest.main()
