package com.personalenglishai.backend.controller.document;

import com.personalenglishai.backend.dto.writing.WritingTaskMetadataResponse;
import com.personalenglishai.backend.entity.WritingTaskMetadata;
import com.personalenglishai.backend.service.writing.WritingTaskMetadataService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/docs/{docId}/writing-task-metadata")
public class WritingTaskMetadataController {
    private final WritingTaskMetadataService writingTaskMetadataService;

    public WritingTaskMetadataController(WritingTaskMetadataService writingTaskMetadataService) {
        this.writingTaskMetadataService = writingTaskMetadataService;
    }

    @PostMapping
    public ResponseEntity<WritingTaskMetadataResponse> ensure(
            @PathVariable String docId,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);
        WritingTaskMetadata metadata = writingTaskMetadataService.ensureForDocument(
                String.valueOf(userId),
                "default",
                docId,
                userId
        );
        return ResponseEntity.ok(WritingTaskMetadataResponse.from(metadata));
    }

    private Long requireUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new IllegalStateException("JWT required");
        }
        return userId;
    }
}
