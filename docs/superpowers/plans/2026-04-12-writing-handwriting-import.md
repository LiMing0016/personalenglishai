# Writing Handwriting Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a handwriting-image import flow to the writing page so users can upload one handwritten essay image, run AI recognition, preview the text, and then replace or append into the current editor while binding the latest handwritten source to the current writing document.

**Architecture:** Keep the writing-page draft as the single source of truth. `EditorShell` only opens a dedicated dialog component and applies the confirmed import result. The backend exposes one recognition endpoint and one metadata-binding update path, reusing the existing writing metadata/document session chain and current AI provider capability instead of introducing a separate OCR subsystem.

**Tech Stack:** Vue 3, TypeScript, Pinia, Spring Boot, MyBatis, MySQL, existing OpenAI/Kimi/Qwen provider abstraction.

---

## File Structure

### Frontend files

- Modify: `web/src/components/writing/EditorShell.vue`
  - Add `...` menu item entry point
  - Open/close the handwriting import dialog
  - Apply confirmed `replace` / `append` text into `draftStore.draftText`
- Create: `web/src/components/writing/HandwritingImportDialog.vue`
  - Own file selection, preview, recognition request, result view, confirm actions
- Modify: `web/src/api/writing.ts`
  - Add handwriting recognition request/response types and API methods
  - Add metadata-binding update API if a dedicated endpoint is used
- Modify: `web/src/stores/writingDraftStore.ts`
  - Only if needed for a narrow helper to apply imported text or refresh doc-scoped metadata
- Test: `web/tests/handwritingImportDialog.test.ts`
  - Dialog state transitions
  - Confirm action payloads
- Test: `web/tests/writingEditorHandwritingImport.test.ts`
  - Editor integration: replace / append / cancel behavior

### Backend files

- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java`
  - Add handwriting recognition endpoint
  - Add metadata binding/update endpoint if not folded into an existing write path
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/RecognizeHandwritingImageRequest.java`
  - Request DTO for one base64 image payload plus provider selection
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/RecognizeHandwritingImageResponse.java`
  - Structured result DTO with source image, raw text, normalized text, confidence
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/BindHandwritingImportRequest.java`
  - Bind latest handwritten source to current document after user confirms import
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/WritingSessionMetadataResponse.java`
  - Return latest handwritten source metadata
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/WritingMetadata.java`
  - Add handwritten source fields
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/HandwritingRecognitionService.java`
  - Dedicated service boundary for handwriting recognition
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/HandwritingRecognitionServiceImpl.java`
  - Prompt assembly and multimodal provider call
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/document/DocumentService.java`
  - Add narrow method to persist latest handwritten source metadata for one document
- Modify: `backend/src/main/resources/mapper/WritingMetadataMapper.xml`
  - Read/write handwritten metadata fields
- Modify or create: `backend/src/main/resources/db/schema.sql`
  - Add new columns to `writing_metadata`
- Create: `backend/src/main/resources/db/migrate_add_handwriting_import_fields.sql`
  - Local migration for the new columns

### Backend tests

- Create: `backend/src/test/java/com/personalenglishai/backend/service/writing/impl/HandwritingRecognitionServiceImplTest.java`
  - Prompt/output parsing tests
- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`
  - Endpoint tests for recognition and binding
- Modify or create: `backend/src/test/java/com/personalenglishai/backend/service/document/DocumentServiceTest.java`
  - Binding latest handwritten metadata to the current doc
- Modify: `backend/src/test/java/com/personalenglishai/backend/db/WritingPromptSheetSchemaTest.java`
  - Or add a new schema test to assert handwritten columns exist

## Task 1: Define backend DTOs and metadata contract

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/RecognizeHandwritingImageRequest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/RecognizeHandwritingImageResponse.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/dto/writing/BindHandwritingImportRequest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/dto/writing/WritingSessionMetadataResponse.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`

- [ ] **Step 1: Write the failing controller test for the new request/response shape**

```java
@Test
void recognizeHandwritingImageShouldReturnStructuredResult() throws Exception {
    when(handwritingRecognitionService.recognize(any()))
        .thenReturn(new RecognizeHandwritingImageResponse(
            "data:image/png;base64,abc",
            "raw line 1",
            "normalized paragraph 1",
            new BigDecimal("0.82")
        ));

    mockMvc.perform(post("/api/writing/recognize-handwriting-image")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"imageBase64":"data:image/png;base64,abc","aiProvider":"openai"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.normalizedText").value("normalized paragraph 1"));
}
```

- [ ] **Step 2: Run the narrow controller test to verify it fails**

Run: `./mvnw.cmd -q "-Dtest=WritingControllerTest" test`

Expected: FAIL because the endpoint and DTOs do not exist yet.

- [ ] **Step 3: Add the request/response DTOs and expose the fields on `WritingSessionMetadataResponse`**

```java
public class RecognizeHandwritingImageRequest {
    @NotBlank
    private String imageBase64;
    private String aiProvider;
}
```

```java
public class RecognizeHandwritingImageResponse {
    private String imageUrl;
    private String recognizedText;
    private String normalizedText;
    private BigDecimal confidence;
}
```

- [ ] **Step 4: Re-run the narrow controller test**

Run: `./mvnw.cmd -q "-Dtest=WritingControllerTest" test`

Expected: still FAIL, but now at missing service/controller wiring instead of DTO compilation.

- [ ] **Step 5: Commit the DTO contract**

```bash
git add backend/src/main/java/com/personalenglishai/backend/dto/writing/RecognizeHandwritingImageRequest.java backend/src/main/java/com/personalenglishai/backend/dto/writing/RecognizeHandwritingImageResponse.java backend/src/main/java/com/personalenglishai/backend/dto/writing/BindHandwritingImportRequest.java backend/src/main/java/com/personalenglishai/backend/dto/writing/WritingSessionMetadataResponse.java backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java
git commit -m "feat(writing): 定义手写导入接口契约"
```

## Task 2: Implement backend handwriting recognition service and controller endpoint

**Files:**
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/HandwritingRecognitionService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/writing/impl/HandwritingRecognitionServiceImpl.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/writing/impl/HandwritingRecognitionServiceImplTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java`

- [ ] **Step 1: Write the failing service test for prompt/output parsing**

```java
@Test
void recognizeShouldNormalizeParagraphsFromMultimodalResponse() {
    when(openAiClient.callWithProvider(eq("openai"), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("""
            {"recognizedText":"Line 1\\nLine 2","normalizedText":"Line 1\\n\\nLine 2","confidence":0.81}
            """);

    var response = service.recognize(request("data:image/png;base64,abc"));

    assertThat(response.getNormalizedText()).isEqualTo("Line 1\n\nLine 2");
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run: `./mvnw.cmd -q "-Dtest=HandwritingRecognitionServiceImplTest" test`

Expected: FAIL because the service does not exist yet.

- [ ] **Step 3: Implement the minimal service and controller**

```java
public interface HandwritingRecognitionService {
    RecognizeHandwritingImageResponse recognize(RecognizeHandwritingImageRequest request);
}
```

```java
@PostMapping("/recognize-handwriting-image")
public ResponseEntity<RecognizeHandwritingImageResponse> recognizeHandwritingImage(
        @Valid @RequestBody RecognizeHandwritingImageRequest request) {
    return ResponseEntity.ok(handwritingRecognitionService.recognize(request));
}
```

Prompt rules in the implementation:

- “只提取手写英文作文正文”
- “保留自然段”
- “不要补写缺失内容”
- “不要输出解释或 markdown”

- [ ] **Step 4: Re-run service + controller tests**

Run: `./mvnw.cmd -q "-Dtest=HandwritingRecognitionServiceImplTest,WritingControllerTest" test`

Expected: PASS for the new recognition path.

- [ ] **Step 5: Commit the backend recognition path**

```bash
git add backend/src/main/java/com/personalenglishai/backend/service/writing/HandwritingRecognitionService.java backend/src/main/java/com/personalenglishai/backend/service/writing/impl/HandwritingRecognitionServiceImpl.java backend/src/main/java/com/personalenglishai/backend/controller/WritingController.java backend/src/test/java/com/personalenglishai/backend/service/writing/impl/HandwritingRecognitionServiceImplTest.java backend/src/test/java/com/personalenglishai/backend/controller/WritingControllerTest.java
git commit -m "feat(writing): 增加手写作文识别接口"
```

## Task 3: Persist the latest handwritten source on the current writing document

**Files:**
- Modify: `backend/src/main/java/com/personalenglishai/backend/entity/WritingMetadata.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/service/document/DocumentService.java`
- Modify: `backend/src/main/resources/mapper/WritingMetadataMapper.xml`
- Modify: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/main/resources/db/migrate_add_handwriting_import_fields.sql`
- Test: `backend/src/test/java/com/personalenglishai/backend/service/document/DocumentServiceTest.java`
- Test: `backend/src/test/java/com/personalenglishai/backend/db/WritingPromptSheetSchemaTest.java`

- [ ] **Step 1: Write the failing document-service test for binding the latest handwritten source**

```java
@Test
void bindHandwritingImportShouldUpdateLatestHandwrittenFields() {
    documentService.bindHandwritingImport("1", "default", "doc_x", 1L,
        "image", "data:image/png;base64,abc", "recognized body");

    verify(writingMetadataMapper).updateHandwritingImportByDocumentId(argThat(meta ->
        "image".equals(meta.getHandwrittenSourceType())
            && "recognized body".equals(meta.getHandwrittenRecognizedText())
    ));
}
```

- [ ] **Step 2: Run the document-service test to verify it fails**

Run: `./mvnw.cmd -q "-Dtest=DocumentServiceTest" test`

Expected: FAIL because the fields and method do not exist.

- [ ] **Step 3: Add metadata columns and service update path**

Recommended new columns on `writing_metadata`:

```sql
ALTER TABLE writing_metadata
  ADD COLUMN handwritten_source_type VARCHAR(16) NULL,
  ADD COLUMN handwritten_source_image_url LONGTEXT NULL,
  ADD COLUMN handwritten_recognized_text LONGTEXT NULL,
  ADD COLUMN handwritten_imported_at DATETIME NULL;
```

Minimal service boundary:

```java
public void bindHandwritingImport(String tenantId, String workspaceId, String publicDocId, Long ownerUserId,
                                  String sourceType, String imageUrl, String recognizedText) { ... }
```

- [ ] **Step 4: Re-run document and schema tests**

Run: `./mvnw.cmd -q "-Dtest=DocumentServiceTest,WritingPromptSheetSchemaTest" test`

Expected: PASS and confirm new metadata fields are queryable.

- [ ] **Step 5: Commit metadata persistence**

```bash
git add backend/src/main/java/com/personalenglishai/backend/entity/WritingMetadata.java backend/src/main/java/com/personalenglishai/backend/service/document/DocumentService.java backend/src/main/resources/mapper/WritingMetadataMapper.xml backend/src/main/resources/db/schema.sql backend/src/main/resources/db/migrate_add_handwriting_import_fields.sql backend/src/test/java/com/personalenglishai/backend/service/document/DocumentServiceTest.java backend/src/test/java/com/personalenglishai/backend/db/WritingPromptSheetSchemaTest.java
git commit -m "feat(writing): 绑定手写导入文档元数据"
```

## Task 4: Expose frontend API methods and build the dialog component

**Files:**
- Modify: `web/src/api/writing.ts`
- Create: `web/src/components/writing/HandwritingImportDialog.vue`
- Test: `web/tests/handwritingImportDialog.test.ts`

- [ ] **Step 1: Write the failing dialog test for state transitions**

```ts
it('emits replace and append actions after recognition success', async () => {
  render(HandwritingImportDialog, { props: { visible: true } })
  await user.upload(screen.getByLabelText('上传图片'), file)
  await user.click(screen.getByRole('button', { name: '开始识别' }))
  expect(await screen.findByText('recognized paragraph')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '替换写作区' }))
  expect(emitted().confirm?.[0]?.[0]).toMatchObject({ mode: 'replace' })
})
```

- [ ] **Step 2: Run the frontend dialog test to verify it fails**

Run: `cd web && npm test -- handwritingImportDialog`

Expected: FAIL because the component and API methods do not exist.

- [ ] **Step 3: Implement the API client and dialog**

Suggested API surface in `web/src/api/writing.ts`:

```ts
export interface RecognizeHandwritingImageRequest {
  imageBase64: string
  aiProvider?: WritingAiProvider
}

export interface RecognizeHandwritingImageResponse {
  imageUrl: string | null
  recognizedText: string | null
  normalizedText: string | null
  confidence?: number | null
}
```

Dialog emits:

```ts
type HandwritingImportConfirmPayload = {
  mode: 'replace' | 'append'
  imageUrl: string | null
  recognizedText: string
  normalizedText: string
}
```

- [ ] **Step 4: Re-run the dialog test**

Run: `cd web && npm test -- handwritingImportDialog`

Expected: PASS for upload, recognize, cancel, replace, append.

- [ ] **Step 5: Commit the frontend dialog**

```bash
git add web/src/api/writing.ts web/src/components/writing/HandwritingImportDialog.vue web/tests/handwritingImportDialog.test.ts
git commit -m "feat(writing): 增加手写作文导入弹层"
```

## Task 5: Integrate the dialog into `EditorShell` and bind confirmation to the current draft/doc

**Files:**
- Modify: `web/src/components/writing/EditorShell.vue`
- Modify: `web/src/stores/writingDraftStore.ts`
- Test: `web/tests/writingEditorHandwritingImport.test.ts`

- [ ] **Step 1: Write the failing integration test for replace/append**

```ts
it('replaces or appends draft text after confirmation', async () => {
  draftStore.draftText = 'Existing body'
  render(EditorShell, props)
  await user.click(screen.getByRole('button', { name: '更多' }))
  await user.click(screen.getByText('上传手写作文'))
  dialogEmitConfirm({
    mode: 'append',
    imageUrl: 'data:image/png;base64,abc',
    recognizedText: 'raw',
    normalizedText: 'Imported body',
  })
  expect(draftStore.draftText).toContain('Existing body')
  expect(draftStore.draftText).toContain('Imported body')
})
```

- [ ] **Step 2: Run the integration test to verify it fails**

Run: `cd web && npm test -- writingEditorHandwritingImport`

Expected: FAIL because `EditorShell` does not yet host the dialog flow.

- [ ] **Step 3: Wire `EditorShell` to the dialog and backend metadata binding**

Integration rules:

- Add `上传手写作文` to the existing `...` menu
- On confirm `replace`: `draftStore.draftText = normalizedText`
- On confirm `append`: append with paragraph spacing if needed
- After confirm, call the metadata-binding API with:
  - current `docId`
  - `imageUrl`
  - `recognizedText`
  - fixed source type `image`

- [ ] **Step 4: Re-run the integration test**

Run: `cd web && npm test -- writingEditorHandwritingImport`

Expected: PASS for replace, append, cancel, and non-destructive failure behavior.

- [ ] **Step 5: Commit the editor integration**

```bash
git add web/src/components/writing/EditorShell.vue web/src/stores/writingDraftStore.ts web/tests/writingEditorHandwritingImport.test.ts
git commit -m "feat(writing): 接入手写作文导入写作区"
```

## Task 6: Verify end-to-end behavior and update docs if needed

**Files:**
- Modify if needed: `docs/writing-ai-exam-prompt-api.md`
- Optional: add a short note to a writing API doc if the new handwriting endpoints need external documentation

- [ ] **Step 1: Run backend targeted tests**

Run: `./mvnw.cmd -q "-Dtest=WritingControllerTest,DocumentServiceTest,HandwritingRecognitionServiceImplTest,WritingPromptSheetSchemaTest" test`

Expected: PASS

- [ ] **Step 2: Run frontend targeted tests**

Run: `cd web && npm test -- handwritingImportDialog writingEditorHandwritingImport`

Expected: PASS

- [ ] **Step 3: Run broad verification**

Run: `./mvnw.cmd test`

Run: `cd web && npm run build`

Expected: backend tests green, frontend build successful

- [ ] **Step 4: Manual regression**

Check:

- `...` 菜单能打开手写导入入口
- 上传一张图片后能看到预览
- 识别成功后能选择替换/追加
- 导入后刷新页面，草稿仍在
- 当前文档元数据重新读取时能看到最新手写来源字段
- 不影响考试模式提交评分

- [ ] **Step 5: Commit final polish/docs**

```bash
git add docs
git commit -m "docs(writing): 更新手写导入接口说明"
```

