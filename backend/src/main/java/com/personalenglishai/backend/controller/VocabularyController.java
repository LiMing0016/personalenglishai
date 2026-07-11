package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardDetailResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardSummaryResponse;
import com.personalenglishai.backend.dto.vocabulary.UpdateVocabularyCardRequest;
import com.personalenglishai.backend.dto.vocabulary.ResolveVocabularyConflictRequest;
import com.personalenglishai.backend.dto.vocabulary.RegenerateVocabularyCardRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyGenerationJobResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyRevisionListResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateCatalogResponse;
import com.personalenglishai.backend.service.vocabulary.VocabularyCaptureService;
import com.personalenglishai.backend.service.vocabulary.VocabularyCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyController {
    private final VocabularyCaptureService captureService;
    private final VocabularyCardService cardService;

    public VocabularyController(
            VocabularyCaptureService captureService,
            VocabularyCardService cardService) {
        this.captureService = captureService;
        this.cardService = cardService;
    }

    @PostMapping("/captures")
    public ResponseEntity<ApiResponse<VocabularyCaptureResponse>> capture(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody VocabularyCaptureRequest request) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(captureService.capture(userId, request)));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<VocabularyTemplateCatalogResponse>> templates(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(cardService.templateCatalog(userId)));
    }

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<AdminPageResponse<VocabularyCardSummaryResponse>>> cards(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(
                cardService.list(userId, keyword, status, sourceType, sort, page, size)));
    }

    @GetMapping("/cards/{cardUid}")
    public ResponseEntity<ApiResponse<VocabularyCardDetailResponse>> detail(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(cardService.getDetail(userId, cardUid)));
    }

    @PutMapping("/cards/{cardUid}")
    public ResponseEntity<ApiResponse<VocabularyCardDetailResponse>> update(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid,
            @Valid @RequestBody UpdateVocabularyCardRequest request) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(cardService.update(userId, cardUid, request)));
    }

    @DeleteMapping("/cards/{cardUid}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid) {
        if (userId == null) {
            return unauthorized();
        }
        cardService.delete(userId, cardUid);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/cards/{cardUid}/regenerate")
    public ResponseEntity<ApiResponse<VocabularyGenerationJobResponse>> regenerate(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid,
            @Valid @RequestBody(required = false) RegenerateVocabularyCardRequest request) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(cardService.regenerate(
                userId, cardUid, request == null ? null : request.templateKey())));
    }

    @PostMapping("/cards/{cardUid}/retry")
    public ResponseEntity<ApiResponse<VocabularyGenerationJobResponse>> retry(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(cardService.retry(userId, cardUid)));
    }

    @GetMapping("/cards/{cardUid}/revisions")
    public ResponseEntity<ApiResponse<VocabularyRevisionListResponse>> revisions(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(cardService.revisions(userId, cardUid)));
    }

    @PostMapping("/cards/{cardUid}/conflicts/{revisionUid}/resolve")
    public ResponseEntity<ApiResponse<VocabularyCardDetailResponse>> resolveConflict(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String cardUid,
            @PathVariable String revisionUid,
            @Valid @RequestBody ResolveVocabularyConflictRequest request) {
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(
                cardService.resolveConflict(userId, cardUid, revisionUid, request)));
    }

    private <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("401001", "Unauthorized"));
    }
}
