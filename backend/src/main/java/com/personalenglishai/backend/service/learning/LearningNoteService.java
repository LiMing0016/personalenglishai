package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.learning.LearningNoteRequest;
import com.personalenglishai.backend.dto.learning.LearningNoteResponse;
import com.personalenglishai.backend.entity.learning.LearningNote;
import com.personalenglishai.backend.mapper.learning.LearningNoteMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LearningNoteService {
    private static final Set<String> SUPPORTED_TYPES = Set.of("vocabulary", "sentence", "grammar", "expression");

    private final LearningNoteMapper mapper;

    public LearningNoteService(LearningNoteMapper mapper) {
        this.mapper = mapper;
    }

    public LearningNoteResponse create(Long userId, LearningNoteRequest request) {
        validateUser(userId);
        LearningNote note = buildNote(userId, createNoteUid(), request);
        mapper.insert(note);
        LearningNote stored = mapper.selectByUidForUser(userId, note.getNoteUid());
        return toResponse(stored == null ? note : stored);
    }

    public LearningNoteResponse update(Long userId, String noteUid, LearningNoteRequest request) {
        validateUser(userId);
        if (noteUid == null || noteUid.isBlank()) {
            throw new IllegalArgumentException("noteUid required");
        }
        LearningNote note = buildNote(userId, noteUid.trim(), request);
        mapper.updateForUser(note);
        LearningNote stored = mapper.selectByUidForUser(userId, note.getNoteUid());
        if (stored == null) {
            throw new IllegalArgumentException("learning note not found");
        }
        return toResponse(stored);
    }

    public LearningNoteResponse get(Long userId, String noteUid) {
        validateUser(userId);
        LearningNote stored = mapper.selectByUidForUser(userId, trimRequired(noteUid, "noteUid"));
        if (stored == null) {
            throw new IllegalArgumentException("learning note not found");
        }
        return toResponse(stored);
    }

    public AdminPageResponse<LearningNoteResponse> list(Long userId, String type, Integer page, Integer size) {
        validateUser(userId);
        String normalizedType = normalizeType(type);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 20));
        int offset = (safePage - 1) * safeSize;
        List<LearningNoteResponse> items = mapper.selectByUserAndType(userId, normalizedType, offset, safeSize)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = mapper.countByUserAndType(userId, normalizedType);
        return new AdminPageResponse<>(items, total, safePage, safeSize);
    }

    public void delete(Long userId, String noteUid) {
        validateUser(userId);
        mapper.softDelete(userId, trimRequired(noteUid, "noteUid"));
    }

    String createNoteUid() {
        return "note-" + UUID.randomUUID().toString().replace("-", "");
    }

    private LearningNote buildNote(Long userId, String noteUid, LearningNoteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request required");
        }
        LearningNote note = new LearningNote();
        note.setNoteUid(noteUid);
        note.setUserId(userId);
        note.setType(normalizeType(request.getType()));
        note.setTitle(trimRequired(request.getTitle(), "title"));
        note.setContentMarkdown(trimRequired(request.getContentMarkdown(), "contentMarkdown"));
        note.setStructuredPayload(trimOptional(request.getStructuredPayload()));
        note.setSourceConversationUid(trimOptional(request.getSourceConversationId()));
        note.setSourceMessageUid(trimOptional(request.getSourceMessageId()));
        note.setSourceText(trimOptional(request.getSourceText()));
        note.setStatus("active");
        return note;
    }

    private LearningNoteResponse toResponse(LearningNote note) {
        LearningNoteResponse response = new LearningNoteResponse();
        response.setNoteUid(note.getNoteUid());
        response.setType(note.getType());
        response.setTitle(note.getTitle());
        response.setContentMarkdown(note.getContentMarkdown());
        response.setStructuredPayload(note.getStructuredPayload());
        response.setSourceConversationId(note.getSourceConversationUid());
        response.setSourceMessageId(note.getSourceMessageUid());
        response.setSourceText(note.getSourceText());
        response.setStatus(note.getStatus());
        response.setCreatedAt(note.getCreatedAt());
        response.setUpdatedAt(note.getUpdatedAt());
        return response;
    }

    private void validateUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("invalid user");
        }
    }

    private String normalizeType(String value) {
        String normalized = value == null ? "vocabulary" : value.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("invalid type");
        }
        return normalized;
    }

    private String trimRequired(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " required");
        }
        return trimmed;
    }

    private String trimOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
