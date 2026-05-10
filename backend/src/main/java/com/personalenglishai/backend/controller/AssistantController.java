package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationDetailResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationSummaryResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRequest;
import com.personalenglishai.backend.controller.dto.assistant.AssistantProjectRequest;
import com.personalenglishai.backend.controller.dto.assistant.AssistantProjectResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantShareResponse;
import com.personalenglishai.backend.controller.dto.assistant.CreateAssistantConversationRequest;
import com.personalenglishai.backend.controller.dto.assistant.MoveAssistantConversationRequest;
import com.personalenglishai.backend.controller.dto.assistant.SendAssistantMessageRequest;
import com.personalenglishai.backend.controller.dto.assistant.SetPinnedAssistantConversationRequest;
import com.personalenglishai.backend.controller.dto.assistant.UpdateAssistantConversationRequest;
import com.personalenglishai.backend.service.assistant.AssistantConversationService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantConversationService assistantConversationService;

    public AssistantController(AssistantConversationService assistantConversationService) {
        this.assistantConversationService = assistantConversationService;
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<AssistantProjectResponse>>> listProjects(@RequestAttribute("userId") Long userId) {
        return ok(assistantConversationService.listProjects(userId));
    }

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<AssistantProjectResponse>> createProject(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AssistantProjectRequest request) {
        return ok(assistantConversationService.createProject(userId, request));
    }

    @PatchMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<AssistantProjectResponse>> updateProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody AssistantProjectRequest request) {
        return ok(assistantConversationService.updateProject(userId, projectId, request));
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        assistantConversationService.deleteProject(userId, projectId);
        return ok(null);
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<AssistantConversationSummaryResponse>>> listConversations(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Long projectId) {
        return ok(assistantConversationService.listConversations(userId, archived, projectId));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<AssistantConversationDetailResponse>> createConversation(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CreateAssistantConversationRequest request) {
        return ok(assistantConversationService.createConversation(userId, request));
    }

    @GetMapping("/conversations/{conversationUid}")
    public ResponseEntity<ApiResponse<AssistantConversationDetailResponse>> getConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid) {
        return ok(assistantConversationService.getConversation(userId, conversationUid));
    }

    @PatchMapping("/conversations/{conversationUid}")
    public ResponseEntity<ApiResponse<AssistantConversationDetailResponse>> updateConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid,
            @Valid @RequestBody UpdateAssistantConversationRequest request) {
        return ok(assistantConversationService.updateConversation(userId, conversationUid, request));
    }

    @PostMapping(value = "/conversations/{conversationUid}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AssistantConversationDetailResponse>> sendMessage(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid,
            @Valid @RequestBody SendAssistantMessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ok(assistantConversationService.sendMessage(userId, conversationUid, request, authorization));
    }

    @PostMapping(value = "/conversations/{conversationUid}/messages/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AssistantConversationDetailResponse>> sendAgentMessage(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid,
            @Valid @RequestBody AssistantRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ok(assistantConversationService.sendAgentMessage(userId, conversationUid, request, authorization));
    }

    @PostMapping(
            value = "/conversations/{conversationUid}/messages/run/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamAgentMessage(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid,
            @Valid @RequestBody AssistantRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StreamingResponseBody body = outputStream ->
                assistantConversationService.writeAgentMessageStream(
                        userId,
                        conversationUid,
                        request,
                        authorization,
                        outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    @PostMapping(value = "/conversations/{conversationUid}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssistantConversationDetailResponse>> sendMessageWithFiles(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid,
            @RequestParam(defaultValue = "") String message,
            @RequestParam(required = false) String studyStage,
            @RequestParam(required = false) String assistantMode,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        SendAssistantMessageRequest request = new SendAssistantMessageRequest();
        request.setMessage(message);
        request.setStudyStage(studyStage);
        request.setAssistantMode(assistantMode);
        return ok(assistantConversationService.sendMessageWithFiles(
                userId,
                conversationUid,
                request,
                files == null ? Collections.emptyList() : files,
                authorization));
    }

    @PostMapping("/conversations/{conversationUid}/archive")
    public ResponseEntity<ApiResponse<AssistantConversationSummaryResponse>> archiveConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid) {
        return ok(assistantConversationService.archiveConversation(userId, conversationUid));
    }

    @PostMapping("/conversations/{conversationUid}/restore")
    public ResponseEntity<ApiResponse<AssistantConversationSummaryResponse>> restoreConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid) {
        return ok(assistantConversationService.restoreConversation(userId, conversationUid));
    }

    @PostMapping("/conversations/{conversationUid}/pin")
    public ResponseEntity<ApiResponse<AssistantConversationSummaryResponse>> setPinned(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid,
            @RequestBody SetPinnedAssistantConversationRequest request) {
        return ok(assistantConversationService.setPinned(userId, conversationUid, request.isPinned()));
    }

    @PostMapping("/conversations/{conversationUid}/move")
    public ResponseEntity<ApiResponse<AssistantConversationSummaryResponse>> moveConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid,
            @RequestBody MoveAssistantConversationRequest request) {
        return ok(assistantConversationService.moveConversation(userId, conversationUid, request));
    }

    @DeleteMapping("/conversations/{conversationUid}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid) {
        assistantConversationService.deleteConversation(userId, conversationUid);
        return ok(null);
    }

    @PostMapping("/conversations/{conversationUid}/share")
    public ResponseEntity<ApiResponse<AssistantShareResponse>> shareConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable String conversationUid) {
        return ok(assistantConversationService.shareConversation(userId, conversationUid));
    }

    @DeleteMapping("/shares/{shareToken}")
    public ResponseEntity<ApiResponse<Void>> revokeShare(
            @RequestAttribute("userId") Long userId,
            @PathVariable String shareToken) {
        assistantConversationService.revokeShare(userId, shareToken);
        return ok(null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        ApiResponse<T> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }
}
