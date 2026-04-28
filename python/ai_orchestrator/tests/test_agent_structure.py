import inspect
import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from python.ai_orchestrator.agents.router import create_router_agent
from python.ai_orchestrator.agents.specialists import SPECIALIST_AGENT_SPECS, _log_handoff
from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.routing import HandoffRoutingMetadata


class AgentStructureTest(unittest.TestCase):
    def test_router_agent_uses_eight_specialist_handoffs(self) -> None:
        router_agent = create_router_agent("test-model")

        self.assertEqual(router_agent.name, "Router Agent")
        self.assertEqual(router_agent.model, "test-model")
        self.assertEqual(
            [handoff.agent_name for handoff in router_agent.handoffs],
            [
                "Polish Agent",
                "Sentence Structure Agent",
                "Vocab Agent",
                "Translation Agent",
                "Scoring Agent",
                "Prompt Design Agent",
                "Ability Profile Agent",
                "Learning Planner Agent",
            ],
        )

    def test_router_agent_exposes_specialists_as_tools_for_multi_intent_tasks(self) -> None:
        router_agent = create_router_agent("test-model")

        self.assertEqual(
            [tool.name for tool in router_agent.tools],
            [
                "polish_text",
                "analyze_sentence_structure",
                "explain_vocab",
                "translate_text",
                "score_english",
                "design_practice_prompt",
                "explain_ability_profile",
                "plan_learning_path",
            ],
        )

    def test_specialists_have_handoff_and_tool_descriptions(self) -> None:
        for spec in SPECIALIST_AGENT_SPECS:
            with self.subTest(agent=spec.name):
                self.assertTrue(spec.handoff_description)
                self.assertTrue(spec.tool_name)
                self.assertTrue(spec.tool_description)

    def test_handoff_tools_accept_routing_metadata(self) -> None:
        router_agent = create_router_agent("test-model")

        for specialist_handoff in router_agent.handoffs:
            with self.subTest(handoff=specialist_handoff.agent_name):
                properties = specialist_handoff.input_json_schema["properties"]
                self.assertIn("intent", properties)
                self.assertIn("reason", properties)
                self.assertIn("confidence", properties)

    def test_handoff_logging_includes_conversation_id(self) -> None:
        on_handoff = _log_handoff("Polish Agent")
        ctx = SimpleNamespace(context=SimpleNamespace(conversation_id="conv-route-1"))
        metadata = HandoffRoutingMetadata(intent="polish", reason="user asked to polish", confidence=0.9)

        with patch("python.ai_orchestrator.agents.specialists.log") as log:
            asyncio.run(on_handoff(ctx, metadata))

        log.info.assert_called_once()
        log_call = log.info.call_args
        self.assertIn("conversation_id=%s", log_call.args[0])
        self.assertIn("conv-route-1", log_call.args)

    def test_prompt_assets_are_loaded_from_prompt_module(self) -> None:
        for spec in SPECIALIST_AGENT_SPECS:
            with self.subTest(prompt=spec.prompt_key):
                instructions = load_agent_instructions(spec.prompt_key)
                self.assertIn("PEAI 英语学习助手", instructions)

        instructions = load_agent_instructions("router")

        self.assertIn("PEAI 英语学习助手", instructions)
        self.assertIn("Scoring Agent", instructions)

    def test_router_prompt_defines_routing_policy(self) -> None:
        instructions = load_agent_instructions("router")

        self.assertIn("PEAI Learning Orchestrator", instructions)
        self.assertIn("任务编排 Agent", instructions)
        self.assertIn("单一明确任务", instructions)
        self.assertIn("多意图", instructions)
        self.assertIn("非英语学习", instructions)
        self.assertIn("不要向用户暴露", instructions)
        self.assertIn("Sentence Structure Agent", instructions)
        self.assertIn("Translation Agent", instructions)
        self.assertIn("Ability Profile Agent", instructions)

    def test_router_prompt_uses_chinese_role_labels(self) -> None:
        instructions = load_agent_instructions("router")

        self.assertIn("润色 Agent（Polish Agent）", instructions)
        self.assertIn("句子结构 Agent（Sentence Structure Agent）", instructions)
        self.assertIn("词汇 Agent（Vocab Agent）", instructions)
        self.assertIn("翻译 Agent（Translation Agent）", instructions)
        self.assertIn("评分 Agent（Scoring Agent）", instructions)

    def test_router_prompt_routes_by_normalized_intent(self) -> None:
        instructions = load_agent_instructions("router")

        self.assertIn("先判断标准 intent，再按 intent 选择目标 Agent", instructions)
        self.assertIn("intent=polish", instructions)
        self.assertIn("intent=sentence_structure", instructions)
        self.assertIn("intent=vocab", instructions)
        self.assertIn("intent=translation", instructions)
        self.assertIn("intent=scoring", instructions)
        self.assertIn("intent=practice_design", instructions)
        self.assertNotIn("intent=prompt_design", instructions)

    def test_router_prompt_keeps_orchestrator_boundaries(self) -> None:
        instructions = load_agent_instructions("router")

        self.assertIn("不自己替代专职工具完成复杂评分、润色、翻译、词汇分析或练习设计", instructions)
        self.assertIn("单意图任务优先转交给对应专职 Agent", instructions)
        self.assertIn("多意图任务调用多个专职 Agent 工具并汇总", instructions)
        self.assertIn("不要列出", instructions)

    def test_router_prompt_handles_contextual_follow_up_intents(self) -> None:
        instructions = load_agent_instructions("router")

        self.assertIn("上下文追问", instructions)
        self.assertIn("还有其他方案吗", instructions)
        self.assertIn("继承上一轮的标准 intent", instructions)
        self.assertIn("learning_planner", instructions)

    def test_polish_prompt_defines_flexible_writing_versions(self) -> None:
        instructions = load_agent_instructions("polish")

        self.assertIn("必须服从运行时注入的「用户画像上下文」和「学段输出标准」", instructions)
        self.assertIn("输出长短和展开程度由用户任务复杂度决定", instructions)
        self.assertIn("适度扩充版", instructions)
        self.assertIn("高复用写作版", instructions)
        self.assertIn("内容扩展与句式多样版", instructions)
        self.assertIn("每个版本都必须给出推荐理由", instructions)
        self.assertIn("为什么好", instructions)
        self.assertIn("扩展幅度、句式复杂度和写作迁移要求必须以运行时注入的当前学段标准为准", instructions)
        self.assertNotIn("如果用户没有指定格式，按以下结构输出", instructions)
        self.assertNotIn("小学、初中阶段", instructions)
        self.assertNotIn("雅思、托福、考研等考试场景", instructions)

    def test_service_does_not_define_agents_or_prompt_bodies_inline(self) -> None:
        from python.ai_orchestrator import assistant_service

        source = inspect.getsource(assistant_service.AssistantAgentService._get_router_agent)

        self.assertIn("create_router_agent", source)
        self.assertNotIn("Agent(", source)
        self.assertNotIn("你的职责是", source)


if __name__ == "__main__":
    unittest.main()
