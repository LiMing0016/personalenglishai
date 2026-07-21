# Adaptive Learning Orchestration Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变普通 Markdown 问答体验的前提下，接通可持久化的 `content + parts` 消息链路，并用 XState v5 完成第一种“重组成句”互动练习的开始、作答、判分、反馈、下一题和退出闭环。

**Architecture:** 用户显式操作通过版本化 `interaction` 字段进入现有 assistant API；Python 编排层只在明确的 `start_practice + sentence_reorder` 请求上运行结构化练习生成 workflow，普通请求继续沿用现有路由与 Markdown 输出。Java 后端作为协议与持久化边界保存消息的 `parts_json`，前端通过注册表校验学习块；只读块直接渲染，互动块由一个通用 XState 活动状态机驱动，确定性判分在本地完成。

**Tech Stack:** Vue 3.4、TypeScript 5.5、Pinia、XState v5、`@xstate/vue`、Spring Boot 3.2 / Java 17 / MyBatis / MySQL、Python 3 / Pydantic / OpenAI Agents SDK 0.17.6、Node test、JUnit 5、pytest。

## Global Constraints

- 当前已经位于功能分支 `codex/word-card-design`，不再创建新分支。
- 工作区还有与本需求无关的暂存和未跟踪文件。每次提交只使用 `git commit --only -- <本任务文件>`，不得清空、覆盖或提交其他改动。
- 普通问答仍然只返回 Markdown；没有显式练习意图时，不自动进入状态机。
- 用户显式 `interaction.uiIntent` 的优先级高于自然语言路由，不再通过隐藏提示词表达按钮含义。
- Phase 1 只支持 `sentence_reorder` 互动块；既有 `vocab_card`、`grammar_tree`、`study_plan`、`sentence_analysis` 保持兼容。
- 只持久化消息的展示内容 `content + parts`；不持久化答案、尝试次数、分数或 XState 快照。
- 模型生成练习内容，代码负责块类型、版本、ID、打乱、状态转换和判分。模型不能返回 Vue 组件名、路由或 XState 状态。
- 结构化生成、块校验或历史解析失败时保留 Markdown，不能让卡片故障阻断对话。
- 新增 XState 的理由是活动存在互斥状态、非法事件、重复提交和错误恢复；它只运行在互动活动组件内，不替代 Pinia。
- 所有新增按钮必须支持键盘；结果必须有可见文本和 `aria-live`，不能只靠颜色表达。
- 每个任务先写失败测试，再实现最小代码，再运行目标测试，最后只提交本任务文件。

---

## Task 1: 建立前端版本化学习块注册表

**Files:**

- Create: `web/src/components/assistant/learning-blocks/contracts.ts`
- Create: `web/src/components/assistant/learning-blocks/registry.ts`
- Create: `web/src/components/assistant/learning-blocks/registry.test.ts`
- Create: `web/src/components/assistant/learning-blocks/vocab-card/schema.ts`
- Create: `web/src/components/assistant/learning-blocks/grammar-tree/schema.ts`
- Create: `web/src/components/assistant/learning-blocks/study-plan/schema.ts`
- Create: `web/src/components/assistant/learning-blocks/sentence-analysis/schema.ts`
- Modify: `web/src/types/assistantBlocks.ts`
- Modify: `web/src/components/assistant/AssistantBlockRenderer.vue`

### Contract

注册表只负责识别、校验、规范化和选择组件，不管理活动状态：

```ts
export interface AssistantLearningBlock<TData = unknown> {
  id: string
  type: string
  version: number
  title?: string
  fallbackMarkdown: string
  data: TData
  actions?: AssistantBlockAction[]
}

export interface LearningBlockDefinition<TBlock extends AssistantLearningBlock> {
  type: TBlock['type']
  version: TBlock['version']
  kind: 'read_only' | 'interactive'
  normalize(value: unknown): TBlock | null
  loadComponent: () => Promise<{ default: Component }>
}
```

注册表在 Node 测试中不能静态加载 `.vue` 文件，因此组件必须用 `loadComponent` 延迟导入，Renderer 再通过 Vue `defineAsyncComponent` 消费。未知类型或版本如果带有安全的 `fallbackMarkdown`，规范化为 `FallbackAssistantBlock`，而不是直接丢弃：

```ts
export interface FallbackAssistantBlock {
  id: string
  type: '__fallback__'
  version: 1
  originalType: string
  originalVersion: number
  fallbackMarkdown: string
}

export type RenderableAssistantBlock = AssistantBlock | FallbackAssistantBlock
```

保留现有动作兼容，同时增加结构化动作：

```ts
export type AssistantBlockAction =
  | { id: string; label: string; kind?: 'prompt'; prompt: string }
  | {
      id: string
      label: string
      kind: 'interaction'
      interaction: AssistantInteractionContext
      displayText?: string
    }
```

### Steps

- [ ] 在 `registry.test.ts` 写失败测试：四种旧块仍可解析；未知类型、未知版本返回 fallback block；空 ID、非法 data 且无 fallback 时被拒绝；动作中的旧 `prompt` 仍能保留。
- [ ] 运行 `node --test --experimental-strip-types src/components/assistant/learning-blocks/registry.test.ts`，确认因模块不存在或断言失败而红。
- [ ] 把现有四种 data 校验从 `assistantBlocks.ts` 拆到对应 `schema.ts`；不要在这一步改变字段或组件样式。
- [ ] 在 `registry.ts` 建立唯一注册表和 `normalizeAssistantBlocks(value)`；现有块缺少 fallback 时由各自 schema 根据 title/data 合成简短 fallback，保证旧 payload 兼容。
- [ ] 把 `AssistantBlockRenderer.vue` 的硬编码 `blockRenderers` 改为 `defineAsyncComponent(definition.loadComponent)`，fallback block 只显示经过 Markdown 管线渲染的 `fallbackMarkdown`。
- [ ] 让 `assistantBlocks.ts` 只重新导出公共类型与 `normalizeAssistantBlocks`，保持现有 import 路径兼容。
- [ ] 再运行注册表测试，以及现有 Markdown/学习助手契约测试。

**Verification:**

```powershell
cd F:\personalenglishai\web
node --test --experimental-strip-types src/components/assistant/learning-blocks/registry.test.ts
node --test --experimental-strip-types src/components/assistant/markdown.test.ts
node --experimental-strip-types tests/assistantMarkdown.test.ts
```

Expected: 所有测试通过；旧消息与旧块无需迁移。

**Commit:**

```powershell
git commit --only -m "refactor(ui): 建立学习块注册表" -- web/src/components/assistant/learning-blocks web/src/types/assistantBlocks.ts web/src/components/assistant/AssistantBlockRenderer.vue
```

---

## Task 2: 安装 XState 并实现通用互动活动状态机

**Files:**

- Modify: `web/package.json`
- Modify: `web/package-lock.json`
- Create: `web/src/components/assistant/learning-activities/contracts.ts`
- Create: `web/src/components/assistant/learning-activities/activityMachine.ts`
- Create: `web/src/components/assistant/learning-activities/activityMachine.test.ts`

### State contract

```ts
export type LearningActivityEvent =
  | { type: 'START'; block: InteractiveLearningBlock }
  | { type: 'ANSWER_CHANGE'; answer: unknown }
  | { type: 'REQUEST_HINT' }
  | { type: 'SUBMIT' }
  | { type: 'SUBMIT_SUCCESS'; result: ActivityResult }
  | { type: 'SUBMIT_ERROR'; error: ActivityError }
  | { type: 'NEXT' }
  | { type: 'RETRY' }
  | { type: 'EXIT' }

export interface LearningActivityContext {
  activityId: string
  block?: InteractiveLearningBlock
  questionIndex: number
  draftAnswer?: unknown
  result?: ActivityResult
  error?: ActivityError
}
```

状态图必须是：

```text
idle --START--> awaitingAnswer --SUBMIT--> submitting
submitting --SUBMIT_SUCCESS--> reviewing
submitting --SUBMIT_ERROR--> error
reviewing --NEXT(hasNext)--> awaitingAnswer
reviewing --NEXT(noNext)--> completed
reviewing --RETRY--> awaitingAnswer
error --RETRY--> awaitingAnswer
任何非终态 --EXIT--> cancelled
```

### Steps

- [ ] 运行 `npm install xstate @xstate/vue`，让 lockfile 记录实际兼容版本。
- [ ] 在 `activityMachine.test.ts` 用 `createActor` 写失败测试，覆盖主路径、最后一题完成、重试、失败、退出、重复提交和终态忽略答案。
- [ ] 运行目标测试，确认状态机文件尚不存在而红。
- [ ] 用 XState v5 `setup({ types, guards, actions }).createMachine(...)` 实现状态机；不得在机器内部请求 API 或写 localStorage。
- [ ] `SUBMIT` 只进入 `submitting`，判分结果必须通过显式事件返回，保证未来可以替换成远端判分。
- [ ] `NEXT` 清空上一题的草稿、结果和错误，并只在 `reviewing` 生效。
- [ ] 运行状态机测试和 TypeScript 构建。

**Verification:**

```powershell
cd F:\personalenglishai\web
node --test --experimental-strip-types src/components/assistant/learning-activities/activityMachine.test.ts
npm run build
```

Expected: 状态转换测试全部通过；构建不新增类型错误。

**Commit:**

```powershell
git commit --only -m "feat(ui): 增加学习活动状态机" -- web/package.json web/package-lock.json web/src/components/assistant/learning-activities
```

---

## Task 3: 实现重组成句数据契约、确定性判分与卡片

**Files:**

- Create: `web/src/components/assistant/learning-blocks/sentence-reorder/schema.ts`
- Create: `web/src/components/assistant/learning-blocks/sentence-reorder/grader.ts`
- Create: `web/src/components/assistant/learning-blocks/sentence-reorder/grader.test.ts`
- Create: `web/src/components/assistant/learning-blocks/sentence-reorder/SentenceReorderBlock.vue`
- Create: `web/tests/sentenceReorderAccessibility.test.ts`
- Modify: `web/src/components/assistant/learning-blocks/contracts.ts`
- Modify: `web/src/components/assistant/learning-blocks/registry.ts`
- Modify: `web/src/components/assistant/learning-blocks/registry.test.ts`

### Block data

```ts
export interface SentenceReorderToken {
  id: string
  text: string
}

export interface SentenceReorderItem {
  id: string
  instruction: string
  translation?: string
  tokens: SentenceReorderToken[]
  initialOrder: string[]
  acceptedOrders: string[][]
  explanation?: string
  hint?: string
}

export type SentenceReorderBlock = AssistantLearningBlock<{
  activityId: string
  items: SentenceReorderItem[]
}> & {
  type: 'sentence_reorder'
  version: 1
}
```

判分只比较 token ID 顺序，不通过自由文本或模型临时判断：

```ts
export function gradeSentenceReorder(
  answer: string[],
  acceptedOrders: string[][],
): ActivityResult {
  const correct = acceptedOrders.some((order) =>
    order.length === answer.length && order.every((id, index) => id === answer[index]),
  )
  return { correct, answer, expected: acceptedOrders[0] ?? [] }
}
```

### Steps

- [ ] 写 schema/判分失败测试：合法数据、重复 token ID、缺失 accepted order、非法 order 引用、正确/错误顺序都覆盖。
- [ ] 运行测试确认红。
- [ ] 实现 schema 与纯函数判分器，并注册 `sentence_reorder@1` 为 `interactive`。
- [ ] 卡片使用 `useMachine(activityMachine)`；挂载后发送 `START`，点击 token 可在题库/答案区移动，键盘 Enter/Space 等价于点击。
- [ ] 提交时先发 `SUBMIT`，同步调用纯函数后发 `SUBMIT_SUCCESS`；任何异常发 `SUBMIT_ERROR`。
- [ ] 反馈区显示“回答正确/再想一想”、正确句子、翻译和解释，并使用 `role="status" aria-live="polite"`。
- [ ] 只有 `reviewing` 显示“下一题/重试”，任意非终态显示“结束练习”；提交中禁用重复提交。
- [ ] 在 390px 下 token 自动换行，按钮触摸目标不小于 40px；不能依赖拖拽。
- [ ] 用源码契约测试检查 button、键盘处理、`aria-live` 和退出事件存在。

**Verification:**

```powershell
cd F:\personalenglishai\web
node --test --experimental-strip-types src/components/assistant/learning-blocks/sentence-reorder/grader.test.ts
node --test --experimental-strip-types src/components/assistant/learning-blocks/registry.test.ts
node --experimental-strip-types tests/sentenceReorderAccessibility.test.ts
npm run build
```

Expected: 判分不调用网络；键盘与触屏路径均有可用控件；状态机测试继续通过。

**Commit:**

```powershell
git commit --only -m "feat(ui): 实现重组成句互动卡片" -- web/src/components/assistant/learning-blocks web/src/components/assistant/learning-activities web/tests/sentenceReorderAccessibility.test.ts
```

---

## Task 4: 增加结构化 UI 意图和显式练习入口

**Files:**

- Modify: `web/src/types/assistantRequest.ts`
- Modify: `web/src/api/assistant.ts`
- Modify: `web/src/pages/app/assistantMock.ts`
- Modify: `web/src/pages/app/assistantState.ts`
- Modify: `web/src/pages/app/AssistantPage.vue`
- Modify: `web/src/components/assistant/AssistantStarterCards.vue`
- Modify: `web/src/components/assistant/AssistantChatView.vue`
- Modify: `web/src/components/assistant/AssistantBlockRenderer.vue`
- Create: `web/src/pages/app/assistantInteraction.test.ts`
- Modify: `web/src/api/assistantStream.test.ts`

### Request contract

```ts
export type AssistantInteractionSource =
  | 'composer'
  | 'quick_action'
  | 'response_action'
  | 'activity_action'

export interface AssistantInteractionContext {
  source: AssistantInteractionSource
  uiIntent?: 'start_practice' | 'show_learning_card' | 'activity_action'
  activeActivityId?: string
  actionId?: string
  context?: {
    exerciseType?: 'sentence_reorder'
    topic?: string
    difficulty?: 'easy' | 'medium' | 'hard'
  }
}
```

`sendPrompt` 改为兼容的 options 形式：

```ts
async function sendPrompt(
  prompt: string,
  attachments: AssistantAttachment[] = composerAttachments.value,
  interaction?: AssistantInteractionContext,
) { /* existing flow */ }
```

### Steps

- [ ] 写失败测试：`toAssistantAgentRequest` 保留 `interaction`；普通输入不带 `uiIntent`；“练一题”按钮发送 `start_practice/sentence_reorder` 而不是只填充 composer。
- [ ] 让 `AssistantStarterCards` 的 `choose` 事件发送区分 `prompt` 与 `interaction` 的联合类型。
- [ ] 在“设计练习”目标下增加“重组成句练习”显式入口，显示文本为“开始重组成句练习”，请求上下文固定为 `{ source:'quick_action', uiIntent:'start_practice', context:{ exerciseType:'sentence_reorder' } }`。
- [ ] `AssistantBlockRenderer` 对旧 prompt action 继续 emit 字符串；对 interaction action emit 结构化对象。
- [ ] `AssistantPage.vue` 根据动作种类选择 `applyStarter` 或直接 `sendPrompt(displayText, [], interaction)`；不要把 `uiIntent` 编码进用户可见 prompt。
- [ ] `assistantState.ts` 把 interaction 透传到 `buildReply`，重试时连同 interaction 一起保存，避免失败重试退化成普通问答。
- [ ] `assistantApi.toAssistantAgentRequest` 增加 interaction；流式 `message.completed.parts` 的读取保持向后兼容。
- [ ] 测试普通问答 payload、显式练习 payload 和旧 action 兼容。

**Verification:**

```powershell
cd F:\personalenglishai\web
node --test --experimental-strip-types src/pages/app/assistantInteraction.test.ts
node --test --experimental-strip-types src/api/assistantStream.test.ts
node --experimental-strip-types tests/assistantStartExperience.test.ts
npm run build
```

Expected: 普通输入行为不变；显式按钮请求可以从 JSON 中直接识别，不依赖提示词猜测。

**Commit:**

```powershell
git commit --only -m "feat(ui): 增加显式学习交互意图" -- web/src/types/assistantRequest.ts web/src/api/assistant.ts web/src/api/assistantStream.test.ts web/src/pages/app/assistantMock.ts web/src/pages/app/assistantState.ts web/src/pages/app/assistantInteraction.test.ts web/src/pages/app/AssistantPage.vue web/src/components/assistant/AssistantStarterCards.vue web/src/components/assistant/AssistantChatView.vue web/src/components/assistant/AssistantBlockRenderer.vue
```

---

## Task 5: 在 Python 编排层实现结构化重组成句 workflow

**Files:**

- Create: `python/ai_orchestrator/schemas/learning_blocks.py`
- Modify: `python/ai_orchestrator/schemas/assistant_request.py`
- Modify: `python/ai_orchestrator/schemas/chat.py`
- Modify: `python/ai_orchestrator/app.py`
- Create: `python/ai_orchestrator/agents/sentence_reorder.py`
- Create: `python/ai_orchestrator/workflows/generate_sentence_reorder.py`
- Create: `python/ai_orchestrator/prompts/agent_instructions/sentence_reorder.md`
- Modify: `python/ai_orchestrator/prompts/agents.py`
- Modify: `python/ai_orchestrator/assistant_service.py`
- Create: `python/ai_orchestrator/tests/test_learning_block_contracts.py`
- Create: `python/ai_orchestrator/tests/test_generate_sentence_reorder_workflow.py`
- Modify: `python/ai_orchestrator/tests/test_assistant_service.py`
- Modify: `python/ai_orchestrator/tests/test_assistant_stream_events.py`
- Modify: `python/ai_orchestrator/tests/test_chat_contracts.py`
- Modify: `python/ai_orchestrator/tests/test_assistant_run_endpoint.py`

### Python schemas

```python
class AssistantInteractionContext(BaseModel):
    source: Literal["composer", "quick_action", "response_action", "activity_action"]
    ui_intent: Literal["start_practice", "show_learning_card", "activity_action"] | None = Field(
        default=None, alias="uiIntent"
    )
    active_activity_id: str | None = Field(default=None, alias="activeActivityId")
    action_id: str | None = Field(default=None, alias="actionId")
    context: AssistantInteractionPayload = Field(default_factory=AssistantInteractionPayload)

class SentenceReorderGeneration(BaseModel):
    intro: str
    questions: list[SentenceReorderGeneratedQuestion] = Field(min_length=1, max_length=3)

class AssistantReply:
    reply: str
    agent_name: str | None
    run: AssistantRunMetadata | None = None
    parts: list[AssistantLearningBlock] = field(default_factory=list)
```

模型只生成正确顺序内容：

```python
class SentenceReorderGeneratedQuestion(BaseModel):
    instruction: str
    chunks: list[str] = Field(min_length=2, max_length=12)
    translation: str | None = None
    explanation: str | None = None
    hint: str | None = None
```

workflow 负责把 chunks 转成 token ID、唯一顺序和可复现的打乱顺序。测试注入固定随机源；生产用 `secrets.SystemRandom`，如果打乱后仍与正确顺序相同则旋转一次。

### Steps

- [ ] 写 schema 失败测试：camelCase interaction 可解析；未知 exercise type 拒绝；块输出带 `type/version/fallbackMarkdown`；问题不足两个 chunk 时拒绝。
- [ ] 写 workflow 失败测试，使用 fake runner 返回 Pydantic 输出，断言模型只提供 chunks，而 workflow 生成 ID、打乱和 acceptedOrders。
- [ ] 在 prompt 注册表增加 `sentence_reorder`，并将其列入 structured-output-only 集合，避免拼入 Markdown 输出策略。
- [ ] 创建单一职责的 Sentence Reorder capability agent，使用 Agents SDK `Agent(..., output_type=SentenceReorderGeneration)` 和 `resolve_agent_prompt_kwargs('sentence_reorder', dynamic=True)`。
- [ ] workflow 使用现有 Agents SDK 和 `RunConfig`，返回 `SentenceReorderWorkflowResult(content, parts, usage, run_items)`；不创建新 runtime，不写数据库。
- [ ] prompt 明确：按学段控制句长和词汇，只生成 1–3 题；chunks 拼接必须形成自然正确句子；不得输出组件名、HTML、路由或答案状态。
- [ ] 在 `AssistantService` 增加纯判断函数，只在 `ui_intent == 'start_practice'` 且 `exercise_type == 'sentence_reorder'` 时走 workflow；其他请求完全沿用当前 route decision 和 specialist 路径。
- [ ] 非流式返回 `reply + parts`；流式路径可以不流 JSON，先发 `run.started/message.created`，workflow 完成后直接发一个带 `parts` 的 `message.completed`，再发 `run.completed`。
- [ ] `app.py` 的 `AssistantRunResponse` 构造必须显式传递 `parts=result.parts`；端点测试同时覆盖非流式 JSON 和 SSE 的 parts，防止 service 正确但入口层丢字段。
- [ ] workflow 结构化输出失败时抛出可诊断业务错误，由现有 `run.failed` 路径处理；不得伪造题目。
- [ ] 普通请求测试必须断言 workflow 没有被调用。

**Verification:**

```powershell
cd F:\personalenglishai\python
.\.venv\Scripts\python.exe -m pytest ai_orchestrator/tests/test_learning_block_contracts.py ai_orchestrator/tests/test_generate_sentence_reorder_workflow.py ai_orchestrator/tests/test_assistant_service.py ai_orchestrator/tests/test_assistant_stream_events.py ai_orchestrator/tests/test_chat_contracts.py ai_orchestrator/tests/test_assistant_run_endpoint.py -q
```

Expected: 显式练习得到严格结构化块；普通 assistant 路由回归通过；无需真实模型密钥。

**Commit:**

```powershell
git commit --only -m "feat(agent): 生成结构化重组成句练习" -- python/ai_orchestrator/schemas/learning_blocks.py python/ai_orchestrator/schemas/assistant_request.py python/ai_orchestrator/schemas/chat.py python/ai_orchestrator/app.py python/ai_orchestrator/agents/sentence_reorder.py python/ai_orchestrator/workflows/generate_sentence_reorder.py python/ai_orchestrator/prompts/agent_instructions/sentence_reorder.md python/ai_orchestrator/prompts/agents.py python/ai_orchestrator/assistant_service.py python/ai_orchestrator/tests/test_learning_block_contracts.py python/ai_orchestrator/tests/test_generate_sentence_reorder_workflow.py python/ai_orchestrator/tests/test_assistant_service.py python/ai_orchestrator/tests/test_assistant_stream_events.py python/ai_orchestrator/tests/test_chat_contracts.py python/ai_orchestrator/tests/test_assistant_run_endpoint.py
```

---

## Task 6: 让 Java 后端透传并持久化 `parts`

**Files:**

- Create: `backend/src/main/resources/db/migration_add_assistant_message_parts.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Modify: `backend/src/main/resources/db/migration_add_assistant_conversation.sql`
- Modify: `backend/src/main/resources/admin-data-catalog.yml`
- Modify: `backend/src/main/resources/mapper/AssistantMessageMapper.xml`
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/assistant/AssistantMessage.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantMessageResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantStreamEventResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/assistant/PythonAssistantClient.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/AssistantControllerTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/assistant/AssistantConversationServiceTest.java`

### Persistence contract

```sql
ALTER TABLE assistant_message
    ADD COLUMN parts_json JSON NULL COMMENT 'versioned assistant learning blocks' AFTER content;
```

Java 内部使用 Jackson `JsonNode`，避免后端复制前端所有卡片字段，但必须保证根节点为数组：

```java
private JsonNode normalizeParts(JsonNode parts) {
    return parts != null && parts.isArray() && !parts.isEmpty() ? parts : null;
}
```

### Steps

- [ ] 在 service 测试中先写失败场景：非流式 Python reply 的 parts 被保存并出现在会话响应；流式 `message.completed.parts` 被捕获、原样 SSE 输出并保存；普通文本保存 null。
- [ ] 在 controller 测试中写 interaction JSON 反序列化断言和带 parts 的 SSE 响应断言。
- [ ] `AssistantRequest` 增加嵌套 `Interaction` 和 `InteractionContext`，字段长度与枚举值由现有 validator 或 DTO 校验限制；未知字段仍保持 Jackson 默认兼容行为。
- [ ] `PythonAssistantReply` 增加 `JsonNode parts`；`AssistantStreamEventResponse` 和 `AssistantMessageResponse` 增加 `JsonNode parts`。
- [ ] `AssistantMessage` 增加 `String partsJson`；MyBatis select/insert 同步映射。数据库列使用 JSON，Java 保存前用 ObjectMapper 序列化规范化数组。
- [ ] 非流式 `sendAgentMessage` 把 reply parts 保存到 assistant message。
- [ ] 流式捕获函数在 `message.completed` 时同时提取 `content` 与 `parts`；只在未失败时保存。
- [ ] `toMessageResponse` 解析 `partsJson`；旧行 null、空串或历史坏数据返回 null 并记录 warn，不能让整个会话 500。
- [ ] 更新建库脚本和增量迁移脚本；不新增 attempt/score/state 表。
- [ ] 更新 `admin-data-catalog.yml` 的 assistant_message 字段说明，避免运维数据目录与真实 schema 分叉。
- [ ] 运行目标 JUnit 测试。

**Verification:**

```powershell
cd F:\personalenglishai\backend
.\mvnw.cmd -Dtest=AssistantConversationServiceTest,AssistantControllerTest test
```

Expected: 旧消息仍只有 content；新卡片消息在刷新/重拉会话后仍有 parts；SSE 字段不丢失。

**Commit:**

```powershell
git commit --only -m "feat(api): 持久化学习助手结构化内容" -- backend/src/main/resources/db/migration_add_assistant_message_parts.sql backend/src/main/resources/db/schema.sql backend/src/main/resources/db/migration_add_assistant_conversation.sql backend/src/main/resources/admin-data-catalog.yml backend/src/main/resources/mapper/AssistantMessageMapper.xml backend/src/main/java/com/personalenglishai/backend/entity/assistant/AssistantMessage.java backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantRequest.java backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantMessageResponse.java backend/src/main/java/com/personalenglishai/backend/controller/dto/assistant/AssistantStreamEventResponse.java backend/src/main/java/com/personalenglishai/backend/service/assistant/PythonAssistantClient.java backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java backend/src/test/java/com/personalenglishai/backend/controller/AssistantControllerTest.java backend/src/test/java/com/personalenglishai/backend/service/assistant/AssistantConversationServiceTest.java
```

---

## Task 7: 接通真实历史消息、卡片动作和降级行为

**Files:**

- Modify: `web/src/pages/app/assistantState.ts`
- Modify: `web/src/pages/app/assistantMock.ts`
- Modify: `web/src/components/assistant/AssistantBlockRenderer.vue`
- Modify: `web/src/components/assistant/AssistantChatView.vue`
- Create: `web/tests/assistantLearningBlocksIntegration.test.ts`
- Modify: `web/src/api/assistantStream.test.ts`

### Steps

- [ ] 写失败测试：流式完成后有卡片；随后 `getConversation` 返回同一 parts 时不消失；旧历史无 parts 时只显示 Markdown；坏块不会删除 content。
- [ ] `fromRemoteConversation` 只通过注册表规范化 parts，不信任后端 JSON；非法块记录开发诊断并返回空 parts。
- [ ] Renderer 对无效/未知块显示其 `fallbackMarkdown`。如果连 fallback 都无效，则跳过块但保留消息 Markdown。
- [ ] 互动卡片自身处理活动内 `SUBMIT/NEXT/RETRY/EXIT`，不要把这些事件重新当成 assistant prompt。
- [ ] 只有需要新模型内容的 response action 才发送 `interaction`；本地判分与题间移动不请求后端。
- [ ] 用户在主 composer 提问时不销毁已挂载活动；切换或刷新会话允许活动回到初始题目，符合 Phase 1 不持久化状态的约束。
- [ ] 加入开发环境诊断：未知 type/version、非法 data、parts JSON 非数组；生产界面不展示堆栈。

**Verification:**

```powershell
cd F:\personalenglishai\web
node --experimental-strip-types tests/assistantLearningBlocksIntegration.test.ts
node --test --experimental-strip-types src/api/assistantStream.test.ts
node --test --experimental-strip-types src/components/assistant/learning-blocks/registry.test.ts
npm run build
```

Expected: 卡片在远端会话替换后仍存在；活动内事件不产生新的聊天消息；降级不影响正文。

**Commit:**

```powershell
git commit --only -m "feat(ui): 接通互动卡片真实对话链路" -- web/src/pages/app/assistantState.ts web/src/pages/app/assistantMock.ts web/src/components/assistant/AssistantBlockRenderer.vue web/src/components/assistant/AssistantChatView.vue web/tests/assistantLearningBlocksIntegration.test.ts web/src/api/assistantStream.test.ts
```

---

## Task 8: 更新协议文档与新题型接入指南

**Files:**

- Modify: `docs/ai/assistant-output-format.md`
- Create: `docs/ai/learning-blocks-and-activities.md`
- Modify: `docs/superpowers/specs/2026-07-22-adaptive-learning-orchestration-design.md`

### Steps

- [ ] 在输出协议文档记录 `interaction` 请求字段、`message.completed.parts`、历史消息 parts、unknown-version fallback 和完整 sentence_reorder JSON 示例。
- [ ] 新建接入指南，说明新增只读块需要 schema/component/registry/test，新增互动块还需要 adapter/grader/activity tests。
- [ ] 明确 XState 与 Pinia 边界：XState 不持久化、不管理会话列表、不存用户状态。
- [ ] 明确 Phase 1 数据边界：数据库保存消息 parts，不保存答案、成绩、尝试或活动快照。
- [ ] 更新设计文档的实施状态，只标记实际完成项，不提前勾选 Phase 2/3。
- [ ] 检查文档中的字段名与三端代码完全一致。

**Verification:**

```powershell
cd F:\personalenglishai
rg -n "interaction|uiIntent|sentence_reorder|parts_json|XState|不持久化" docs/ai docs/superpowers/specs/2026-07-22-adaptive-learning-orchestration-design.md
```

Expected: 协议、状态边界和扩展步骤可以只靠文档复现，不存在同义字段分叉。

**Commit:**

```powershell
git commit --only -m "docs(ui): 补充学习活动接入协议" -- docs/ai/assistant-output-format.md docs/ai/learning-blocks-and-activities.md docs/superpowers/specs/2026-07-22-adaptive-learning-orchestration-design.md
```

---

## Task 9: 全链路验证与发布前检查

**Files:**

- Modify only if a verified defect is found: files already listed in Tasks 1–8

### Automated verification

- [ ] 运行全部新增前端纯逻辑/契约测试。
- [ ] 运行 `npm run build`，记录 chunk warning，但不得把既有 warning 当成失败。
- [ ] 运行 Python assistant 相关回归，确认普通 Markdown、结构化练习主路径和失败路径。
- [ ] 运行 Java assistant controller/service 测试。
- [ ] 用测试数据库执行 `migration_add_assistant_message_parts.sql`，确认重复执行策略由部署流程控制，并验证旧行可读。

```powershell
cd F:\personalenglishai\web
node --test --experimental-strip-types src/components/assistant/learning-blocks/registry.test.ts src/components/assistant/learning-blocks/sentence-reorder/grader.test.ts src/components/assistant/learning-activities/activityMachine.test.ts src/api/assistantStream.test.ts src/pages/app/assistantInteraction.test.ts
node --experimental-strip-types tests/assistantLearningBlocksIntegration.test.ts
node --experimental-strip-types tests/sentenceReorderAccessibility.test.ts
npm run build

cd F:\personalenglishai\python
.\.venv\Scripts\python.exe -m pytest ai_orchestrator/tests/test_learning_block_contracts.py ai_orchestrator/tests/test_generate_sentence_reorder_workflow.py ai_orchestrator/tests/test_assistant_service.py ai_orchestrator/tests/test_assistant_stream_events.py ai_orchestrator/tests/test_chat_contracts.py ai_orchestrator/tests/test_assistant_run_endpoint.py -q

cd F:\personalenglishai\backend
.\mvnw.cmd -Dtest=AssistantConversationServiceTest,AssistantControllerTest test
```

### Manual browser verification

- [ ] 普通输入“actually 是什么意思？”只返回 Markdown，不出现活动卡。
- [ ] 点击“重组成句练习”发送显式 interaction，加载后出现一张题卡。
- [ ] 用鼠标/触屏点击词块形成答案；错误提交显示正确答案和解释。
- [ ] 用键盘完成一题，结果被 `aria-live` 播报。
- [ ] 正确提交后可以进入下一题，最后一题后进入完成状态。
- [ ] 任意题目可结束；结束后主 composer 仍可提问。
- [ ] 活动中从主 composer 提普通问题，活动不会被误提交。
- [ ] 刷新或切换会话后结构化卡片仍存在，但未完成的答案与分数不恢复。
- [ ] 在 390px、768px 和桌面宽度检查 token 换行、按钮、反馈区和焦点。
- [ ] 模拟未知 block version，确认显示 fallback/保留 Markdown，不白屏。

### Final repository checks

- [ ] `git diff --check` 无空白错误。
- [ ] `git status --short` 中确认没有把原有 45 个无关暂存文件纳入任何提交。
- [ ] `git log --oneline --max-count=12` 确认每个任务是小提交且使用 Conventional Commits。
- [ ] 评估合并到 `main`：该功能包含依赖、数据库 schema、三端协议与新 workflow，只有自动化验证、迁移演练和人工浏览器验证全部通过后才适合合并；否则保持在功能分支。

---

## Implementation Notes

### 为什么消息 parts 要在 Phase 1 落库

前端在流式完成后会重新请求 `getConversation` 并用远端会话替换乐观状态。如果后端只保存 content，卡片会立即消失；因此 `parts_json` 是展示一致性所需的消息字段，不等同于学习行为持久化。

### 为什么第一题型不使用模型判分

重组成句是封闭题。模型生成题目时确定正确 token 顺序，前端只比较 token ID；这保证重复提交结果一致、离线可判分并且易于测试。开放写作、口语等题型以后可以把同一状态机的 `SUBMIT` 后端实现替换成异步 actor，但不能反向修改已提交题目的标准答案。

### 为什么显式练习不再经过自然语言猜测

按钮本身已经表达用户意图。把 `uiIntent=start_practice` 结构化传递可以减少一次路由误判，并允许日志和测试直接验证。用户自由输入“给我练习”仍由现有 RouteAgent 识别；Phase 1 不新增第二个意图分类模型。

### XState 官方接入依据

- 使用 `npm install xstate @xstate/vue`。
- 使用 XState v5 `setup(...)` 获得 context/event/guard 的强类型推断。
- Vue 组件使用 `useMachine` 返回的 `snapshot` 与 `send`；不使用 v4 的 `state/service` API。
- 状态机通过事件接收判分结果，不直接承担 API、缓存或数据库职责。

### Merge assessment

本计划与设计文档可以直接合并；实现本身属于跨前端、后端、Python 和数据库的高影响功能，不建议在任何一端未验证时直接合并到 `main`。最小合并门槛是：三端自动化测试通过、数据库迁移演练成功、普通 Markdown 回归通过、重组成句全流程人工验证通过。
