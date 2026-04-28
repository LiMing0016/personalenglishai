import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.prompt_sheet import ChartSpec
from python.ai_orchestrator.schemas.prompt_sheet import GenerateExamPromptRequest
from python.ai_orchestrator.schemas.prompt_sheet import GenerateExamPromptResponse
from python.ai_orchestrator.schemas.prompt_sheet import PromptSheetCanvasToolInput
from python.ai_orchestrator.schemas.prompt_sheet import PromptSheetChatRequest
from python.ai_orchestrator.schemas.prompt_sheet import PromptSheetChatResponse
from python.ai_orchestrator.services.prompt_sheet_workflow import PromptSheetWorkflowService


class PromptSheetWorkflowServiceTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.env_patch = patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=False)
        self.env_patch.start()

    def tearDown(self) -> None:
        self.env_patch.stop()

    def test_prompt_assets_are_loaded_from_python_prompt_assets(self) -> None:
        chat_prompt = load_agent_instructions("prompt_sheet_chat")
        canvas_prompt = load_agent_instructions("prompt_sheet_canvas")

        self.assertIn("PEAI 英语写作题目设计页的对话 Agent", chat_prompt)
        self.assertIn("generate_prompt_sheet_canvas", chat_prompt)
        self.assertIn("默认理解为：生成一份可用于写作练习的作文题单", chat_prompt)
        self.assertIn("不要说“我给你写了一篇作文”", chat_prompt)
        self.assertIn("PEAI 英语考试写作题单 Canvas Agent", canvas_prompt)
        self.assertIn("结构化题单资产编辑器", canvas_prompt)
        self.assertIn("输出前自检", canvas_prompt)
        self.assertIn("字段一致性", canvas_prompt)
        self.assertIn("不生成真实图片", canvas_prompt)
        self.assertIn("不要生成范文、答案、提纲或写作正文", canvas_prompt)
        self.assertIn("promptTypeStandard", canvas_prompt)
        self.assertIn("不要把聊天解释写进结构化字段", canvas_prompt)
        self.assertIn("comicScenes", canvas_prompt)
        self.assertNotIn("图表题必须输出完整 `columns` 和 `rows`", canvas_prompt)
        self.assertNotIn("IELTS 图表题要求", canvas_prompt)
        self.assertNotIn("Summarise the information", canvas_prompt)

    def test_chat_agent_uses_canvas_agent_as_tool(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        chat_agent = service._get_chat_agent()

        self.assertEqual(chat_agent.name, "Prompt Sheet Chat Agent")
        self.assertEqual([tool.name for tool in chat_agent.tools], ["generate_prompt_sheet_canvas"])

    def test_canvas_tool_uses_structured_request_schema(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        tool = service._get_canvas_tool()
        schema = tool.params_json_schema

        self.assertIn("request", schema["properties"])
        self.assertNotIn("input", schema["properties"])
        request_schema = schema["$defs"]["PromptSheetCanvasToolInput"]
        self.assertIn("instruction", request_schema["properties"])
        self.assertIn("topic", request_schema["properties"])
        self.assertNotIn("promptType", request_schema["properties"])
        self.assertIn("genre", request_schema["properties"])
        self.assertIn("preserveDetails", request_schema["properties"])

    def test_canvas_tool_request_from_chat_context_injects_dynamic_standards(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        canvas_request = service._build_canvas_request_from_chat_tool_input(
            PromptSheetChatRequest(
                message="给我生成一篇折线图作文题",
                studyStage="postgrad",
                taskType="task2",
                wordRange="160-200",
                requirements="描述主要趋势、解释原因并评论意义。",
            ),
            PromptSheetCanvasToolInput(
                instruction="Create/update a postgraduate chart-style prompt sheet about 折线图作文.",
                topic="折线图作文",
                genre="图表作文",
            ),
        )
        payload = service._build_agent_payload(canvas_request)

        self.assertEqual(payload["studyStage"], "postgrad")
        self.assertEqual(payload["promptType"], "chart")
        self.assertEqual(payload["taskType"], "task2")
        self.assertIn("考研题单风格", payload["examPromptStandard"])
        self.assertIn("chart 题单标准", payload["promptTypeStandard"])
        self.assertIn("chart:postgrad 题单标准", payload["promptTypeStandard"])
        self.assertIn("优先使用当前运行环境中可用的检索工具", payload["promptTypeStandard"])
        self.assertIn("interpret / describe the chart or table", payload["promptTypeStandard"])
        self.assertIn("give your comments", payload["promptTypeStandard"])
        self.assertIn("不要使用 IELTS Task 1", payload["promptTypeStandard"])

    def test_canvas_tool_request_keeps_structured_fields_ahead_of_stale_canvas_text(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        canvas_request = service._build_canvas_request_from_chat_tool_input(
            PromptSheetChatRequest(
                message="可以",
                studyStage="postgrad",
                taskType="task2",
                promptType="chart",
                genre="chart",
                wordRange="160-200",
                currentTopic="China GDP and inflation rate over the past 10 years",
                currentPromptText="TOEFL Task 1 line chart summary about GDP and inflation.",
            ),
            PromptSheetCanvasToolInput(
                instruction="把右侧题单改成考研漫画作文，主题是大学生在校生活。",
                topic="大学生在校生活",
                genre="picture essay",
                preserveDetails=["考研英语 task2", "漫画作文", "大学生在校生活", "160-200 words"],
            ),
        )
        payload = service._build_agent_payload(canvas_request)

        self.assertEqual(canvas_request.original_input, "把右侧题单改成考研漫画作文，主题是大学生在校生活。")
        self.assertEqual(payload["studyStage"], "postgrad")
        self.assertEqual(payload["promptType"], "comic")
        self.assertEqual(payload["taskType"], "task2")
        self.assertEqual(payload["genre"], "picture essay")
        self.assertIn("preserveDetails", payload)
        self.assertIn("comic 题单标准", payload["promptTypeStandard"])
        self.assertIn("comic:postgrad 题单标准", payload["promptTypeStandard"])
        self.assertNotIn("TOEFL Task 1", json.dumps(payload, ensure_ascii=False))

    def test_build_agent_input_renders_readable_sections_for_traces(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        agent_input = service._build_agent_input(
            GenerateExamPromptRequest(
                originalInput="生成一篇折线图作文题",
                topic="A social indicator over the past decade",
                studyStage="postgrad",
                promptType="chart",
                taskType="task2",
            )
        )

        self.assertTrue(agent_input.startswith("# Prompt Sheet Agent Input"))
        self.assertIn("## Request", agent_input)
        self.assertIn('"studyStage": "postgrad"', agent_input)
        self.assertIn("## examPromptStandard", agent_input)
        self.assertIn("## promptTypeStandard", agent_input)
        self.assertIn("[chart 题单标准]", agent_input)

    def test_build_agent_input_injects_ielts_standard_only_for_ielts(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        payload = service._build_agent_payload(
            GenerateExamPromptRequest(
                originalInput="生成一篇折线图作文题",
                topic="Online learning trends",
                studyStage="ielts",
                promptType="chart",
                taskType="task1",
            )
        )

        self.assertIn("examPromptStandard", payload)
        self.assertIn("IELTS 题单风格", payload["examPromptStandard"])
        self.assertIn("promptTypeStandard", payload)
        self.assertIn("chart 题单标准", payload["promptTypeStandard"])
        self.assertIn("chart:ielts 题单标准", payload["promptTypeStandard"])
        self.assertIn("Summarise the information", payload["promptTypeStandard"])
        self.assertIn("优先使用当前运行环境中可用的检索工具", payload["promptTypeStandard"])
        self.assertIn("不要编造真实数据", payload["promptTypeStandard"])

    def test_build_agent_input_does_not_inject_ielts_standard_for_toefl(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        payload = service._build_agent_payload(
            GenerateExamPromptRequest(
                originalInput="生成一篇关于 online learning 的 TOEFL 写作题",
                topic="Online learning",
                studyStage="toefl",
                promptType="general",
                taskType="task1",
            )
        )

        self.assertIn("examPromptStandard", payload)
        self.assertIn("TOEFL 题单风格", payload["examPromptStandard"])
        self.assertIn("不要套用 IELTS Task 1", payload["examPromptStandard"])
        self.assertNotIn("Summarise the information", payload["examPromptStandard"])
        self.assertIn("promptTypeStandard", payload)
        self.assertIn("general:toefl 题单标准", payload["promptTypeStandard"])

    def test_build_agent_input_loads_stage_and_prompt_type_standards_for_each_stage(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")
        cases = {
            "primary": ("小学题单风格", "general:primary 题单标准"),
            "junior": ("初中题单风格", "general:junior 题单标准"),
            "senior": ("高中题单风格", "general:senior 题单标准"),
            "cet4": ("四级题单风格", "general:cet4 题单标准"),
            "cet6": ("六级题单风格", "general:cet6 题单标准"),
            "postgrad": ("考研题单风格", "general:postgrad 题单标准"),
            "ielts": ("IELTS 题单风格", "general:ielts 题单标准"),
            "toefl": ("TOEFL 题单风格", "general:toefl 题单标准"),
        }

        for study_stage, (exam_marker, type_marker) in cases.items():
            with self.subTest(study_stage=study_stage):
                payload = service._build_agent_payload(
                    GenerateExamPromptRequest(
                        originalInput="生成一篇作文题",
                        topic="School life",
                        studyStage=study_stage,
                        promptType="general",
                    )
                )
                self.assertIn(exam_marker, payload["examPromptStandard"])
                self.assertIn("general 题单标准", payload["promptTypeStandard"])
                self.assertIn(type_marker, payload["promptTypeStandard"])

    def test_build_agent_input_loads_chart_type_override_for_toefl(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")

        payload = service._build_agent_payload(
            GenerateExamPromptRequest(
                originalInput="生成一篇 TOEFL 数据图作文题",
                topic="Technology use",
                studyStage="toefl",
                promptType="chart",
            )
        )

        self.assertIn("chart 题单标准", payload["promptTypeStandard"])
        self.assertIn("chart:toefl 题单标准", payload["promptTypeStandard"])
        self.assertIn("不要使用 `Summarise the information...`", payload["promptTypeStandard"])
        self.assertIn("优先使用当前运行环境中可用的检索工具", payload["promptTypeStandard"])
        self.assertIn("只有在用户没有要求真实数据", payload["promptTypeStandard"])

    def test_prompt_type_standards_have_stage_specific_matrix(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")
        stages = ["primary", "junior", "senior", "cet4", "cet6", "postgrad", "ielts", "toefl"]
        prompt_types = ["general", "material", "chart", "comic"]

        for study_stage in stages:
            for prompt_type in prompt_types:
                with self.subTest(study_stage=study_stage, prompt_type=prompt_type):
                    payload = service._build_agent_payload(
                        GenerateExamPromptRequest(
                            originalInput="生成一篇作文题",
                            topic="School life",
                            studyStage=study_stage,
                            promptType=prompt_type,
                        )
                    )
                    self.assertIn(
                        f"{prompt_type}:{study_stage} 题单标准",
                        payload["promptTypeStandard"],
                    )

    async def test_chat_returns_structured_response_from_agents_sdk(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")
        fake_usage = SimpleNamespace(
            requests=1,
            input_tokens=1400,
            output_tokens=120,
            total_tokens=1520,
            input_tokens_details=SimpleNamespace(cached_tokens=1024),
            output_tokens_details=SimpleNamespace(reasoning_tokens=0),
        )
        output = PromptSheetChatResponse(
            reply="我已经把右侧题单改成 IELTS Task 2 环保主题。",
            action="update_prompt_sheet",
            needsCanvasUpdate=True,
            needsConfirmation=False,
            canvasInstruction="改成环保主题 Task 2。",
            promptSheet=GenerateExamPromptResponse(
                promptType="general",
                topic="Environmental Responsibility",
                promptText="Some people believe individuals should protect the environment, while others think governments should take the main responsibility. Discuss both views and give your own opinion.",
                requirements="Discuss both views and give your own opinion.",
                wordRange="250+",
                sourceType="ai_generated",
                taskType="task2",
            ),
        )
        fake_result = SimpleNamespace(
            final_output=output,
            last_agent=SimpleNamespace(name="Prompt Sheet Chat Agent"),
            context_wrapper=SimpleNamespace(usage=fake_usage),
            new_items=[SimpleNamespace(type="tool_call_item", raw_item=SimpleNamespace(name="generate_prompt_sheet_canvas"))],
            raw_responses=[SimpleNamespace(response_id="resp-chat-1", model="test-model")],
        )

        with (
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=fake_result) as run,
            patch("python.ai_orchestrator.services.prompt_sheet_workflow.log") as log,
        ):
            response = await service.chat(PromptSheetChatRequest(message="把主题换成环保", studyStage="ielts"))

        run.assert_awaited_once()
        self.assertEqual(run.await_args.kwargs["context"].study_stage, "ielts")
        agent_input = run.await_args.args[1]
        self.assertIn("# Prompt Sheet Agent Input", agent_input)
        self.assertIn("## Request", agent_input)
        self.assertIn('"message": "把主题换成环保"', agent_input)
        self.assertIn("## examPromptStandard", agent_input)
        self.assertTrue(response.needs_canvas_update)
        self.assertIsNotNone(response.prompt_sheet)
        assert response.prompt_sheet is not None
        self.assertEqual(response.prompt_sheet.task_type, "task2")
        openai_call = next(call for call in log.info.call_args_list if "OPENAI_AGENTS_RUN" in call.args[0])
        self.assertIn("workflow=%s", openai_call.args[0])
        self.assertIn("input_cached_tokens=%s", openai_call.args[0])
        self.assertIn("tool_calls=%s", openai_call.args[0])
        self.assertIn("response_ids=%s", openai_call.args[0])
        self.assertIn("prompt_sheet_chat", openai_call.args)
        self.assertIn(1024, openai_call.args)
        self.assertIn(("generate_prompt_sheet_canvas",), openai_call.args)
        self.assertIn(("resp-chat-1",), openai_call.args)

    async def test_generate_defaults_missing_task_type(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")
        output = GenerateExamPromptResponse(
            promptType="general",
            topic="青年责任",
            promptText="Write an essay on the responsibilities of young people.",
            requirements="give your comments",
            wordRange="160-200",
            sourceType="ai_generated",
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=output)):
            response = await service.generate(
                GenerateExamPromptRequest(
                    originalInput="给我一题关于青年责任的英语作文",
                    topic="青年责任",
                    studyStage="postgrad",
                    promptType="general",
                )
            )

        self.assertEqual(response.task_type, "task1")
        self.assertEqual(response.source_type, "ai_generated")

    async def test_generate_injects_postgrad_exam_style_reference(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")
        output = GenerateExamPromptResponse(
            promptType="chart",
            topic="China GDP and Inflation Rate",
            promptText="Write an original exam-style prompt.",
            requirements="Describe and interpret the chart, then give comments.",
            wordRange="160-200",
            sourceType="ai_generated",
            taskType="task2",
            chartSpec=ChartSpec(
                title="China GDP and Inflation Rate",
                displayType="chart",
                columns=["Year", "GDP", "Inflation rate"],
                rows=[["2014", "10", "2.0"], ["2023", "18", "3.1"]],
            ),
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=output)) as run:
            await service.generate(
                GenerateExamPromptRequest(
                    originalInput="生成一篇考研图表作文题，主题是中国GDP和通胀率近十年变化",
                    topic="中国GDP和通胀率近十年变化",
                    studyStage="postgrad",
                    promptType="chart",
                    taskType="task2",
                    wordRange="160-200",
                )
            )

        agent_input = run.await_args.args[1]
        self.assertIn("examStyleReference", agent_input)
        self.assertIn("考研英语题库风格参考", agent_input)
        self.assertIn("原创题单", agent_input)
        self.assertNotIn("Mobile-phone subscriptions", agent_input)

    async def test_generate_repairs_chart_prompt_without_rows(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")
        output = GenerateExamPromptResponse(
            promptType="chart",
            topic="China's Global GDP Ranking and Inflation Ranking Over the Past 10 Years",
            promptText="A line chart comparing China's global GDP ranking and inflation ranking over the past 10 years.",
            requirements="Summarise the information and make comparisons where relevant.",
            wordRange="160-200",
            sourceType="ai_generated",
            taskType="task1",
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=output)):
            response = await service.generate(
                GenerateExamPromptRequest(
                    originalInput="改成折线图",
                    topic="中国GDP排名和通胀排名趋势",
                    studyStage="ielts",
                    promptType="chart",
                    taskType="task1",
                )
            )

        self.assertEqual(response.prompt_type, "chart")
        self.assertEqual(response.attachment_type, "visual")
        self.assertEqual(response.visual_kind, "chart")
        self.assertIsNotNone(response.chart_spec)
        assert response.chart_spec is not None
        self.assertGreaterEqual(len(response.chart_spec.rows), 2)
        self.assertIn("Inflation ranking", response.chart_spec.columns)

    async def test_chat_repairs_embedded_chart_prompt_sheet_without_rows(self) -> None:
        service = PromptSheetWorkflowService(model="test-model")
        output = PromptSheetChatResponse(
            reply="我已经整理成折线图版本。",
            action="update_prompt_sheet",
            needsCanvasUpdate=True,
            needsConfirmation=False,
            canvasInstruction="改成折线图。",
            promptSheet=GenerateExamPromptResponse(
                promptType="chart",
                topic="China's GDP and Engel Coefficient Trends",
                promptText="A line chart comparing China's GDP and Engel coefficient trends from 2014 to 2023.",
                requirements="Summarise the information.",
                wordRange="160-200",
                sourceType="ai_generated",
                taskType="task1",
            ),
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=output)):
            response = await service.chat(
                PromptSheetChatRequest(message="改成中国GDP和恩格尔系数2014-2023的双轴折线图", studyStage="ielts")
            )

        self.assertTrue(response.needs_canvas_update)
        self.assertIsNotNone(response.prompt_sheet)
        assert response.prompt_sheet is not None
        self.assertIsNotNone(response.prompt_sheet.chart_spec)
        assert response.prompt_sheet.chart_spec is not None
        self.assertEqual(response.prompt_sheet.chart_spec.display_type, "chart")
        self.assertGreaterEqual(len(response.prompt_sheet.chart_spec.rows), 2)
        self.assertIn("Engel coefficient (%)", response.prompt_sheet.chart_spec.columns)


if __name__ == "__main__":
    unittest.main()
