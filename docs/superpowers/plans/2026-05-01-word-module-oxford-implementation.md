# Word Module Oxford Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first version of the vocabulary page backed by Oxford Dictionaries through a backend proxy.

**Architecture:** The backend owns Oxford credentials, calls the Oxford Words endpoint, normalizes the response into project DTOs, and exposes `/api/dictionary/lookup`. The frontend adds a typed dictionary API wrapper and turns `/app/vocabulary` into a workbench-style lookup page with language switching and expandable results.

**Tech Stack:** Spring Boot 3, Java 17, Jackson, `java.net.http.HttpClient`, Vue 3, TypeScript, Axios, Vite.

---

### Task 1: Backend DTO, Parser, and Service Tests

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/dictionary/DictionaryLookupResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/dictionary/DictionaryEntryDto.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/dictionary/DictionaryPhoneticDto.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/dictionary/impl/OxfordDictionaryResponseParser.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/dictionary/OxfordDictionaryResponseParserTest.java`

- [ ] Write failing parser tests for phonetics, audio, entries, definitions, examples, and missing optional fields.
- [ ] Run the parser test and verify it fails because the parser does not exist.
- [ ] Implement minimal DTOs and parser logic.
- [ ] Run the parser test and verify it passes.

### Task 2: Backend Oxford Client and Controller

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/config/OxfordDictionaryProperties.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/dictionary/DictionaryLookupService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/dictionary/DictionaryLookupException.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/dictionary/impl/OxfordDictionaryService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/controller/DictionaryController.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/.env.example`
- Test: `backend/src/test/java/com/personalenglishai/backend/controller/DictionaryControllerTest.java`

- [ ] Write failing controller tests for empty word and successful lookup through a mocked service.
- [ ] Run the controller test and verify it fails because the controller does not exist.
- [ ] Implement config, service interface, controller, and exception mapping.
- [ ] Run controller tests and verify they pass.

### Task 3: Frontend API and Vocabulary Page Tests

**Files:**
- Create: `web/src/api/dictionary.ts`
- Modify: `web/src/views/VocabularyView.vue`
- Test: `web/tests/vocabularyDictionaryPage.test.ts`

- [ ] Write a failing source-level test for API wrapper use, language selector, expandable results, and no learning action buttons.
- [ ] Run the frontend test and verify it fails against the current placeholder page.
- [ ] Implement the API wrapper and page.
- [ ] Run the frontend test and verify it passes.

### Task 4: Full Verification

**Commands:**
- `cd backend && ./mvnw.cmd test`
- `cd web && node tests/vocabularyDictionaryPage.test.ts`
- `cd web && npm run build`

- [ ] Run backend tests.
- [ ] Run frontend source test.
- [ ] Run frontend build.
- [ ] Commit the implementation with `feat(word): 接入牛津词典查词页`.
