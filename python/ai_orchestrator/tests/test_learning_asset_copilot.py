import os
import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from python.ai_orchestrator.agents.learning_asset_copilot import create_learning_asset_copilot_agent
from python.ai_orchestrator.app import app
from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.learning_assets import LearningAssetOrganizeRequest
from python.ai_orchestrator.schemas.learning_assets import LearningAssetOrganizeResponse
from python.ai_orchestrator.services.agent_session_runner import AgentSessionResult
from python.ai_orchestrator.services.learning_asset_copilot import LearningAssetCopilotService


class CapturingLearningAssetService:
    def __init__(self) -> None:
        self.received = None

    async def organize(self, request):
        self.received = request
        return LearningAssetOrganizeResponse(candidateMarkdown="# opportunity\n\n**中文释义：** 机会")


class LearningAssetCopilotTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.env_patch = patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=False)
        self.env_patch.start()

    def tearDown(self) -> None:
        self.env_patch.stop()

    def test_prompt_asset_defines_canvas_copilot_boundary(self) -> None:
        instructions = load_agent_instructions("learning_asset_copilot")

        self.assertIn("学习资产画布 Copilot", instructions)
        self.assertIn("不是聊天助手", instructions)
        self.assertIn("只输出 Markdown", instructions)
        self.assertIn("不要删除用户自己的“我的笔记”内容", instructions)

    def test_agent_uses_python_prompt_asset(self) -> None:
        agent = create_learning_asset_copilot_agent("test-model")

        self.assertEqual(agent.name, "Learning Asset Copilot Agent")
        self.assertEqual(agent.model, "test-model")
        self.assertIn("学习资产画布 Copilot", agent.instructions)

    async def test_service_runs_agent_with_asset_action_and_template(self) -> None:
        service = LearningAssetCopilotService(model="test-model", session_db_path="/tmp/test-learning-assets.db")
        request = LearningAssetOrganizeRequest(
            type="grammar",
            title="a window of opportunity",
            selectedText="a window of opportunity",
            currentMarkdown="# a window of opportunity",
            action="expand",
            instruction="补充一个自然例句",
        )

        with patch(
            "python.ai_orchestrator.services.learning_asset_copilot.run_agent_session",
            new=AsyncMock(return_value=AgentSessionResult(final_output="# a window of opportunity\n\n**类型：** 语法笔记", agent_name="Learning Asset Copilot Agent")),
        ) as runner:
            response = await service.organize(request)

        self.assertTrue(response.candidate_markdown.startswith("# a window"))
        runner.assert_awaited_once()
        kwargs = runner.await_args.kwargs
        self.assertEqual(kwargs["conversation_id"], "learning-asset-copilot")
        self.assertFalse(kwargs["use_session"])
        self.assertEqual(kwargs["trace_workflow_name"], "learning_asset_copilot")
        self.assertIn("动作：expand", kwargs["agent_input"])
        self.assertIn("扩展当前学习笔记", kwargs["agent_input"])
        self.assertIn("默认语法笔记模板", kwargs["agent_input"])
        self.assertIn("自定义要求：补充一个自然例句", kwargs["agent_input"])

    def test_http_endpoint_calls_learning_asset_service(self) -> None:
        client = TestClient(app)
        fake_service = CapturingLearningAssetService()

        with patch("python.ai_orchestrator.app.learning_asset_copilot_service", fake_service):
            response = client.post(
                "/learning-assets/organize",
                json={
                    "type": "vocabulary",
                    "title": "opportunity",
                    "selectedText": "opportunity",
                    "currentMarkdown": "# opportunity",
                    "action": "polish",
                    "instruction": "改得更适合复习",
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["candidateMarkdown"], "# opportunity\n\n**中文释义：** 机会")
        self.assertEqual(fake_service.received.selected_text, "opportunity")
        self.assertEqual(fake_service.received.current_markdown, "# opportunity")
        self.assertEqual(fake_service.received.action, "polish")
        self.assertEqual(fake_service.received.instruction, "改得更适合复习")


if __name__ == "__main__":
    unittest.main()
