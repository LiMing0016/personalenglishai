# Vocabulary Search Popover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the vocabulary search page's fixed heading and shortcut cards with a compact search bar whose focus popover contains recent searches and popular suggestions.

**Architecture:** Keep the existing search history and dictionary state in `VocabularyView.vue`. Add one local open/closed state and a focus boundary around the existing search form; render the popover from the existing `recentSearches` and `hotSearches` arrays without adding an API, store, dependency, or persistence key.

**Tech Stack:** Vue 3, TypeScript, Playwright, Vite

## Global Constraints

- Preserve `vocabulary.recentSearches` session storage behavior, deduplication, ordering, and the eight-item limit.
- Suggestion clicks fill the input but do not submit a dictionary lookup.
- Search submit, Escape, or focus leaving the search shell closes the popover.
- Do not modify dictionary APIs, vocabulary card persistence, or the four-tab vocabulary navigation.
- Use existing design tokens and introduce no runtime dependency.

---

### Task 1: Contextual search suggestions

**Files:**
- Modify: `web/tests/vocabularyDepositionFlow.spec.ts`
- Modify: `web/src/views/VocabularyView.vue`

**Interfaces:**
- Consumes: `recentSearches: Ref<string[]>`, `hotSearches: string[]`, `query: Ref<string>`, `clearRecentSearches(): void`, and `submitLookup(): Promise<void>` already owned by `VocabularyView.vue`.
- Produces: an accessible region named `搜索建议`, controlled by the dictionary search input through `aria-expanded` and `aria-controls`.

- [ ] **Step 1: Write the failing browser behavior test**

Add a Playwright test that uses the existing `installApiMocks(page, [])`, provides one dictionary response, and exercises the real page:

```ts
test('keeps recent and popular searches inside the focused search popover', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  await installApiMocks(page, [])
  await page.route('**/api/dictionary/lookup?*', (route) => route.fulfill({
    json: {
      code: '0',
      data: {
        word: 'horizon',
        language: 'en-gb',
        source: 'local',
        phonetics: [{ text: 'həˈraɪzn' }],
        entries: [{
          partOfSpeech: 'noun',
          definitions: ['the line where the sky seems to meet the earth or sea'],
          examples: [],
        }],
        favorite: false,
        lookupCount: 1,
      },
    },
  }))

  await page.goto('/app/vocabulary')
  const search = page.getByRole('searchbox', { name: '输入单词、词组或中文释义' })
  const suggestions = page.getByRole('region', { name: '搜索建议' })

  await expect(page.getByText('查询、学习、一步到位')).toHaveCount(0)
  await expect(page.getByText('热门搜索')).toHaveCount(0)
  await search.focus()
  await expect(suggestions).toBeVisible()
  await suggestions.getByRole('button', { name: 'innovative' }).click()
  await expect(search).toHaveValue('innovative')
  await page.keyboard.press('Escape')
  await expect(suggestions).toHaveCount(0)

  await search.fill('horizon')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await search.focus()
  await expect(suggestions.getByRole('button', { name: 'horizon' })).toBeVisible()
  await suggestions.getByRole('button', { name: '清空' }).click()
  await expect(suggestions.getByRole('button', { name: 'horizon' })).toHaveCount(0)
  await expect(suggestions.getByRole('button', { name: 'innovative' })).toBeVisible()
  await expectCleanRuntime(page, errors)
})
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
npx playwright test tests/vocabularyDepositionFlow.spec.ts -g "keeps recent and popular searches inside the focused search popover"
```

Expected: FAIL because the fixed heading and `热门搜索` card still exist and the `搜索建议` region does not.

- [ ] **Step 3: Add the minimal Vue interaction**

In `VocabularyView.vue`:

- Remove the search page `page-heading` section and the complete `search-meta-grid` section.
- Wrap the dictionary form and new popover in `.dictionary-search-shell`.
- Add `searchSuggestionsOpen = ref(false)`.
- Open only from the text input's focus event.
- Close from form submission, Escape, and focus leaving the shell.
- Add `aria-expanded`, `aria-controls="dictionary-search-suggestions"`, and `aria-haspopup="true"` to the input.
- Render recent searches only when the list is non-empty; always render popular suggestions.
- Keep suggestion actions as `query = item` with no implicit submit.

The new template boundary should follow this structure:

```vue
<div
  class="dictionary-search-shell"
  @focusout="handleSearchShellFocusOut"
  @keydown.esc="closeSearchSuggestions"
>
  <form class="dictionary-search" @submit.prevent="submitLookup">
    <!-- existing search controls -->
  </form>
  <section
    v-if="searchSuggestionsOpen"
    id="dictionary-search-suggestions"
    class="search-suggestions-popover"
    aria-label="搜索建议"
  >
    <!-- recent searches when present, then popular suggestions -->
  </section>
</div>
```

Use `FocusEvent.relatedTarget` to keep the popover open while focus moves to buttons inside the shell:

```ts
function handleSearchShellFocusOut(event: FocusEvent) {
  const nextTarget = event.relatedTarget
  if (nextTarget instanceof Node && (event.currentTarget as HTMLElement).contains(nextTarget)) return
  closeSearchSuggestions()
}
```

- [ ] **Step 4: Add focused popover styling**

Style `.dictionary-search-shell` as a relative, centered, 820px-wide anchor. Remove the form's old top margin. Position `.search-suggestions-popover` absolutely below the search bar with a white surface, `#dce7e1` border, 12px radius, green-tinted shadow, and a z-index above dictionary results. Use compact group headers and pill buttons; preserve the existing one-column responsive search form at 820px and make the popover static on narrow screens to avoid viewport clipping.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the same Playwright command from Step 2. Expected: PASS with no page or console errors.

- [ ] **Step 6: Run regression verification**

Run:

```powershell
npx --yes tsx tests/vocabularyLearningPage.test.ts
npm run build
```

Expected: the learning-page contract test and production build pass. Existing Rollup chunk-size warnings are acceptable; new TypeScript, Vue, or runtime errors are not.

- [ ] **Step 7: Commit the implementation**

```powershell
git add -- web/tests/vocabularyDepositionFlow.spec.ts web/src/views/VocabularyView.vue
git commit -m "feat(ui): 优化单词搜索聚焦浮层"
```

After the commit, verify the 4173 preview manually: page heading and fixed shortcut cards are absent; focusing the input opens the popover without moving the result layout; popular and recent terms fill the input; Escape and clicking outside close it.
