from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class RouteDecisionRegressionCase:
    user_message: str
    expected_intent: str
    expected_route_type: str
    expected_workflow: str | None
    expected_target_agent: str | None


ROUTE_DECISION_REGRESSION_CASES: tuple[RouteDecisionRegressionCase, ...] = (
    RouteDecisionRegressionCase(
        "帮我看看这篇作文是否跑题，并给一个分数。",
        "writing_evaluation",
        "run_workflow",
        "writing_evaluation",
        "writing_evaluation",
    ),
    RouteDecisionRegressionCase(
        "我只有作文，没有题目，你能判断跑题吗？",
        "writing_evaluation",
        "ask_clarification",
        None,
        None,
    ),
    RouteDecisionRegressionCase(
        "这个题目第一段应该怎么开头？",
        "first_draft_coach",
        "run_workflow",
        "first_draft_coach",
        "first_draft_coach",
    ),
    RouteDecisionRegressionCase(
        "我下一段该写什么？",
        "first_draft_coach",
        "run_workflow",
        "first_draft_coach",
        "first_draft_coach",
    ),
    RouteDecisionRegressionCase(
        "I very like English. 这句现在写得怎么样？",
        "realtime_sentence_feedback",
        "run_workflow",
        "realtime_sentence_feedback",
        "realtime_sentence_feedback",
    ),
    RouteDecisionRegressionCase(
        "我刚写完这一段，帮我看看当前问题。",
        "realtime_sentence_feedback",
        "run_workflow",
        "realtime_sentence_feedback",
        "realtime_sentence_feedback",
    ),
    RouteDecisionRegressionCase("润色这句话：I very like English.", "polish", "run_workflow", "specialist_single_turn", "polish"),
    RouteDecisionRegressionCase("分析这个句子结构：What matters most is practice.", "sentence_structure", "run_workflow", "specialist_single_turn", "sentence_structure"),
    RouteDecisionRegressionCase("这个单词 nuanced 怎么用？", "vocab", "run_workflow", "specialist_single_turn", "vocab"),
    RouteDecisionRegressionCase("翻译这句话：学习英语需要长期坚持。", "translation", "run_workflow", "specialist_single_turn", "translation"),
    RouteDecisionRegressionCase("给这段作文评分。", "scoring", "run_workflow", "specialist_single_turn", "scoring"),
    RouteDecisionRegressionCase("给我出 3 道中考英语写作练习题。", "practice_design", "run_workflow", "specialist_single_turn", "practice_design"),
    RouteDecisionRegressionCase("我现在英语能力怎么样？", "ability_profile", "run_workflow", "specialist_single_turn", "ability_profile"),
    RouteDecisionRegressionCase("帮我制定一个两周写作提升计划。", "learning_planner", "run_workflow", "specialist_single_turn", "learning_planner"),
    RouteDecisionRegressionCase("谢谢，明白了。", "free_chat", "answer_direct", None, None),
    RouteDecisionRegressionCase("这个解释可以再短一点吗？", "free_chat", "answer_direct", None, None),
    RouteDecisionRegressionCase("帮我写一个 Java 文件上传接口。", "out_of_scope", "out_of_scope", None, None),
)
