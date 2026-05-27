---
title: Jupyter 验证不同 Schema
status: draft
owner: ai
last_updated: 2026-05-21
review_cycle: monthly
related_code:
  - python/ai_orchestrator/schemas/assistant_request.py
  - python/ai_orchestrator/services/prompt_sheet_workflow.py
related_docs:
  - docs/learning/index.md
  - docs/agent/StructuredOutput学习笔记.md
  - docs/agent/FunctionCall学习笔记.md
---

# Jupyter 验证不同 Schema

这篇文档用于在 Jupyter Notebook 里快速验证一个核心想法：

> 同一个写作教练流程，可以根据不同阶段选择不同的结构化输出 Schema。

这个实验适合先在 Notebook 里看效果，确认思路成立后，再迁移到项目里的 Python agent 工作流。

## 1. 实验目标

写作教练不是每一步都输出同一种结构。

例如：

| 阶段 | 需要的结构 |
| --- | --- |
| 审题 | 题目类型、关键词、中心任务、风险点 |
| 提纲 | 中心论点、开头思路、主体段要点、结尾思路 |
| 润色 | 原文、修改后文本、修改点、修改原因 |

所以我们希望代码做到：

```text
stage = topic   -> TopicAnalysisOutput
stage = outline -> OutlineOutput
stage = polish  -> PolishOutput
```

## 2. 准备环境

先确认本地已经配置好 OpenAI API Key：

```powershell
$env:OPENAI_API_KEY="你的 key"
```

如果是在 Jupyter Notebook 里，也可以在启动 Notebook 前配置环境变量。

## 3. Cell 1：导入依赖

```python
from openai import OpenAI
from pydantic import BaseModel
from typing import Literal

# 创建 OpenAI 客户端。
# 默认会从环境变量 OPENAI_API_KEY 读取 API Key。
client = OpenAI()
```

说明：

- `OpenAI`：用于调用 OpenAI API。
- `BaseModel`：Pydantic 的基础类，用来定义结构化输出。
- `Literal`：限制阶段只能是指定字符串，避免传错阶段名。

## 4. Cell 2：定义不同阶段的 Schema

```python
class TopicAnalysisOutput(BaseModel):
    # 题目类型，例如 opinion essay / discussion essay。
    topic_type: str

    # 题目关键词。
    keywords: list[str]

    # 这个作文真正要求完成的核心任务。
    central_task: str

    # 写作时容易跑偏或遗漏的风险点。
    risks: list[str]

    # 建议采用的立场。
    suggested_position: str


class OutlineOutput(BaseModel):
    # 中心论点。
    thesis: str

    # 开头段的写作思路。
    introduction_idea: str

    # 主体段要点。
    body_points: list[str]

    # 结尾段的写作思路。
    conclusion_idea: str


class PolishOutput(BaseModel):
    # 原始文本。
    original_text: str

    # 润色后的文本。
    revised_text: str

    # 具体修改点。
    changes: list[str]

    # 为什么这样修改。
    reason: str


# 限制当前实验只支持这三个阶段。
WritingStage = Literal["topic", "outline", "polish"]


# 阶段到 Schema 的映射。
# 后面会根据 stage 自动选择对应的结构化输出类型。
STAGE_SCHEMAS = {
    "topic": TopicAnalysisOutput,
    "outline": OutlineOutput,
    "polish": PolishOutput,
}
```

关键点：

```text
不是一个大 Schema 包所有阶段，
而是每个阶段有自己的 Schema。
```

## 5. Cell 3：封装写作教练函数

```python
def run_writing_coach(stage: WritingStage, user_input: str):
    # 根据当前阶段选择不同的 Pydantic Schema。
    schema = STAGE_SCHEMAS[stage]

    response = client.responses.parse(
        model="gpt-4o-2024-08-06",
        input=[
            {
                "role": "system",
                "content": f"""
你是一个英语写作教练。
当前阶段是：{stage}

要求：
1. 只完成当前阶段的任务。
2. 不要提前完成其他阶段。
3. 输出必须符合当前阶段指定的结构化 Schema。
""",
            },
            {
                "role": "user",
                "content": user_input,
            },
        ],
        # 这里是重点：
        # text_format 接收的是当前阶段对应的 Schema。
        text_format=schema,
    )

    # output_parsed 是已经按照 Pydantic Schema 解析后的 Python 对象。
    return response.output_parsed
```

这段代码的核心是：

```python
schema = STAGE_SCHEMAS[stage]
```

也就是说，调用同一个函数时，只要传入不同的 `stage`，就能切换不同的输出结构。

## 6. Cell 4：测试审题阶段

```python
topic_result = run_writing_coach(
    stage="topic",
    user_input="Write an essay about whether AI is beneficial to education.",
)

topic_result
```

预期返回类型：

```python
TopicAnalysisOutput
```

可能看到类似结果：

```python
TopicAnalysisOutput(
    topic_type="opinion essay",
    keywords=["AI", "education", "beneficial"],
    central_task="Discuss whether AI is beneficial to education.",
    risks=[
        "Do not only describe AI technology.",
        "Need a clear position.",
        "Need education-related examples.",
    ],
    suggested_position="AI is beneficial when used properly, but it should not replace teachers.",
)
```

## 7. Cell 5：测试提纲阶段

这一步可以把上一步的审题结果传给模型，作为提纲阶段的上下文。

```python
outline_result = run_writing_coach(
    stage="outline",
    user_input=f"""
题目：
Write an essay about whether AI is beneficial to education.

审题结果：
{topic_result.model_dump_json(indent=2)}
""",
)

outline_result
```

预期返回类型：

```python
OutlineOutput
```

可能看到类似结果：

```python
OutlineOutput(
    thesis="AI can improve education by supporting personalized learning, but it should be used as a tool rather than a replacement for teachers.",
    introduction_idea="Introduce the growing use of AI in schools and state a balanced opinion.",
    body_points=[
        "AI can provide personalized exercises and instant feedback.",
        "Teachers are still needed for emotional support and deeper guidance.",
    ],
    conclusion_idea="Restate that AI is useful when combined with responsible teaching.",
)
```

## 8. Cell 6：测试润色阶段

```python
polish_result = run_writing_coach(
    stage="polish",
    user_input="""
Original paragraph:
AI is good for education because it can help students study.
It can answer questions and make learning easy.
""",
)

polish_result
```

预期返回类型：

```python
PolishOutput
```

可能看到类似结果：

```python
PolishOutput(
    original_text="AI is good for education because it can help students study. It can answer questions and make learning easy.",
    revised_text="AI can benefit education because it helps students learn more efficiently. For example, it can answer questions quickly and provide personalized support.",
    changes=[
        "Replaced 'is good for' with 'can benefit' to make the expression more formal.",
        "Changed 'help students study' to 'helps students learn more efficiently' for clearer meaning.",
        "Added an example sentence to make the paragraph more specific.",
    ],
    reason="The revised version is more formal, specific, and suitable for an essay.",
)
```

## 9. 观察重点

运行完三个阶段后，重点观察返回对象的类型：

```python
type(topic_result)
type(outline_result)
type(polish_result)
```

预期结果：

```text
TopicAnalysisOutput
OutlineOutput
PolishOutput
```

这说明同一个流程已经能根据阶段切换不同 Schema。

## 10. 和写作教练项目的关系

这个 Notebook 实验对应到项目里，可以演进成：

```text
writing_coach_context.action
  -> 选择阶段
  -> 选择 Pydantic Schema
  -> 创建或获取对应 Agent
  -> 运行模型
  -> 保存阶段结构化结果
  -> 生成最终用户回复
```

推荐的长期结构是：

```text
内部阶段状态：每个阶段一个 Schema
用户最终回复：统一一个 Response Schema
```

这样既能保证内部工作流可编排，也能保证前端展示格式稳定。

## 11. 常见问题

### 为什么不用一个大 Schema？

因为不同阶段关心的字段不一样。

一个大 Schema 很容易变成：

```text
topic_result: 有值
outline_result: null
draft_result: null
polish_result: null
final_check_result: null
```

这种结构后期会越来越臃肿。

### 这是不是 function calling？

不是。

这里使用的是 Structured Outputs：

```python
text_format=schema
```

它控制的是模型最终输出格式。

function calling 更适合让模型调用外部工具，例如：

- 查询数据库。
- 保存草稿。
- 读取历史作文。
- 调用评分服务。

### 这个实验有什么价值？

它验证了写作教练工作流可以做到：

- 每个阶段输出不同结构。
- 阶段之间可以传递结构化结果。
- 后续代码不用从自然语言里猜字段。
- 前端可以按字段稳定渲染内容。
