package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeResponse;
import com.personalenglishai.backend.dto.learning.LearningNoteRequest;
import com.personalenglishai.backend.dto.learning.LearningNoteResponse;
import com.personalenglishai.backend.service.learning.LearningCanvasOrganizeService;
import com.personalenglishai.backend.service.learning.LearningNoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning-notes")
public class LearningNoteController {
    private final LearningNoteService learningNoteService;
    private final LearningCanvasOrganizeService organizeService;

    public LearningNoteController(
            LearningNoteService learningNoteService,
            LearningCanvasOrganizeService organizeService) {
        this.learningNoteService = learningNoteService;
        this.organizeService = organizeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LearningNoteResponse>> create(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody LearningNoteRequest request) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(learningNoteService.create(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminPageResponse<LearningNoteResponse>>> list(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(required = false, defaultValue = "vocabulary") String type,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("401000", "请先登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(learningNoteService.list(userId, type, page, size)));
    }

    @GetMapping("/{noteUid}")
    public ResponseEntity<ApiResponse<LearningNoteResponse>> get(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String noteUid) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(learningNoteService.get(userId, noteUid)));
    }

    @PutMapping("/{noteUid}")
    public ResponseEntity<ApiResponse<LearningNoteResponse>> update(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String noteUid,
            @RequestBody LearningNoteRequest request) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(learningNoteService.update(userId, noteUid, request)));
    }

    @DeleteMapping("/{noteUid}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String noteUid) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("401000", "请先登录"));
        }
        learningNoteService.delete(userId, noteUid);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @PostMapping("/organize")
    public ResponseEntity<ApiResponse<LearningCanvasOrganizeResponse>> organize(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody LearningCanvasOrganizeRequest request) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("401000", "请先登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(organizeService.organize(request)));
    }

    private <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("401000", "请先登录"));
    }
}
