package com.personalenglishai.backend.controller.document;

import com.personalenglishai.backend.service.document.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商用级文档 API：创建、版本、获取、软删
 * 租户/工作区从当前用户推导（tenantId=userId，workspaceId=default）
 */
@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateDocRequest body,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        String tenantId = String.valueOf(userId);
        String workspaceId = "default";
        DocumentService.CreateResult r = documentService.createDocument(
                tenantId, workspaceId, userId,
                body.getTitle(), body.getContent());
        return ResponseEntity.ok(Map.of(
                "docId", r.docId,
                "latestRevision", r.latestRevision));
    }

    @PostMapping("/{docId}/revisions")
    public ResponseEntity<Map<String, Integer>> appendRevision(
            @PathVariable String docId,
            @Valid @RequestBody AppendRevisionRequest body,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        String tenantId = String.valueOf(userId);
        DocumentService.AppendResult r = documentService.appendRevision(
                tenantId, "default", docId,
                body.getExpectedLatestRevision(), body.getContent(), userId);
        return ResponseEntity.ok(Map.of("latestRevision", r.latestRevision));
    }

    @GetMapping("/{docId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String docId,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        String tenantId = String.valueOf(userId);
        var docEntity = documentService.findByPublicId(tenantId, "default", docId);
        return documentService.getLatestContent(tenantId, "default", docId, userId)
                .map(doc -> {
                    var resp = new java.util.LinkedHashMap<String, Object>();
                    resp.put("title", doc.title);
                    resp.put("latestRevision", doc.revision);
                    resp.put("content", doc.content);
                    if (docEntity != null) {
                        resp.put("taskPrompt", docEntity.getTaskPrompt());
                        resp.put("submitCount", docEntity.getSubmitCount() != null ? docEntity.getSubmitCount() : 0);
                        resp.put("initialScore", docEntity.getInitialScore());
                        resp.put("latestScore", docEntity.getLatestScore());
                        // mode: 有 taskPrompt 则为 exam，否则 free
                        resp.put("mode", docEntity.getTaskPrompt() != null && !docEntity.getTaskPrompt().isBlank() ? "exam" : "free");
                    }
                    return ResponseEntity.ok((Map<String, Object>) resp);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{docId}/revisions/{rev}")
    public ResponseEntity<Map<String, Object>> getRevision(
            @PathVariable String docId,
            @PathVariable int rev,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        return documentService.getContentByRevision(String.valueOf(userId), "default", docId, rev, userId)
                .map(doc -> ResponseEntity.ok(Map.<String, Object>of(
                        "title", doc.title,
                        "revision", doc.revision,
                        "content", doc.content)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{docId}/title")
    public ResponseEntity<Void> rename(
            @PathVariable String docId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        String title = body.getOrDefault("title", "").trim();
        if (title.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        documentService.updateTitle(String.valueOf(userId), "default", docId, userId, title);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(
            @PathVariable String docId,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        documentService.softDelete(String.valueOf(userId), "default", docId, userId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new IllegalStateException("JWT required");
        }
        return userId;
    }

    public static class CreateDocRequest {
        private String title = "";
        private String content = "";

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class AppendRevisionRequest {
        private int expectedLatestRevision;
        private String content = "";

        public int getExpectedLatestRevision() { return expectedLatestRevision; }
        public void setExpectedLatestRevision(int expectedLatestRevision) { this.expectedLatestRevision = expectedLatestRevision; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
