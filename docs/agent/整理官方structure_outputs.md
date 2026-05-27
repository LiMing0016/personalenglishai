---
title: 整理官方 Structured Outputs
status: draft
owner: ai
last_updated: 2026-05-22
review_cycle: monthly
related_code:
  - python/ai_orchestrator/
related_docs:
  - docs/agent/StructuredOutput学习笔记.md
  - docs/agent/FunctionCall学习笔记.md
---

# 整理官方 Structured Outputs

来源：OpenAI 官方文档 Structured Outputs 指南。

整理原则：

- 按官方网页目录和出现顺序整理。
- 每一节先翻译 / 概括官方含义，再补充代码注释和 PEAI 使用注意。
- 不把内容重新改造成另一套架构，方便对照官方网页学习。

## Structured model outputs

Structured Outputs 的目标是让模型输出符合你给定的 JSON Schema。

它解决的问题是：普通模型回答虽然自然，但结构不稳定；业务系统需要稳定字段，例如 `intent`、`score`、`feedback`、`items`、`tool_args`。

官方强调的核心点：

- 可以通过 JSON Schema 约束模型输出。
- 可以让模型稳定返回结构化数据。
- 适合和程序、数据库、前端 UI、Agent 工作流对接。

PEAI 理解：

- 对用户看的自然语言，可以不强制 Structured Outputs。
- 对后端要解析、入库、路由、统计、追踪的内容，应该优先设计 Structured Outputs。
- Agent 的关键输出最好变成“接口契约”，而不是靠提示词约定。

## Getting a structured response

官方首先展示的是“如何拿到结构化响应”。

在 Python 里，推荐用 Pydantic 定义结构，然后调用 `client.responses.parse()`。

```python
from openai import OpenAI
from pydantic import BaseModel

client = OpenAI()


class CalendarEvent(BaseModel):
    # 事件名称
    name: str

    # 事件日期
    date: str

    # 参与人列表
    participants: list[str]


response = client.responses.parse(
    model="gpt-4o-2024-08-06",
    input=[
        {
            "role": "system",
            "content": "Extract the event information.",
        },
        {
            "role": "user",
            "content": "Alice and Bob are going to a science fair on Friday.",
        },
    ],
    # 关键：让模型输出符合 CalendarEvent 结构
    text_format=CalendarEvent,
)

# output_parsed 是 SDK 已经解析好的 Pydantic 对象
event = response.output_parsed

# Jupyter 里建议显式打印，否则只赋值可能看不到结果
print(event.model_dump_json(indent=2))
```

这一节要记住：

- `BaseModel` 是结构定义。
- `text_format=CalendarEvent` 是把结构交给 Responses API。
- `response.output_parsed` 是解析后的对象。

## Supported models

Structured Outputs 不是所有模型都支持。使用前要确认当前模型支持这一能力。

官方文档会列出支持模型。实际开发时不要死记模型清单，因为模型可用性会变化。应该以官方文档和平台返回为准。

PEAI 建议：

- 项目配置里把结构化输出使用的模型单独配置。
- 如果切换模型，要跑结构化输出回归测试。
- 不要只测“能回答”，要测字段是否完整、类型是否正确、enum 是否稳定。

## Function calling vs response_format

官方区分两类场景：

- Function calling / tools：模型要调用工具，输出的是工具参数。
- `response_format` / `text.format`：模型最终回复本身要是结构化数据。

简单判断：

| 场景 | 推荐 |
|---|---|
| 模型需要调用后端函数、数据库、搜索、MCP 工具 | Function calling / tools |
| 模型最终要返回结构化 JSON 给你的程序 | `response_format` 或 `text.format` |
| Agent 路由后要调用某个工具 | tools |
| Router Agent 只返回路由决策 | Structured Outputs |
| 评分 Agent 返回评分项和建议 | Structured Outputs |

PEAI 例子：

- 用户说“查一下今天的学习计划”：如果要查数据库，用工具调用。
- Router 判断用户意图是 `grammar` 还是 `vocab`：用 Structured Outputs。
- 作文评分返回 `score`、`rubric_items`、`suggestions`：用 Structured Outputs。

## Structured Outputs vs JSON mode

官方把 Structured Outputs 和 JSON mode 区分开。

JSON mode 的作用：

- 保证输出是合法 JSON。
- 但不保证字段一定存在。
- 不保证字段类型、enum、嵌套结构完全符合业务要求。

Structured Outputs 的作用：

- 按 JSON Schema 约束输出。
- 字段、类型、required、enum、数组对象结构更稳定。
- 更适合后端解析、入库、前端展示和自动测试。

一句话：

```text
JSON mode 保证“像 JSON”。
Structured Outputs 保证“像你定义的接口”。
```

PEAI 建议：

- 临时脚本、一次性演示，可以用 JSON mode。
- 正式 Agent 输出、日志结构、评分结果、路由结果，优先 Structured Outputs。

## Examples

官方接下来给出多个示例，展示 Structured Outputs 可以解决哪些类型的问题。

## Example: Chain of thought

这个示例是让模型按步骤输出推理过程，典型结构包括：

- `steps`：数组，每一步有解释和输出。
- `final_answer`：最终答案。

```python
from pydantic import BaseModel
from openai import OpenAI

client = OpenAI()


class Step(BaseModel):
    # 当前步骤的解释
    explanation: str

    # 当前步骤的结果
    output: str


class MathReasoning(BaseModel):
    # 解题步骤列表
    steps: list[Step]

    # 最终答案
    final_answer: str


response = client.responses.parse(
    model="gpt-4o-2024-08-06",
    input=[
        {
            "role": "system",
            "content": "You are a helpful math tutor. Guide the user through the solution step by step.",
        },
        {
            "role": "user",
            "content": "how can I solve 8x + 7 = -23",
        },
    ],
    text_format=MathReasoning,
)

math_reasoning = response.output_parsed
print(math_reasoning.model_dump_json(indent=2))
```

PEAI 用法：

- 作文讲解可以返回 `steps`。
- 语法分析可以返回 `analysis_steps`。
- 但不要把内部隐藏推理强行暴露成详细思维链；面向用户时输出可解释步骤即可。

## Example: Structured data extraction

这个示例是从非结构化文本中提取结构化字段，例如从论文里提取标题、作者、摘要、关键词。

```python
from openai import OpenAI
from pydantic import BaseModel

client = OpenAI()


class ResearchPaperExtraction(BaseModel):
    # 论文标题
    title: str

    # 作者列表
    authors: list[str]

    # 摘要
    abstract: str

    # 关键词列表
    keywords: list[str]


response = client.responses.parse(
    model="gpt-4o-2024-08-06",
    input=[
        {
            "role": "system",
            "content": (
                "You are an expert at structured data extraction. "
                "You will be given unstructured text from a research paper "
                "and should convert it into the given structure."
            ),
        },
        {
            "role": "user",
            "content": "这里放论文原文",
        },
    ],
    text_format=ResearchPaperExtraction,
)

research_paper = response.output_parsed
print(research_paper.model_dump_json(indent=2))
```

PEAI 用法：

- 从用户输入中抽取考试类型、目标分数、学习阶段。
- 从作文中抽取主题、立场、论点、错误类型。
- 从聊天记录中抽取用户偏好和学习画像。

## Example: UI Generation

这个示例展示如何用递归结构生成 UI。重点不是直接让模型写 HTML，而是让模型返回一个受控 UI 树。

```python
from enum import Enum
from typing import List

from openai import OpenAI
from pydantic import BaseModel

client = OpenAI()


class UIType(str, Enum):
    # 限定模型只能返回这些 UI 节点类型
    div = "div"
    button = "button"
    header = "header"
    section = "section"
    field = "field"
    form = "form"


class Attribute(BaseModel):
    # 属性名，例如 class、id、placeholder
    name: str

    # 属性值
    value: str


class UI(BaseModel):
    # 当前节点类型
    type: UIType

    # 当前节点展示文案
    label: str

    # 子节点；这里是递归结构
    children: List["UI"]

    # 当前节点属性
    attributes: List[Attribute]


# 递归类型必须调用 model_rebuild
UI.model_rebuild()


class Response(BaseModel):
    # 最终输出 UI 树
    ui: UI


response = client.responses.parse(
    model="gpt-4o-2024-08-06",
    input=[
        {
            "role": "system",
            "content": "You are a UI generator AI. Convert the user input into a UI.",
        },
        {
            "role": "user",
            "content": "Make a User Profile Form",
        },
    ],
    text_format=Response,
)

ui = response.output_parsed
print(ui.model_dump_json(indent=2))
```

PEAI 注意：

- 这种方式适合生成低风险配置，不适合直接把模型生成的 HTML 无审查地渲染到生产页面。
- 如果要做动态练习卡片，可以让模型输出题目结构，而不是输出完整页面代码。

## Example: Moderation

这个示例是把用户输入分类到多个审核类别中。

```python
from enum import Enum
from typing import Optional

from openai import OpenAI
from pydantic import BaseModel

client = OpenAI()


class Category(str, Enum):
    # 暴力
    violence = "violence"

    # 性内容
    sexual = "sexual"

    # 自伤
    self_harm = "self_harm"


class ContentCompliance(BaseModel):
    # 是否违规
    is_violating: bool

    # 违规类别；不违规时可以是 None
    category: Optional[Category]

    # 违规解释；不违规时可以是 None
    explanation_if_violating: Optional[str]


response = client.responses.parse(
    model="gpt-4o-2024-08-06",
    input=[
        {
            "role": "system",
            "content": "Determine if the user input violates specific guidelines and explain if they do.",
        },
        {
            "role": "user",
            "content": "How do I prepare for a job interview?",
        },
    ],
    text_format=ContentCompliance,
)

compliance = response.output_parsed
print(compliance.model_dump_json(indent=2))
```

PEAI 用法：

- 可用于学习内容安全检查。
- 可用于判断用户输入是否偏离学习任务。
- 注意不要只依赖模型分类，关键场景仍需要后端规则、审计日志和人工复核。

## How to use Structured Outputs with response_format

官方这一部分讲 `response_format` 的使用方式，常见于 Chat Completions。

### Step 1: Define your schema

第一步是定义 schema。可以用 Pydantic / Zod，也可以直接写 JSON Schema。

Schema 设计建议：

- 字段名要清楚直观。
- 重要字段要写 `description`。
- 用 evals 验证结构是否适合你的真实任务。

PEAI 注意：

- 不要一上来就写复杂 JSON Schema。
- 先写清楚“后端/前端要消费哪些字段”。
- 再把字段转成 Pydantic、Zod 或 JSON Schema。

### Step 2: Supply your schema in the API call

第二步是在 API 调用中传入 schema。

如果用 Chat Completions，可以用 `client.chat.completions.parse()` 和 `response_format`。

```python
from pydantic import BaseModel


class Step(BaseModel):
    # 单步解释
    explanation: str

    # 单步输出
    output: str


class MathReasoning(BaseModel):
    # 解题步骤
    steps: list[Step]

    # 最终答案
    final_answer: str


completion = client.chat.completions.parse(
    model="gpt-4o-2024-08-06",
    messages=[
        {
            "role": "system",
            "content": "You are a helpful math tutor. Guide the user through the solution step by step.",
        },
        {
            "role": "user",
            "content": "how can I solve 8x + 7 = -23",
        },
    ],
    # 关键：要求响应符合 MathReasoning
    response_format=MathReasoning,
)

message = completion.choices[0].message
```

### Step 3: Handle edge cases

第三步是处理边界情况。

模型可能没有返回符合 schema 的正常结果，原因包括：

- 安全拒答。
- 达到最大输出 token。
- 内容被过滤。
- 响应不完整。
- 网络或 API 错误。

```python
try:
    completion = client.chat.completions.parse(
        model="gpt-4o-2024-08-06",
        messages=[
            {
                "role": "system",
                "content": "You are a helpful math tutor. Guide the user through the solution step by step.",
            },
            {
                "role": "user",
                "content": "how can I solve 8x + 7 = -23",
            },
        ],
        response_format=MathReasoning,
    )
except Exception as e:
    # 处理 API 错误、解析失败、输出不完整等情况
    print(f"请求失败: {e}")
```

## Refusals with Structured Outputs

如果模型因为安全原因拒答，拒答内容不一定符合你提供的 schema。

因此，API 响应中可能出现 `refusal` 字段。业务代码要先检查拒答，再读取结构化结果。

```python
message = completion.choices[0].message

if message.refusal:
    # 模型拒答时，展示或记录拒答内容
    print(message.refusal)
else:
    # 没有拒答时，读取结构化结果
    print(message.parsed)
```

PEAI 注意：

- UI 上不要把拒答当作解析失败。
- 日志里应该区分：拒答、解析错误、业务校验失败。
- Agent trace 里也应该记录是否发生 refusal。

## How to use Structured Outputs with text.format

官方接下来讲 Responses API 的 `text.format` 写法。

### Step 1: Define your schema

仍然是先定义 JSON Schema。这里官方提醒：Structured Outputs 支持 JSON Schema 的子集，不是所有 JSON Schema 关键字都能用。

设计建议：

- key 命名清楚。
- 重要 key 写 title / description。
- 用 evals 找到最适合自己业务的结构。

### Step 2: Supply your schema in the API call

在 Responses API 中，通过 `text.format` 传入 schema。

```python
from openai import OpenAI

client = OpenAI()

response = client.responses.create(
    model="gpt-4o-2024-08-06",
    input=[
        {
            "role": "system",
            "content": "You are a helpful math tutor. Guide the user through the solution step by step.",
        },
        {
            "role": "user",
            "content": "how can I solve 8x + 7 = -23",
        },
    ],
    text={
        "format": {
            # 使用 JSON Schema
            "type": "json_schema",

            # schema 名称
            "name": "math_response",

            # 严格遵守 schema
            "strict": True,

            # schema 正文
            "schema": {
                "type": "object",
                "properties": {
                    "steps": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "explanation": {"type": "string"},
                                "output": {"type": "string"},
                            },
                            "required": ["explanation", "output"],
                            "additionalProperties": False,
                        },
                    },
                    "final_answer": {"type": "string"},
                },
                "required": ["steps", "final_answer"],
                "additionalProperties": False,
            },
        }
    },
)

print(response.output_text)
```

官方提示：

- 第一次使用某个 schema 时，API 处理 schema 可能增加延迟。
- 后续相同 schema 通常不会重复产生这部分额外延迟。

### Step 3: Handle edge cases

Responses API 同样要处理拒答、输出不完整、内容过滤、最大 token 等情况。

```python
try:
    response = client.responses.create(
        model="gpt-4o-2024-08-06",
        input=[
            {
                "role": "system",
                "content": "You are a helpful math tutor. Guide the user through the solution step by step.",
            },
            {
                "role": "user",
                "content": "how can I solve 8x + 7 = -23",
            },
        ],
        text={
            "format": {
                "type": "json_schema",
                "name": "math_response",
                "strict": True,
                "schema": {
                    "type": "object",
                    "properties": {
                        "steps": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "explanation": {"type": "string"},
                                    "output": {"type": "string"},
                                },
                                "required": ["explanation", "output"],
                                "additionalProperties": False,
                            },
                        },
                        "final_answer": {"type": "string"},
                    },
                    "required": ["steps", "final_answer"],
                    "additionalProperties": False,
                },
            },
        },
    )
except Exception as e:
    # 处理 finish_reason、refusal、content_filter、max tokens 等异常
    print(f"请求失败: {e}")
```

## Tips and best practices

### Handling user-generated input

如果应用使用用户输入，prompt 里要说明“输入无法产生有效结构时怎么办”。

原因：模型会尽量遵守 schema。即使用户输入和任务无关，模型也可能为了填字段而编造内容。

建议：

- 输入不适用时返回空数组。
- 或返回 `null`。
- 或返回固定说明。
- 或返回 `is_applicable: false`。

PEAI Router 示例：

```json
{
  "intent": "unknown",
  "target_agent": null,
  "confidence": 0.0,
  "reason": "用户输入和当前学习任务无关"
}
```

### Handling mistakes

Structured Outputs 仍然可能有错误。

如果发现结构化输出不稳定，官方建议：

- 调整 instructions。
- 在 system instructions 中提供示例。
- 把复杂任务拆成更简单的子任务。
- 参考 prompt engineering guide 优化提示。

PEAI 注意：

- 不要只靠一次 prompt 调整。
- 要准备真实输入样本做回归测试。
- 结构错了优先检查 schema 是否过大、字段是否含糊、任务是否太复杂。

### Avoid JSON schema divergence

官方建议避免 JSON Schema 和编程语言里的类型定义分叉。

问题例子：

- JSON Schema 里有字段，Java DTO 没有。
- TypeScript 类型改了，schema 忘了改。
- schema 允许 null，后端字段不允许 null。
- enum 两边不一致。

推荐：

- Python 优先用 Pydantic。
- TypeScript 优先用 Zod。
- 如果直接写 JSON Schema，可以加 CI 检查。
- 或者从类型定义自动生成 JSON Schema。

### Streaming

Streaming 可以在模型生成过程中逐步处理响应，不用等完整响应结束。

常见用途：

- 边生成边显示字段。
- 边接收边处理 function call 参数。
- 长输出时改善用户体验。

```python
from typing import List

from openai import OpenAI
from pydantic import BaseModel


class EntitiesModel(BaseModel):
    # 属性词
    attributes: List[str]

    # 颜色词
    colors: List[str]

    # 动物词
    animals: List[str]


client = OpenAI()

with client.responses.stream(
    model="gpt-4.1",
    input=[
        {
            "role": "system",
            "content": "Extract entities from the input text",
        },
        {
            "role": "user",
            "content": "The quick brown fox jumps over the lazy dog with piercing blue eyes",
        },
    ],
    text_format=EntitiesModel,
) as stream:
    for event in stream:
        # 拒答增量
        if event.type == "response.refusal.delta":
            print(event.delta, end="")

        # 普通输出文本增量
        elif event.type == "response.output_text.delta":
            print(event.delta, end="")

        # 错误事件
        elif event.type == "response.error":
            print(event.error, end="")

        # 完成事件
        elif event.type == "response.completed":
            print("Completed")

    # 最终完整响应
    final_response = stream.get_final_response()
    print(final_response)
```

注意：

- 这些 event 是 API / SDK 包装出来的流式事件。
- 它们不是模型自己在 JSON 里写出来的字段。
- 真正做最终业务处理时，通常仍然看 `final_response`。

## Supported schemas

官方说明：Structured Outputs 支持 JSON Schema 的一个子集。

### Supported types

支持类型：

- String
- Number
- Boolean
- Integer
- Object
- Array
- Enum
- anyOf

### Supported properties

支持的字符串属性：

- `pattern`：字符串必须匹配正则表达式。
- `format`：预定义格式。

支持的 `format`：

- `date-time`
- `time`
- `date`
- `duration`
- `email`
- `hostname`
- `ipv4`
- `ipv6`
- `uuid`

支持的数字属性：

- `multipleOf`
- `maximum`
- `exclusiveMaximum`
- `minimum`
- `exclusiveMinimum`

支持的数组属性：

- `minItems`
- `maxItems`

示例：

```json
{
  "name": "user_data",
  "strict": true,
  "schema": {
    "type": "object",
    "properties": {
      "name": {
        "type": "string",
        "description": "The name of the user"
      },
      "username": {
        "type": "string",
        "description": "The username of the user. Must start with @",
        "pattern": "^@[a-zA-Z0-9_]+$"
      },
      "email": {
        "type": "string",
        "description": "The email of the user",
        "format": "email"
      }
    },
    "additionalProperties": false,
    "required": ["name", "username", "email"]
  }
}
```

## Root objects must not be anyOf and must be an object

根节点必须是 object，不能是 `anyOf`。

Zod 的 `discriminatedUnion` 常见问题是会在顶层生成 `anyOf` / `oneOf`，这对 Structured Outputs 无效。

```ts
import { z } from "zod";
import { zodResponseFormat } from "openai/helpers/zod";

// 成功响应
const BaseResponseSchema = z.object({
  status: z.literal("success"),
  data: z.string(),
});

// 失败响应
const UnsuccessfulResponseSchema = z.object({
  status: z.literal("error"),
  message: z.string(),
});

// 问题：顶层会生成 anyOf / oneOf 类似结构
const finalSchema = z.discriminatedUnion("status", [
  BaseResponseSchema,
  UnsuccessfulResponseSchema,
]);

// 对 Structured Outputs 无效
const json = zodResponseFormat(finalSchema, "final_schema");
```

正确思路：

- 最外层固定 object。
- 把变化放到 object 的字段里。
- 或者用统一对象结构，加 `status`、`data`、`error`、`message` 等 nullable 字段。

## All fields must be required

Structured Outputs 要求所有字段或函数参数都必须写进 `required`。

```json
{
  "name": "get_weather",
  "description": "Fetches the weather in the given location",
  "strict": true,
  "parameters": {
    "type": "object",
    "properties": {
      "location": {
        "type": "string",
        "description": "The location to get the weather for"
      },
      "unit": {
        "type": "string",
        "description": "The unit to return the temperature in",
        "enum": ["F", "C"]
      }
    },
    "additionalProperties": false,
    "required": ["location", "unit"]
  }
}
```

含义：

- 不是“业务上必填”的字段才写 required。
- 在 Structured Outputs 里，字段都要出现在 `required` 中。
- 如果业务上可以为空，就用 `null`。

## Emulate optional parameters with null

虽然所有字段必须 required，但可以用 `null` 模拟可选字段。

```json
{
  "name": "get_weather",
  "description": "Fetches the weather in the given location",
  "strict": true,
  "parameters": {
    "type": "object",
    "properties": {
      "location": {
        "type": "string",
        "description": "The location to get the weather for"
      },
      "unit": {
        "type": ["string", "null"],
        "description": "The unit to return the temperature in",
        "enum": ["F", "C"]
      }
    },
    "additionalProperties": false,
    "required": ["location", "unit"]
  }
}
```

记忆：

```text
required 表示字段必须出现。
null 表示字段可以没有业务值。
```

## Objects have limitations on nesting depth and size

对象有深度和大小限制：

- 一个 schema 最多可以有 5000 个 object properties。
- 最多 10 层嵌套。

PEAI 建议：

- 路由、评分、讲解、练习生成分开 schema。
- 超过 5 到 6 层时，优先考虑拆任务。
- 不要把完整数据库对象塞进模型输出。

## Limitations on total string size

schema 中以下字符串总长度不能超过 120000 字符：

- property names
- definition names
- enum values
- const values

PEAI 建议：

- 字段名短而清楚。
- 长说明写进 `description`。
- 大词表不要放 enum。

## Limitations on enum size

enum 限制：

- 所有 enum 属性合计最多 1000 个 enum values。
- 如果单个字符串 enum 超过 250 个值，这些 enum values 的字符串总长度不能超过 15000 字符。

适合 enum：

- `route_type`
- `target_agent`
- `status`
- `severity`
- `difficulty`

不适合 enum：

- 全部单词。
- 全部作文题目。
- 全部知识点路径。
- 全部用户标签。

## additionalProperties: false must always be set in objects

所有 object 都必须设置：

```json
"additionalProperties": false
```

作用：禁止模型返回 schema 之外的额外字段。

```json
{
  "name": "get_weather",
  "description": "Fetches the weather in the given location",
  "strict": true,
  "schema": {
    "type": "object",
    "properties": {
      "location": {
        "type": "string",
        "description": "The location to get the weather for"
      },
      "unit": {
        "type": "string",
        "description": "The unit to return the temperature in",
        "enum": ["F", "C"]
      }
    },
    "additionalProperties": false,
    "required": ["location", "unit"]
  }
}
```

PEAI 理解：

- 这让模型输出像后端 DTO。
- 前端和后端不会收到意外字段。
- 日志和测试也更稳定。

## Key ordering

Structured Outputs 会按照 schema 中 key 的顺序输出字段。

这对阅读和日志有帮助。建议把重要字段放前面。

PEAI 示例：

```text
intent -> route_type -> target_agent -> confidence -> reason
```

比下面这种顺序更适合调试：

```text
reason -> confidence -> target_agent -> intent -> route_type
```

## Some type-specific keywords are not yet supported

Structured Outputs 只支持 JSON Schema 的一部分。有些类型专属关键字不支持。

实际做法：

- 不要默认把完整 JSON Schema 都搬进去。
- 如果某个关键字报错，就移到应用层校验。
- 对关键业务约束，后端仍然要二次校验。

PEAI 例子：

- 字符串长度限制可以后端校验。
- 复杂正则可以后端校验。
- 分数范围可以后端校验。

## For anyOf, the nested schemas must each be a valid JSON Schema per this subset

`anyOf` 可以在嵌套位置使用，但里面的每个 schema 也必须符合 Structured Outputs 支持的子集。

注意：

- 顶层不能是 `anyOf`。
- 嵌套 `anyOf` 可以用，但不要过度复杂。
- 每个分支里的 object 也要遵守 `required` 和 `additionalProperties: false`。

## Definitions are supported

Structured Outputs 支持 definitions / `$defs` 这类复用定义。

适合场景：

- 多个字段复用同一种对象结构。
- 数组 item 和其他字段共享结构。
- schema 较长时减少重复。

PEAI 建议：

- 小 schema 不必强行抽 `$defs`。
- 复用明显时再抽。
- 保持可读性比炫技更重要。

## Recursive schemas are supported

Structured Outputs 支持递归 schema。

典型例子：

- UI 树。
- 目录树。
- 评论树。
- 分类树。

但 PEAI 里要谨慎使用：

- 递归结构容易变深。
- 前端渲染要限制层级。
- 后端要防止过大响应。

## JSON mode

官方最后说明 JSON mode。

JSON mode 可以让模型返回合法 JSON，但不提供和 Structured Outputs 同等的 schema 约束。

使用 JSON mode 时要注意：

- prompt 里要明确要求 JSON。
- 应用层要解析和校验。
- 不能假设字段一定存在或类型一定正确。

适用场景：

- 临时脚本。
- 低风险数据整理。
- 你愿意在应用层做完整校验。

不适合场景：

- 正式 Agent 路由。
- 后端入库。
- 评分结果。
- 前端直接渲染。
- 自动化工具参数。

## PEAI 总结检查表

| 检查项 | 要求 |
|---|---|
| 根节点 | 必须是 object |
| 顶层 anyOf | 不允许 |
| required | 所有字段都要 required |
| optional | 用 `null` 表达 |
| object | 每个 object 都写 `additionalProperties: false` |
| enum | 只放稳定小集合 |
| 大列表 | 放数据库、配置或检索 |
| 嵌套 | 最多 10 层，实际尽量更浅 |
| 字段顺序 | 重要字段放前面 |
| description | 关键字段写清楚 |
| 拒答 | 检查 refusal |
| streaming | 用事件处理增量，用 final response 做最终业务处理 |
| eval | 用真实样本验证 schema |
