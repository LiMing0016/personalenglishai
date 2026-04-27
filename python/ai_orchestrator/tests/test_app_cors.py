import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from python.ai_orchestrator.app import app
from python.ai_orchestrator.schemas.chat import AssistantReply


class CapturingAssistantService:
    def __init__(self) -> None:
        self.received: dict | None = None

    def is_configured(self) -> bool:
        return True

    async def chat(self, **kwargs) -> AssistantReply:
        self.received = kwargs
        return AssistantReply(reply="ok", agent_name="Router Agent")


class AppCorsTest(unittest.TestCase):
    def test_allows_local_vite_assistant_page_origin(self) -> None:
        client = TestClient(app)

        response = client.options(
            "/chat",
            headers={
                "Origin": "http://localhost:3000",
                "Access-Control-Request-Method": "POST",
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers["access-control-allow-origin"], "http://localhost:3000")

    def test_chat_forwards_study_stage_to_assistant_service(self) -> None:
        client = TestClient(app)
        fake_service = CapturingAssistantService()

        with patch("python.ai_orchestrator.app.service", fake_service):
            response = client.post(
                "/chat",
                data={
                    "message": "Explain this sentence.",
                    "conversation_id": "conv-1",
                    "study_stage": "postgrad",
                    "assistant_mode": "exam",
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(fake_service.received["study_stage"], "postgrad")
        self.assertEqual(fake_service.received["assistant_mode"], "exam")
        self.assertEqual(fake_service.received["message"], "Explain this sentence.")

    def test_chat_forwards_authorization_header_to_assistant_service(self) -> None:
        client = TestClient(app)
        fake_service = CapturingAssistantService()

        with patch("python.ai_orchestrator.app.service", fake_service):
            response = client.post(
                "/chat",
                data={
                    "message": "Polish this.",
                    "conversation_id": "conv-1",
                },
                headers={"Authorization": "Bearer token"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(fake_service.received["authorization"], "Bearer token")


if __name__ == "__main__":
    unittest.main()
