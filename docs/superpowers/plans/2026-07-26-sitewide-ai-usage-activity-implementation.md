# 全站 AI 用量活动 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 AI Token 事件账本升级为个人中心可查询、可解释的全站用量活动，并补齐学习助手和词汇卡生成的可靠用量归集。

**Architecture:** 保留 `ai_token_usage_event` 作为不可变事实源，新增独立 `/api/users/me/usage` 只读接口，由 Java 服务按请求时区聚合原始事件并通过集中式产品分类器输出稀疏日 bucket。前端只消费日 bucket，并在浏览器内派生每日热力图、52 周趋势、12 月累计和产品构成；订阅额度、兑换码和套餐继续使用原接口。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis、JUnit 5、Python 3、Pydantic、OpenAI Agents SDK、Vue 3、TypeScript、Node Test Runner、Vite

## Global Constraints

- 继续复用 `ai_token_usage_event`、现有订阅聚合表和兑换码流程，不新增数据库表。
- `/api/subscription/me` 保持当前契约；历史活动只通过 `/api/users/me/usage` 查询。
- 原始事件时刻按 UTC 解释，默认展示时区固定为 `Asia/Shanghai`。
- 查询跨度最多 366 个自然日，响应只返回有用量的稀疏日 bucket。
- 优先使用供应商 `total_tokens`；仅在缺失时回退为输入与输出 Token 之和。
- 托管 ChatKit、本地 PDF/OCR 和非 Token 资源不纳入本轮统计。
- 正式支付前的额度预占、可靠写入、冲正与供应商对账只进入文档边界，本轮不实现。

---

### Task 1: 统一 Token 口径与产品分类

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/subscription/AiUsageProductClassifier.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/subscription/AiUsageProductClassifierTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/subscription/AiUsageRecorder.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/subscription/SubscriptionService.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/subscription/SubscriptionServiceTest.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/subscription/AiUsageRecorderTest.java`

**Interfaces:**
- Produces: `String AiUsageProductClassifier.classify(String featureKey)`
- Changes: `SubscriptionService.recordUsage(AiTokenUsageRecord)` writes a UTC `occurredAt`.
- Changes: `AiUsageRecorder.recordCurrentContext(...)` gives a positive provider total precedence over derived fields.

- [ ] **Step 1: Write failing classifier and Token precedence tests**

```java
@Test
void classifiesStableProductDimensions() {
    assertThat(classifier.classify("assistant.conversation")).isEqualTo("assistant");
    assertThat(classifier.classify("ai.command.free_chat")).isEqualTo("assistant");
    assertThat(classifier.classify("writing.translate")).isEqualTo("translation");
    assertThat(classifier.classify("writing.evaluate")).isEqualTo("writing");
    assertThat(classifier.classify("vocabulary.card-generation")).isEqualTo("vocabulary");
    assertThat(classifier.classify("future.unknown")).isEqualTo("other");
}

@Test
void providerTotalWinsOverComponentSum() {
    recorder.recordCurrentContext("openai", "gpt-5", "resp-1", 100, 20, 40, 30, 150);
    assertThat(captured.totalTokens()).isEqualTo(150L);
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
cd backend
mvn -Dtest=AiUsageProductClassifierTest,AiUsageRecorderTest,SubscriptionServiceTest test
```

Expected: tests fail because the classifier and corrected total/UTC behavior do not exist.

- [ ] **Step 3: Implement minimal classifier and total precedence**

```java
public String classify(String featureKey) {
    String key = featureKey == null ? "" : featureKey.trim().toLowerCase(Locale.ROOT);
    if (key.startsWith("assistant.") || key.startsWith("ai.command.")) return "assistant";
    if ("writing.translate".equals(key) || key.startsWith("translation.")) return "translation";
    if (key.startsWith("writing.")) return "writing";
    if (key.startsWith("vocabulary.")) return "vocabulary";
    return "other";
}
```

Use provider total when it is non-null and non-negative; otherwise return non-negative input plus non-negative output. Store event time with:

```java
LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Task 1 Maven command and require zero failures.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/subscription backend/src/test/java/com/personalenglishai/backend/service/subscription
git commit -m "fix(subscription): 统一 AI Token 计量口径"
```

---

### Task 2: 新增独立用户用量查询接口

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/subscription/dto/AiUsageActivityResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/subscription/AiUsageActivityService.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/subscription/AiUsageActivityServiceTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/mapper/subscription/AiTokenUsageMapper.java`
- Modify: `backend/src/main/resources/mapper/AiTokenUsageMapper.xml`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/UserController.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/controller/UserUsageControllerTest.java`

**Interfaces:**
- Consumes: `AiUsageProductClassifier.classify(String)`
- Produces: `AiUsageActivityResponse AiUsageActivityService.getActivity(Long userId, String metric, LocalDate from, LocalDate to, String timezone)`
- Produces: `GET /api/users/me/usage?metric=ai_tokens&from=YYYY-MM-DD&to=YYYY-MM-DD&granularity=day&timezone=Asia/Shanghai`
- Mapper: `List<AiTokenUsageEvent> selectEventsByUserAndOccurredAt(Long userId, LocalDateTime fromUtc, LocalDateTime toUtcExclusive)`

- [ ] **Step 1: Write failing service tests for timezone aggregation and validation**

```java
@Test
void groupsUtcEventsIntoRequestedTimezoneAndProduct() {
    mapper.events.add(event("a", 7L, "assistant.conversation", 30L,
            LocalDateTime.of(2026, 7, 25, 16, 30)));
    var response = service.getActivity(
            7L, "ai_tokens", LocalDate.of(2026, 7, 26),
            LocalDate.of(2026, 7, 26), "Asia/Shanghai");
    assertThat(response.getTotal()).isEqualTo(30L);
    assertThat(response.getBuckets()).singleElement().satisfies(bucket -> {
        assertThat(bucket.getDate()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(bucket.getByProduct().get("assistant")).isEqualTo(30L);
    });
}

@Test
void rejectsRangesLongerThan366Days() {
    assertThatThrownBy(() -> service.getActivity(
            7L, "ai_tokens", LocalDate.of(2025, 1, 1),
            LocalDate.of(2026, 7, 26), "Asia/Shanghai"))
            .isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: Write failing MVC contract test**

```java
mockMvc.perform(get("/api/users/me/usage")
        .requestAttr("userId", 7L)
        .param("metric", "ai_tokens")
        .param("from", "2026-07-01")
        .param("to", "2026-07-26")
        .param("granularity", "day")
        .param("timezone", "Asia/Shanghai"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.metric").value("ai_tokens"))
    .andExpect(jsonPath("$.data.unit").value("token"));
```

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
cd backend
mvn -Dtest=AiUsageActivityServiceTest,UserUsageControllerTest test
```

Expected: compilation/test failure because the service, DTO, mapper method and route are absent.

- [ ] **Step 4: Implement mapper, aggregation service and controller**

The mapper selects the authenticated user’s events inside the half-open UTC interval:

```xml
<select id="selectEventsByUserAndOccurredAt"
        resultType="com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent">
    SELECT usage_event_id, user_id, feature_key, provider, model,
           input_tokens, cached_input_tokens, output_tokens,
           reasoning_tokens, total_tokens, trace_id, occurred_at
    FROM ai_token_usage_event
    WHERE user_id = #{userId}
      AND occurred_at &gt;= #{fromUtc}
      AND occurred_at &lt; #{toUtcExclusive}
    ORDER BY occurred_at ASC, usage_event_id ASC
</select>
```

The service validates metric, granularity, date order, 366-day maximum and IANA zone, converts local day boundaries to UTC, aggregates `totalTokens` by local date and classifier product, and returns ordered sparse buckets.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Task 2 Maven command and require zero failures.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/controller/UserController.java backend/src/main/java/com/personalenglishai/backend/mapper/subscription/AiTokenUsageMapper.java backend/src/main/resources/mapper/AiTokenUsageMapper.xml backend/src/main/java/com/personalenglishai/backend/service/subscription backend/src/test/java/com/personalenglishai/backend
git commit -m "feat(api): 新增个人 AI 用量活动接口"
```

---

### Task 3: 归集学习助手同步与流式用量

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantUsageService.java`
- Create: `backend/src/test/java/com/personalenglishai/backend/service/assistant/AssistantUsageServiceTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/assistant/AssistantConversationServiceTest.java`

**Interfaces:**
- Consumes: `SubscriptionService.assertAiTokenQuotaAvailable(Long)`
- Consumes: `SubscriptionService.recordUsage(AiTokenUsageRecord)`
- Produces: `void AssistantUsageService.assertQuota(Long userId)`
- Produces: `void AssistantUsageService.record(Long userId, AssistantRunMetadataResponse run)`

- [ ] **Step 1: Write failing usage service tests**

```java
@Test
void recordsRunUsageWithStableRunId() {
    service.record(7L, run("run-42", "gpt-5", 80, 20, 90));
    assertThat(captured.usageEventId()).isEqualTo("assistant:run-42");
    assertThat(captured.featureKey()).isEqualTo("assistant.conversation");
    assertThat(captured.totalTokens()).isEqualTo(90L);
}

@Test
void skipsMissingUsageWithoutInventingTokens() {
    service.record(7L, runWithoutUsage("run-42"));
    verifyNoInteractions(subscriptionService);
}
```

- [ ] **Step 2: Write failing conversation tests for quota and exactly-once recording**

Add a synchronous reply with run metadata and a stream containing both `message.completed` and `run.completed`. Assert quota is checked before the Python client and record is invoked once after completion.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
cd backend
mvn -Dtest=AssistantUsageServiceTest,AssistantConversationServiceTest test
```

Expected: tests fail because assistant quota and usage integration are absent.

- [ ] **Step 4: Implement fail-open recording and integrate both paths**

`AssistantUsageService.record` creates:

```java
new AiTokenUsageRecord(
    "assistant:" + run.getRunId(),
    userId,
    "assistant.conversation",
    "openai",
    run.getModel(),
    toLong(usage.getInputTokens()),
    toLong(usage.getCachedInputTokens()),
    toLong(usage.getOutputTokens()),
    null,
    toLong(usage.getTotalTokens()),
    run.getTraceId())
```

It logs and returns when run ID or usage is absent. `AssistantConversationService` calls `assertQuota` before each Python request and `record` after each successful synchronous or stream completion. The same run ID makes retries and duplicate stream metadata idempotent at the ledger.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Task 3 Maven command and require zero failures.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/service/assistant backend/src/test/java/com/personalenglishai/backend/service/assistant
git commit -m "feat(assistant): 归集对话 AI Token 用量"
```

---

### Task 4: 为词汇卡生成契约补充 Token 用量

**Files:**
- Modify: `python/ai_orchestrator/schemas/vocabulary_card.py`
- Modify: `python/ai_orchestrator/workflows/vocabulary_card_generation.py`
- Modify: `python/ai_orchestrator/tests/test_vocabulary_card_schemas.py`
- Modify: `python/ai_orchestrator/tests/test_vocabulary_card_generation_workflow.py`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationMetadata.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationPythonResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/GeneratedVocabularyCard.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProvider.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorker.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/PythonVocabularyGenerationProviderTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyGenerationWorkerTest.java`

**Interfaces:**
- Adds: nullable `usage` inside `VocabularyGenerationMetadata`, with `inputTokens`, `cachedInputTokens`, `outputTokens`, `totalTokens`, `requests`.
- Preserves: contract version 2 and all existing response fields.
- Consumes: `SubscriptionService.recordUsage(AiTokenUsageRecord)` in the Java worker.

- [ ] **Step 1: Write failing Python schema/workflow tests**

```python
def test_generation_metadata_accepts_strict_usage() -> None:
    payload = response_payload()
    payload["generation"]["usage"] = {
        "inputTokens": 40,
        "cachedInputTokens": 10,
        "outputTokens": 20,
        "totalTokens": 60,
        "requests": 2,
    }
    parsed = VocabularyCardGenerationResponse.model_validate(payload)
    assert parsed.generation.usage.total_tokens == 60
```

The workflow test provides two agent results with usage and expects the response metadata to contain their summed input, cached input, output, total and request count.

- [ ] **Step 2: Run Python tests and verify RED**

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_card_schemas.py python/ai_orchestrator/tests/test_vocabulary_card_generation_workflow.py -q
```

Expected: tests fail because vocabulary generation metadata rejects/omits usage.

- [ ] **Step 3: Implement Python usage accumulation**

Reuse `extract_usage` from `services.agent_session_runner`; capture each successful agent result and sum:

```python
VocabularyGenerationUsage(
    inputTokens=sum(item.input_tokens for item in usages),
    cachedInputTokens=sum(item.cached_input_tokens for item in usages),
    outputTokens=sum(item.output_tokens for item in usages),
    totalTokens=sum(item.total_tokens for item in usages),
    requests=sum(item.requests for item in usages),
)
```

Return `usage=None` only when no trustworthy SDK usage is available.

- [ ] **Step 4: Run Python tests and verify GREEN**

Run the Task 4 Python command and require zero failures.

- [ ] **Step 5: Write failing Java provider and worker tests**

The provider test parses the complete Python JSON including usage. The worker test uses a generated card with usage and verifies one event:

```java
assertThat(captured.usageEventId()).isEqualTo("vocabulary-card:" + job.getJobUid());
assertThat(captured.userId()).isEqualTo(card.getUserId());
assertThat(captured.featureKey()).isEqualTo("vocabulary.card-generation");
assertThat(captured.totalTokens()).isEqualTo(60L);
```

- [ ] **Step 6: Run Java vocabulary tests and verify RED**

```powershell
cd backend
mvn -Dtest=PythonVocabularyGenerationProviderTest,VocabularyGenerationWorkerTest test
```

Expected: tests fail because Java metadata and worker do not consume usage.

- [ ] **Step 7: Implement Java contract parsing and worker recording**

Extend the strict metadata field set with nullable usage, propagate it through `GeneratedVocabularyCard`, and record after successful finalization. Use job UID for the event ID and card owner for user ID. If usage is absent or total is zero, skip without estimation.

- [ ] **Step 8: Run Java vocabulary tests and verify GREEN**

Run the Task 4 Java command and require zero failures.

- [ ] **Step 9: Commit**

```powershell
git add python/ai_orchestrator/schemas/vocabulary_card.py python/ai_orchestrator/workflows/vocabulary_card_generation.py python/ai_orchestrator/tests backend/src/main/java/com/personalenglishai/backend/service/vocabulary backend/src/test/java/com/personalenglishai/backend/service/vocabulary
git commit -m "feat(vocabulary): 记录词汇卡生成用量"
```

---

### Task 5: 实现前端日、周、月活动模型

**Files:**
- Create: `web/src/components/personal-center/usageActivity.ts`
- Create: `web/src/components/personal-center/usageActivity.test.ts`
- Modify: `web/src/api/user.ts`

**Interfaces:**
- Adds: `AiUsageActivity`, `AiUsageDayBucket`, `AiUsageProductKey`.
- Adds: `userApi.getMyAiUsage(params)`.
- Produces: `buildUsageCalendar(activity, today)`, `buildWeeklyUsage(activity)`, `buildMonthlyUsage(activity)`, `buildProductBreakdown(activity)`.

- [ ] **Step 1: Write failing pure-model tests**

```typescript
test('日历包含完整日期并按非零日分位数分级', () => {
  const model = buildUsageCalendar(activity([
    day('2026-07-24', 10),
    day('2026-07-25', 20),
    day('2026-07-26', 10_000),
  ]), '2026-07-26')
  assert.equal(model.days.filter((item) => item.total > 0).length, 3)
  assert.ok(model.days.find((item) => item.date === '2026-07-24')!.level > 0)
})

test('周和月聚合与日总量守恒', () => {
  const source = activity([day('2026-06-30', 30), day('2026-07-01', 70)])
  assert.equal(sum(buildWeeklyUsage(source)), 100)
  assert.equal(sum(buildMonthlyUsage(source)), 100)
})
```

- [ ] **Step 2: Run Node tests and verify RED**

```powershell
cd web
node --test --experimental-strip-types src/components/personal-center/usageActivity.test.ts
```

Expected: module-not-found failure because the model is absent.

- [ ] **Step 3: Implement model and API types**

Use UTC-safe string date helpers rather than `new Date('YYYY-MM-DD')` local conversions. Build four non-zero quantile levels, Monday-based weeks, natural months, stable product labels, zero-safe percentages and sparse response normalization.

- [ ] **Step 4: Run Node tests and verify GREEN**

Run the Task 5 Node command and require zero failures.

- [ ] **Step 5: Commit**

```powershell
git add web/src/api/user.ts web/src/components/personal-center/usageActivity.ts web/src/components/personal-center/usageActivity.test.ts
git commit -m "feat(ui): 增加 AI 用量活动数据模型"
```

---

### Task 6: 替换进度条为高级用量活动组件

**Files:**
- Create: `web/src/components/personal-center/AiUsageActivityPanel.vue`
- Modify: `web/src/components/personal-center/SubscriptionSection.vue`

**Interfaces:**
- Consumes: `userApi.getMyAiUsage(...)`
- Consumes: Task 5 activity model functions.
- Emits no global state; subscription and activity requests fail independently.

- [ ] **Step 1: Implement accessible activity panel using tested model**

Create a white 18px-radius card with:

- “全站 AI Token 活动” title and total.
- “每日 / 每周 / 累计” segmented tabs.
- 53 × 7 calendar grid for daily mode.
- 52-week and 12-month CSS bar charts for aggregate modes.
- Keyboard-focusable cells/bars with native title and an in-panel tooltip.
- Product composition rows below the chart.
- loading skeleton, empty state and retry action.
- internal horizontal scroll below 760px.

No new UI/chart dependency is introduced.

- [ ] **Step 2: Integrate independently from subscription loading**

`SubscriptionSection.vue` continues loading plans/status and renders `AiUsageActivityPanel` between current entitlement and redeem form. A usage API failure must not hide entitlement, redemption or plans.

- [ ] **Step 3: Run front-end model tests and production build**

```powershell
cd web
node --test --experimental-strip-types src/components/personal-center/usageActivity.test.ts
npm run build
```

Expected: all Node tests pass and Vite build exits 0.

- [ ] **Step 4: Commit**

```powershell
git add web/src/components/personal-center/AiUsageActivityPanel.vue web/src/components/personal-center/SubscriptionSection.vue
git commit -m "feat(ui): 展示全站 AI Token 活动"
```

---

### Task 7: 更新正式文档并完成全链路验证

**Files:**
- Create: `docs/api/user-ai-usage.md`
- Modify: `docs/api/index.md`
- Modify: `docs/data/subscription-and-ai-usage.md` if present; otherwise create it from `docs/templates/data.md`.
- Modify: `design-qa.md`

**Interfaces:**
- Documents the exact endpoint, timezone, classification, exclusions and future billing prerequisites.

- [ ] **Step 1: Update API and data documentation**

Document:

- endpoint, authentication, parameters, response and validation errors;
- UTC storage and requested-zone grouping;
- event ledger versus quota aggregates;
- current product mapping;
- ChatKit/OCR/audio exclusions;
- pre-payment reservation, outbox, adjustment and reconciliation requirements.

- [ ] **Step 2: Run complete automated verification**

```powershell
cd backend
mvn test
cd ..\python
python -m pytest ai_orchestrator/tests/test_vocabulary_card_schemas.py ai_orchestrator/tests/test_vocabulary_card_generation_workflow.py -q
cd ..\web
node --test --experimental-strip-types src/components/personal-center/usageActivity.test.ts
npm run build
cd ..\docs
npm run build
```

All commands must exit 0 before completion is claimed.

- [ ] **Step 3: Browser regression**

Open `http://127.0.0.1:4173/app/me?tab=subscription` and verify:

1. current plan, quota and redemption remain visible;
2. daily heatmap, weekly trend and cumulative month view switch correctly;
3. tooltip and product composition match returned data;
4. empty and API-failure states do not block redemption;
5. desktop and narrow viewport layouts remain usable;
6. no console error or failed unexpected network request appears.

Record the result and unresolved severity in `design-qa.md`.

- [ ] **Step 4: Review scope, docs and merge readiness**

Run:

```powershell
git status --short
git diff --check
git diff origin/main...HEAD --stat
```

Confirm no unrelated files were changed. The branch is mergeable only after all automated verification and browser QA pass; payment-grade reservation/outbox/reconciliation remain documented follow-up work and do not block this redemption-stage feature.

- [ ] **Step 5: Commit documentation and QA**

```powershell
git add docs/api docs/data design-qa.md
git commit -m "docs(usage): 记录 AI 用量接口与数据口径"
```
