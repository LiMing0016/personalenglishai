# Local PaddleOCR-VL Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional local PaddleOCR-VL parsing path for high-quality PDF uploads while keeping local PPStructureV3 as the stable fallback.

**Architecture:** The backend adds a `local-paddle-vl` `DocumentParseProvider` that calls a local HTTP service. The existing PaddleOCR service exposes a `/vl/pdf` endpoint backed by a lazy-loaded local PaddleOCR-VL engine; if it is disabled or unavailable, the orchestrator falls back to the existing local `paddle-ocr` provider.

**Tech Stack:** Spring Boot, Java `HttpClient`, Jackson, FastAPI, Pydantic, PaddleOCR.

---

### Task 1: Backend Provider Ordering

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/translation/DocumentParseProviderType.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/translation/DocumentParseOrchestrator.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/translation/DocumentParseOrchestratorTest.java`

- [ ] Add `LOCAL_PADDLE_VL("local-paddle-vl")`.
- [ ] Write tests showing `high_quality` tries `local-paddle-vl` before `paddle-ocr`.
- [ ] Write tests showing local VL failure falls back to `paddle-ocr` with a warning.
- [ ] Keep `standard` on local `paddle-ocr`.

### Task 2: Backend HTTP Provider

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/translation/LocalPaddleVlDocumentParseProvider.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/translation/LocalPaddleVlDocumentParseProviderTest.java`
- Modify: `docker-compose.local.yml`

- [ ] Write an HTTP contract test using a fake local server.
- [ ] Send `documentBase64`, `language`, `parseMode=high_quality`, `maxPages`, `dpi`, and feature flags to `/vl/pdf`.
- [ ] Parse the same `OcrResponse` shape used by `/ocr/pdf`.
- [ ] Enable via `APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLED=false` by default.
- [ ] Default local URL should be Docker-friendly: `http://host.docker.internal:8091`.

### Task 3: Python OCR Service VL Endpoint

**Files:**
- Create: `services/paddle-ocr/app/vl_engine.py`
- Modify: `services/paddle-ocr/app/main.py`
- Modify: `services/paddle-ocr/app/schemas.py`
- Test: `services/paddle-ocr/tests/test_api_contract.py`

- [ ] Add `PaddleVlDocumentEngine` with lazy loading and safe unavailable state.
- [ ] Add `/vl/pdf` using the same `OcrPdfRequest` and `OcrResponse` contract.
- [ ] Normalize PaddleOCR-VL results into `OcrPage.elements`, preserving markdown text when available.
- [ ] Return a normal `FAILED` response when VL is disabled or unavailable.

### Task 4: Documentation And Verification

**Files:**
- Modify: `services/paddle-ocr/README.md`
- Modify: `docs/architecture/PaddleOCR高质量文档解析方案.md`

- [ ] Document local-only defaults and local VL env vars.
- [ ] Run targeted backend tests.
- [ ] Run Python service tests.
- [ ] Run full backend test suite if targeted tests pass.
