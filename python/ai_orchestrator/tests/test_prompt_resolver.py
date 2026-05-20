import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from python.ai_orchestrator.agents.route_decision import create_route_agent
from python.ai_orchestrator.agents.router import create_router_agent
from python.ai_orchestrator.prompts.resolver import PromptResolutionError
from python.ai_orchestrator.prompts.resolver import remote_prompt_env_names
from python.ai_orchestrator.prompts.resolver import resolve_agent_prompt_kwargs


class PromptResolverTest(unittest.TestCase):
    def test_default_source_uses_local_instructions(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            kwargs = resolve_agent_prompt_kwargs("router")

        self.assertIn("instructions", kwargs)
        self.assertNotIn("prompt", kwargs)
        self.assertIn("PEAI 学习编排 Agent", kwargs["instructions"])

    def test_hybrid_source_uses_remote_prompt_when_configured(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "AI_PROMPT_ROUTE_DECISION_ID": "pmpt_route_123",
                "AI_PROMPT_ROUTE_DECISION_VERSION": "7",
                "AI_PROMPT_ROUTE_DECISION_VARIABLES_JSON": '{"release": "smoke"}',
            },
            clear=True,
        ):
            kwargs = resolve_agent_prompt_kwargs("route_decision")

        self.assertEqual(
            kwargs,
            {
                "prompt": {
                    "id": "pmpt_route_123",
                    "version": "7",
                    "variables": {"release": "smoke"},
                }
            },
        )

    def test_hybrid_source_falls_back_to_local_when_remote_prompt_is_missing(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
            },
            clear=True,
        ):
            kwargs = resolve_agent_prompt_kwargs("router")

        self.assertIn("instructions", kwargs)
        self.assertNotIn("prompt", kwargs)

    def test_dynamic_local_instructions_render_runtime_learning_context(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            kwargs = resolve_agent_prompt_kwargs("router", dynamic=True)

        self.assertIn("instructions", kwargs)
        self.assertTrue(callable(kwargs["instructions"]))

        rendered = kwargs["instructions"](
            SimpleNamespace(context=SimpleNamespace(study_stage="postgrad", assistant_mode="exam")),
            SimpleNamespace(name="Router Agent"),
        )

        self.assertIn("PEAI 学习编排 Agent", rendered)
        self.assertIn("# Runtime Learning Context", rendered)
        self.assertIn("- 学段: 考研", rendered)
        self.assertIn("考研独立标准", rendered)
        self.assertIn("- 当前模式: 考试模式", rendered)

    def test_dynamic_prompt_keeps_remote_prompt_reference_when_configured(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "AI_PROMPT_ROUTER_ID": "pmpt_router_123",
            },
            clear=True,
        ):
            kwargs = resolve_agent_prompt_kwargs("router", dynamic=True)

        self.assertEqual(kwargs, {"prompt": {"id": "pmpt_router_123"}})

    def test_remote_source_requires_prompt_id(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "remote",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
            },
            clear=True,
        ):
            with self.assertRaises(PromptResolutionError):
                resolve_agent_prompt_kwargs("router")

    def test_remote_source_requires_openai_platform_base_url(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "remote",
                "OPENAI_BASE_URL": "https://api.moonshot.cn/v1",
                "AI_PROMPT_ROUTER_ID": "pmpt_router_123",
            },
            clear=True,
        ):
            with self.assertRaises(PromptResolutionError):
                resolve_agent_prompt_kwargs("router")

    def test_invalid_variables_json_is_rejected(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "AI_PROMPT_ROUTER_ID": "pmpt_router_123",
                "AI_PROMPT_ROUTER_VARIABLES_JSON": "[]",
            },
            clear=True,
        ):
            with self.assertRaises(PromptResolutionError):
                resolve_agent_prompt_kwargs("router")

    def test_remote_prompt_env_names_are_stable(self) -> None:
        self.assertEqual(
            remote_prompt_env_names("prompt_sheet_canvas"),
            (
                "AI_PROMPT_PROMPT_SHEET_CANVAS_ID",
                "AI_PROMPT_PROMPT_SHEET_CANVAS_VERSION",
                "AI_PROMPT_PROMPT_SHEET_CANVAS_VARIABLES_JSON",
            ),
        )

    def test_route_agent_can_use_remote_prompt_without_instructions(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "AI_PROMPT_ROUTE_DECISION_ID": "pmpt_route_123",
            },
            clear=True,
        ):
            agent = create_route_agent("test-model")

        self.assertIsNone(agent.instructions)
        self.assertEqual(agent.prompt, {"id": "pmpt_route_123"})

    def test_router_remote_prompt_does_not_force_specialists_remote(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "AI_PROMPT_ROUTER_ID": "pmpt_router_123",
            },
            clear=True,
        ):
            router = create_router_agent("test-model")

        self.assertIsNone(router.instructions)
        self.assertEqual(router.prompt, {"id": "pmpt_router_123"})
        self.assertTrue(router.handoffs[0].agent_name)


if __name__ == "__main__":
    unittest.main()
