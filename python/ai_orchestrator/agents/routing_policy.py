from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class RoutingRegressionCase:
    user_message: str
    expected_targets: tuple[str, ...]
    multi_intent: bool = False
    out_of_scope: bool = False


ROUTING_REGRESSION_CASES: tuple[RoutingRegressionCase, ...] = (
    RoutingRegressionCase("润色这句话：I very like learn English.", ("Polish Agent",)),
    RoutingRegressionCase("分析这个句子结构：What matters most is whether we keep practicing.", ("Sentence Structure Agent",)),
    RoutingRegressionCase("这个单词 nuanced 怎么用？", ("Vocab Agent",)),
    RoutingRegressionCase("翻译这句话：学习英语需要长期坚持。", ("Translation Agent",)),
    RoutingRegressionCase("给这段作文评分，并指出主要问题。", ("Scoring Agent",)),
    RoutingRegressionCase("给我出几道考研英语写作训练题。", ("Prompt Design Agent",)),
    RoutingRegressionCase("我现在英语能力怎么样？", ("Ability Profile Agent",)),
    RoutingRegressionCase("帮我制定一个两周英语学习计划。", ("Learning Planner Agent",)),
    RoutingRegressionCase(
        "翻译并润色这句话：The number of appliances show an increasing trend.",
        ("Translation Agent", "Polish Agent"),
        multi_intent=True,
    ),
    RoutingRegressionCase("帮我写一段 Java 文件上传代码。", tuple(), out_of_scope=True),
)
