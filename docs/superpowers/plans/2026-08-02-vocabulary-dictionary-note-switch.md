# Vocabulary Dictionary Note Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在单词搜索页中，为已沉淀的同词单词卡提供“词典释义 / 我的笔记”内联切换，并展示数据库中当前有效版本的真实模板内容与用户修改。

**Architecture:** 后端在现有单词卡服务上增加按当前用户、语言和规范化词形解析 `cardUid` 的只读接口，随后继续复用现有详情接口。前端使用 TanStack Query 分离解析查询与详情查询，用新的只读预览组件复用 Lexical Core、Card Blocks 和 Markdown 渲染器；搜索页只管理当前面板选择和路由跳转。

**Tech Stack:** Java 17、Spring Boot 3.2、MyBatis、JUnit 5、Mockito、MockMvc、Vue 3、TypeScript、TanStack Query、Vue Router、Node test runner、tsx、Playwright、Vite。

## Global Constraints

- 默认展示词典释义；只有当前用户存在语言与规范化词形完全匹配的未删除单词卡时才显示切换。
- 已软删除的单词卡不得解析成功，也不得在搜索页暴露 `cardUid`。
- “我的笔记”必须展示卡片当前有效版本的真实 `core`、`cardBlocks` 或历史 `markdown`，不能创建新的固定模板。
- 搜索新词或切换词典语言后立即恢复到词典释义，迟到响应不能覆盖当前词。
- 搜索页内只读；编辑、重新生成、历史和删除继续通过现有完整单词卡详情页完成。
- 不增加数据库字段、表、前端持久化键、Pinia 状态或运行时依赖。
- 词典失败与笔记失败相互隔离；笔记解析或详情失败不能覆盖可用的词典内容。
- 保持现有列表、详情、收藏、查询次数和单词卡修订接口兼容。

---

### Task 1: 后端精确解析单词卡身份

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCardResolutionResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTransactionTest.java`

**Interfaces:**
- Consumes: `VocabularyTermNormalizer.normalize(String)` and `VocabularyCardMapper.findByIdentityIncludingDeleted(Long, String, String)`.
- Produces: `VocabularyCardService.resolve(Long userId, String term, String language): VocabularyCardResolutionResponse` and response fields `found`, `cardUid`.

- [ ] **Step 1: Write failing service tests for exact identity, missing card, and soft deletion**

Add tests equivalent to:

```java
@Test
void resolvesTheCurrentUsersExactNormalizedCardIdentity() {
    VocabularyCard card = VocabularyTestFixtures.ready("card_wonder", 7L, "wonder", "rev_1");
    when(cards.findByIdentityIncludingDeleted(7L, "en", "wonder")).thenReturn(card);

    var result = service.resolve(7L, "  Wonder!  ", "EN");

    assertTrue(result.found());
    assertEquals("card_wonder", result.cardUid());
}

@Test
void returnsNotFoundForMissingOrSoftDeletedExactCard() {
    when(cards.findByIdentityIncludingDeleted(7L, "en", "absent")).thenReturn(null);
    assertFalse(service.resolve(7L, "absent", "en").found());

    VocabularyCard deleted = VocabularyTestFixtures.ready("card_deleted", 7L, "deleted", "rev_1");
    deleted.setDeletedAt(LocalDateTime.of(2026, 8, 1, 12, 0));
    when(cards.findByIdentityIncludingDeleted(7L, "en", "deleted")).thenReturn(deleted);
    assertFalse(service.resolve(7L, "deleted", "en").found());
}
```

Update both direct `new VocabularyCardService(...)` test constructors to pass a real `VocabularyTermNormalizer`.

- [ ] **Step 2: Run the focused service tests and verify the new contract fails**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyCardServiceTest,VocabularyCardServiceTransactionTest test
```

Expected: compilation or assertion failure because the resolution DTO, constructor dependency, and `resolve` method do not exist yet.

- [ ] **Step 3: Add the response DTO and minimal service implementation**

Create:

```java
package com.personalenglishai.backend.dto.vocabulary;

public record VocabularyCardResolutionResponse(boolean found, String cardUid) {
    public static VocabularyCardResolutionResponse found(String cardUid) {
        return new VocabularyCardResolutionResponse(true, cardUid);
    }

    public static VocabularyCardResolutionResponse notFound() {
        return new VocabularyCardResolutionResponse(false, null);
    }
}
```

Inject `VocabularyTermNormalizer` into `VocabularyCardService`, then add:

```java
public VocabularyCardResolutionResponse resolve(Long userId, String term, String language) {
    String normalizedTerm = termNormalizer.normalize(term);
    String normalizedLanguage = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
    if (normalizedTerm.isBlank() || normalizedLanguage.isBlank()) {
        throw new IllegalArgumentException("term and language are required");
    }
    VocabularyCard card = cards.findByIdentityIncludingDeleted(userId, normalizedLanguage, normalizedTerm);
    if (card == null || card.getDeletedAt() != null) {
        return VocabularyCardResolutionResponse.notFound();
    }
    return VocabularyCardResolutionResponse.found(card.getCardUid());
}
```

Use the existing mapper call so ownership remains part of the database predicate; do not add an unscoped lookup.

- [ ] **Step 4: Run the focused service tests and verify they pass**

Run the same Maven command from Step 2.

Expected: all selected tests pass with zero failures and zero errors.

- [ ] **Step 5: Commit the service contract**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/dto/vocabulary/VocabularyCardResolutionResponse.java backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardService.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTest.java backend/src/test/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardServiceTransactionTest.java
git commit -m "feat(api): 增加单词卡精确解析服务"
```

### Task 2: 暴露兼容的精确解析 REST 接口

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java`
- Modify: `docs/api/vocabulary.md`

**Interfaces:**
- Consumes: `VocabularyCardService.resolve(Long, String, String)` from Task 1.
- Produces: `GET /api/vocabulary/cards/resolve?term=<term>&language=<language>` returning `ApiResponse<VocabularyCardResolutionResponse>`.

- [ ] **Step 1: Write failing MockMvc tests for found, absent, and anonymous requests**

Add controller tests equivalent to:

```java
@Test
void resolvesOwnedCardByTermAndLanguage() throws Exception {
    when(cardService.resolve(7L, "Wonder", "en"))
            .thenReturn(VocabularyCardResolutionResponse.found("card_wonder"));

    mockMvc.perform(get("/api/vocabulary/cards/resolve")
                    .requestAttr("userId", 7L)
                    .param("term", "Wonder")
                    .param("language", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.found").value(true))
            .andExpect(jsonPath("$.data.cardUid").value("card_wonder"));
}

@Test
void resolvesMissingCardWithoutTurningAbsenceIntoAnError() throws Exception {
    when(cardService.resolve(7L, "absent", "en"))
            .thenReturn(VocabularyCardResolutionResponse.notFound());

    mockMvc.perform(get("/api/vocabulary/cards/resolve")
                    .requestAttr("userId", 7L)
                    .param("term", "absent")
                    .param("language", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.found").value(false))
            .andExpect(jsonPath("$.data.cardUid").doesNotExist());
}
```

Extend the anonymous request test with `GET /api/vocabulary/cards/resolve?term=wonder&language=en` and expect HTTP 401.

- [ ] **Step 2: Run the controller test and verify the route is missing**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyControllerTest test
```

Expected: the new found/absent tests fail with HTTP 404 or a missing interaction.

- [ ] **Step 3: Add the static route before the dynamic card route**

Add:

```java
@GetMapping("/cards/resolve")
public ResponseEntity<ApiResponse<VocabularyCardResolutionResponse>> resolveCard(
        @RequestAttribute("userId") Long userId,
        @RequestParam String term,
        @RequestParam(defaultValue = "en") String language) {
    return ResponseEntity.ok(ApiResponse.success(cardService.resolve(userId, term, language)));
}
```

Keep `GET /cards/{cardUid}` unchanged. Document request parameters, found/absent response examples, current-user isolation, soft-delete behavior, and the follow-up detail call in `docs/api/vocabulary.md`.

- [ ] **Step 4: Run the controller and service tests**

Run:

```powershell
cd backend
mvn -Dtest=VocabularyControllerTest,VocabularyCardServiceTest,VocabularyCardServiceTransactionTest test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the endpoint and API documentation**

```powershell
git add backend/src/main/java/com/personalenglishai/backend/controller/VocabularyController.java backend/src/test/java/com/personalenglishai/backend/controller/VocabularyControllerTest.java docs/api/vocabulary.md
git commit -m "feat(api): 暴露单词卡精确解析接口"
```

### Task 3: 前端 API 与两阶段查询封装

**Files:**
- Modify: `web/src/api/vocabulary.ts`
- Create: `web/src/composables/useVocabularyCardResolution.ts`
- Modify: `web/tests/vocabularyApiContract.test.ts`
- Create: `web/tests/vocabularyCardResolution.test.ts`

**Interfaces:**
- Consumes: Task 2 resolution endpoint and existing `getVocabularyCard(cardUid)`.
- Produces: `resolveVocabularyCard(term, language)`, `VocabularyCardResolution`, `mapDictionaryLanguageToCardLanguage`, and `useVocabularyCardResolution(termRef, languageRef)`.

- [ ] **Step 1: Write failing API and pure helper tests**

Add an adapter-backed API test asserting:

```ts
http.defaults.adapter = async (config) => {
  assert.equal(config.method, 'get')
  assert.equal(config.url, '/vocabulary/cards/resolve')
  assert.deepEqual(config.params, { term: 'wonder', language: 'en' })
  return response(config, { code: '0', data: { found: true, cardUid: 'card_wonder' } })
}
assert.deepEqual(await resolveVocabularyCard('wonder', 'en'), {
  found: true,
  cardUid: 'card_wonder',
})
```

Create pure helper tests asserting:

```ts
assert.equal(mapDictionaryLanguageToCardLanguage('en-gb'), 'en')
assert.equal(mapDictionaryLanguageToCardLanguage('en-us'), 'en')
assert.equal(normalizeVocabularyResolutionTerm('  Wonder  '), 'wonder')
assert.equal(normalizeVocabularyResolutionTerm(''), '')
```

The source-contract part must also assert distinct query keys for resolution and detail and that detail is enabled only after a `cardUid` is resolved.

- [ ] **Step 2: Run the focused frontend tests and verify they fail**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyApiContract.test.ts tests/vocabularyCardResolution.test.ts
```

Expected: missing exports or assertions fail.

- [ ] **Step 3: Add the typed API wrapper and composable**

Add to the API module:

```ts
export interface VocabularyCardResolution {
  found: boolean
  cardUid: string | null
}

export const resolveVocabularyCard = (term: string, language: string) =>
  unwrap<VocabularyCardResolution>(http.get('/vocabulary/cards/resolve', {
    params: { term, language },
  }))
```

Implement the composable with:

```ts
const normalizedTerm = computed(() => normalizeVocabularyResolutionTerm(term.value))
const cardLanguage = computed(() => mapDictionaryLanguageToCardLanguage(language.value))
const resolutionQuery = useQuery({
  queryKey: computed(() => ['vocabulary', 'card-resolution', cardLanguage.value, normalizedTerm.value]),
  queryFn: () => resolveVocabularyCard(normalizedTerm.value, cardLanguage.value),
  enabled: computed(() => Boolean(normalizedTerm.value)),
  retry: shouldRetryVocabularyCardQuery,
})
const resolvedCardUid = computed(() => resolutionQuery.data.value?.cardUid ?? null)
const detailQuery = useQuery({
  queryKey: computed(() => ['vocabulary', 'card', resolvedCardUid.value]),
  queryFn: () => getVocabularyCard(resolvedCardUid.value!),
  enabled: computed(() => Boolean(resolvedCardUid.value)),
  retry: shouldRetryVocabularyCardQuery,
})
```

Return both query objects, `card`, `found`, and a `retry` function that retries the failed stage. Do not mirror query data into Pinia or storage.

- [ ] **Step 4: Run the focused frontend tests**

Run the same `tsx` command from Step 2.

Expected: all focused tests pass.

- [ ] **Step 5: Commit the frontend data boundary**

```powershell
git add web/src/api/vocabulary.ts web/src/composables/useVocabularyCardResolution.ts web/tests/vocabularyApiContract.test.ts web/tests/vocabularyCardResolution.test.ts
git commit -m "feat(ui): 接入单词卡精确解析查询"
```

### Task 4: 只读渲染当前有效笔记

**Files:**
- Create: `web/src/components/vocabulary/VocabularyCardNotePreview.vue`
- Create: `web/tests/vocabularyCardNotePreview.test.ts`

**Interfaces:**
- Consumes: `VocabularyCardDetail`, `VocabularyCoreSummary`, `VocabularyCardBlocks`, `VocabularyMarkdownRenderer`, and `safeExternalUrl`.
- Produces: props `{ card: VocabularyCardDetail }` and emits `open`, `pronounce`.

- [ ] **Step 1: Write a failing component contract test**

Read the component source and assert it:

```ts
for (const required of [
  'VocabularyCoreSummary',
  'VocabularyCardBlocks',
  'VocabularyMarkdownRenderer',
  'card.cardBlocks',
  'card.markdown',
  'card.sources',
  'card.theme?.name',
  '打开完整笔记',
  "emit('open')",
]) assert.ok(source.includes(required), required)

assert.doesNotMatch(source, /核心理解|我的语境|易混辨析/)
```

Also assert the component exposes a readable generation placeholder rather than inventing content when the current revision has no `core`, Blocks, or Markdown.

- [ ] **Step 2: Run the component test and verify the file is missing**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyCardNotePreview.test.ts
```

Expected: the test fails because the component does not exist.

- [ ] **Step 3: Implement the read-only preview**

The component must render:

```vue
<header class="vocabulary-note-preview__header">
  <div>
    <span>我的沉淀</span>
    <h2>{{ card.displayTerm }}</h2>
    <p>{{ card.theme?.name || '兼容卡片' }}<template v-if="card.themeVersion"> · v{{ card.themeVersion }}</template></p>
  </div>
  <button type="button" @click="emit('open')">打开完整笔记</button>
</header>

<VocabularyCoreSummary v-if="card.core" :core="card.core" @pronounce="forwardPronunciation" />
<VocabularyCardBlocks v-if="card.cardBlocks" :card-blocks="card.cardBlocks" />
<VocabularyMarkdownRenderer v-else-if="card.markdown" :markdown="card.markdown" />
<p v-else-if="!card.core" role="status">笔记内容正在生成</p>
```

Render sources from `card.sources`, allow external links only through `safeExternalUrl`, and show actual status/updated time. Use existing card data without fixed chapter labels or editing controls. Keep the document width and typography compatible with the dictionary detail card.

- [ ] **Step 4: Run the preview and existing renderer tests**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyCardNotePreview.test.ts tests/vocabularyCoreSummary.test.ts tests/vocabularyCardBlocks.test.ts tests/vocabularyMarkdownRenderer.test.ts
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the preview component**

```powershell
git add web/src/components/vocabulary/VocabularyCardNotePreview.vue web/tests/vocabularyCardNotePreview.test.ts
git commit -m "feat(ui): 增加单词笔记只读预览"
```

### Task 5: 在搜索页接入可访问的内联切换

**Files:**
- Modify: `web/src/views/VocabularyView.vue`
- Modify: `web/tests/vocabularyDictionaryPage.test.ts`
- Modify: `web/tests/vocabularyDepositionFlow.spec.ts`

**Interfaces:**
- Consumes: `useVocabularyCardResolution` and `VocabularyCardNotePreview` from Tasks 3 and 4.
- Produces: search detail mode `'dictionary' | 'note'`, accessible tab UI, automatic reset, retry state, and full-note navigation.

- [ ] **Step 1: Write failing page contract assertions**

Add assertions for:

```ts
for (const required of [
  'useVocabularyCardResolution',
  'VocabularyCardNotePreview',
  '词典释义',
  '我的笔记',
  'role="tablist"',
  'role="tab"',
  'role="tabpanel"',
  'searchDetailMode',
  'openResolvedVocabularyCard',
  '我的笔记暂不可用',
]) assert.ok(pageSource.includes(required), required)
```

Assert that the resolution term is derived from the successful dictionary result rather than the editable input value, so typing does not switch the visible note before search succeeds.

- [ ] **Step 2: Extend Playwright mocks and write failing interaction tests**

In `installApiMocks`, handle `/cards/resolve` before the dynamic `/cards/{cardUid}` matcher:

```ts
if (path.endsWith('/cards/resolve') && method === 'GET') {
  const term = url.searchParams.get('term')?.toLocaleLowerCase('en-US') ?? ''
  const card = cards.find((item) => item.language === 'en' && item.normalizedTerm === term)
  return route.fulfill({ json: { code: '0', data: {
    found: Boolean(card),
    cardUid: card?.cardUid ?? null,
  } } })
}
```

Add a browser test that mocks dictionary lookup for `wonder`, opens the search page, submits the word, verifies both tabs, switches to “我的笔记”, and confirms the preview contains the saved theme, current Markdown/Card Blocks, and source context. Click “打开完整笔记” and expect `/app/vocabulary/cards/card_wonder`.

Add a second test that searches `wonder`, switches to the note, then searches `absent`; expect the tab strip to disappear and the Oxford dictionary panel to be selected. Add keyboard assertions for `ArrowRight` and `ArrowLeft` across the two tabs.

- [ ] **Step 3: Run focused page tests and verify they fail before implementation**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyDictionaryPage.test.ts
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps --grep "词典.*笔记|dictionary.*note"
```

Expected: contract and browser tests fail because the tabs, note preview, and resolve mock path are not connected yet.

- [ ] **Step 4: Implement state, template, keyboard behavior, and styles**

Add:

```ts
type SearchDetailMode = 'dictionary' | 'note'
const searchDetailMode = ref<SearchDetailMode>('dictionary')
const vocabularyResolutionTerm = computed(() => result.value?.word?.trim() ?? '')
const noteResolution = useVocabularyCardResolution(vocabularyResolutionTerm, language)

watch([vocabularyResolutionTerm, language], () => {
  searchDetailMode.value = 'dictionary'
})
watch(() => noteResolution.found.value, (found) => {
  if (!found && searchDetailMode.value === 'note') searchDetailMode.value = 'dictionary'
})
```

Render the tablist only after identity resolution confirms a card. Keep the dictionary component mounted for dictionary mode and render `VocabularyCardNotePreview` for note mode. The error bar must call the composable retry and must not replace `DictionaryDetail`.

Implement `ArrowLeft`, `ArrowRight`, `Home`, and `End` for the two tabs, update `aria-selected` and `tabindex`, and move focus to the selected tab. Style the control as a compact centered segmented switch that shares the existing green, white, and slate palette; do not add a new page header or card shell.

`openResolvedVocabularyCard()` must call the existing `selectVocabularyCard(card.cardUid)` so routing and navigation context remain centralized.

- [ ] **Step 5: Run focused contract, component, and browser tests**

Run:

```powershell
cd web
npx tsx --test tests/vocabularyDictionaryPage.test.ts tests/vocabularyCardResolution.test.ts tests/vocabularyCardNotePreview.test.ts
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps --grep "词典.*笔记|dictionary.*note"
```

Expected: all selected tests pass, including route navigation and keyboard switching.

- [ ] **Step 6: Commit the integrated search experience**

```powershell
git add web/src/views/VocabularyView.vue web/tests/vocabularyDictionaryPage.test.ts web/tests/vocabularyDepositionFlow.spec.ts
git commit -m "feat(ui): 支持词典与沉淀笔记切换"
```

### Task 6: 全量回归、文档一致性和合并评估

**Files:**
- Modify if contract wording requires alignment: `docs/architecture/vocabulary-deposition.md`
- Verify: all files changed in Tasks 1-5.

**Interfaces:**
- Consumes: completed backend endpoint, frontend query, preview, and page integration.
- Produces: verified branch with API/architecture documentation matching runtime behavior.

- [ ] **Step 1: Update architecture documentation only where the new read path changes the documented boundary**

Add one paragraph stating that dictionary lookup remains separate from the user card, while the search page may resolve an owned card identity and read its current active revision through the existing detail endpoint. Explicitly state that this read path does not merge dictionary favorite/query state into the card.

- [ ] **Step 2: Run the complete backend test suite**

Run:

```powershell
cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-resolution-32-bytes'
mvn test
```

Expected: zero failures and zero errors. If a MySQL opt-in test reports skipped because disposable credentials are absent, report the skip separately and do not present it as a live MySQL pass.

- [ ] **Step 3: Run all vocabulary source tests and the production build**

Run:

```powershell
cd web
npx tsx --test "tests/vocabulary*.test.ts"
npm run build
```

Expected: all vocabulary tests pass; `vue-tsc` and Vite exit successfully.

- [ ] **Step 4: Run the vocabulary Chromium browser regression**

Run:

```powershell
cd web
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium --no-deps
```

Expected: all tests in the specification pass with no uncaught page errors.

- [ ] **Step 5: Inspect the final diff and commit any documentation-only adjustment**

Run:

```powershell
git diff --check
git status --short
git diff --stat main...HEAD
```

If `docs/architecture/vocabulary-deposition.md` changed in Task 6, commit it with:

```powershell
git add docs/architecture/vocabulary-deposition.md
git commit -m "docs(vocabulary): 补充词典笔记解析边界"
```

- [ ] **Step 6: Assess merge readiness**

Confirm the branch contains no unrelated files, the worktree is clean, documentation matches the API, and all verification evidence is current. Because this changes a user-facing flow and adds an API, recommend merging to `main` only after the focused browser acceptance and full backend/frontend verification above succeed.
