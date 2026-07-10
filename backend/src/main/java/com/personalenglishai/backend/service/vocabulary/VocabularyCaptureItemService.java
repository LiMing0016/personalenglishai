package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class VocabularyCaptureItemService {
    static final int MAX_CONTEXT_LENGTH = 2_000;

    private final VocabularyCardMapper cards;
    private final VocabularySourceMapper sources;
    private final VocabularyGenerationJobMapper jobs;
    private final UserVocabularyPreferenceMapper preferences;
    private final VocabularyTermNormalizer termNormalizer;
    private final VocabularyTemplateRegistry templateRegistry;
    private final ObjectMapper objectMapper;

    public VocabularyCaptureItemService(
            VocabularyCardMapper cards,
            VocabularySourceMapper sources,
            VocabularyGenerationJobMapper jobs,
            UserVocabularyPreferenceMapper preferences,
            VocabularyTermNormalizer termNormalizer,
            VocabularyTemplateRegistry templateRegistry,
            ObjectMapper objectMapper) {
        this.cards = cards;
        this.sources = sources;
        this.jobs = jobs;
        this.preferences = preferences;
        this.termNormalizer = termNormalizer;
        this.templateRegistry = templateRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public VocabularyCaptureResponse.Item captureOne(
            Long userId,
            VocabularyCaptureRequest request,
            int index) {
        String rawTerm = request.terms().get(index);
        String idempotencyKey = request.clientRequestId() + ":" + index;
        VocabularyCardSource existingSource = sources.findSourceByIdempotencyKey(userId, idempotencyKey);
        if (existingSource != null) {
            VocabularyCard existingCard = cards.findByUidIncludingDeleted(existingSource.getCardUid());
            String status = existingCard == null ? "captured" : existingCard.getStatus();
            return new VocabularyCaptureResponse.Item(
                    rawTerm, existingSource.getCardUid(), "source_merged", status);
        }

        VocabularyTemplateRegistry.TemplateDefinition selectedTemplate = resolveTemplate(userId, request.templateKey());
        String normalizedTerm = termNormalizer.normalize(rawTerm);
        boolean reviewRequired = termNormalizer.isReviewRequired(rawTerm, normalizedTerm);
        String language = canonicalLanguage(request.language());
        LocalDateTime capturedAt = LocalDateTime.now();

        VocabularyCard card = cards.findByIdentityIncludingDeleted(userId, language, normalizedTerm);
        boolean created = false;
        boolean restored = false;
        if (card == null) {
            card = newCard(
                    userId, rawTerm, normalizedTerm, language, selectedTemplate, reviewRequired, capturedAt);
            try {
                cards.insert(card);
                created = true;
            } catch (DuplicateKeyException exception) {
                card = cards.findByIdentityIncludingDeleted(userId, language, normalizedTerm);
                if (card == null) {
                    throw exception;
                }
            }
        }

        String status;
        if (created) {
            status = card.getStatus();
        } else if (card.getDeletedAt() != null) {
            status = restoredStatus(card, reviewRequired);
            cards.restoreAndTouch(userId, card.getCardUid(), normalizedTerm, status, capturedAt);
            restored = true;
        } else {
            cards.touch(userId, card.getCardUid(), capturedAt);
            status = reviewRequired ? "needs_review" : card.getStatus();
        }

        VocabularyCardSource source = newSource(
                userId, card.getCardUid(), rawTerm, idempotencyKey, request.source(), capturedAt);
        try {
            sources.insertSource(source);
        } catch (DuplicateKeyException exception) {
            VocabularyCardSource winningSource = sources.findSourceByIdempotencyKey(userId, idempotencyKey);
            if (winningSource == null || !card.getCardUid().equals(winningSource.getCardUid())) {
                throw exception;
            }
            return new VocabularyCaptureResponse.Item(
                    rawTerm, winningSource.getCardUid(), "source_merged", status);
        }

        boolean shouldGenerate = !reviewRequired
                && (created || restored)
                && card.getActiveRevisionUid() == null;
        if (shouldGenerate) {
            VocabularyTemplateRegistry.TemplateDefinition generationTemplate =
                    created ? selectedTemplate : cardTemplate(card, selectedTemplate);
            jobs.insertJob(newJob(card, source, generationTemplate, capturedAt, request.clientRequestId(), index));
            status = "generating";
        }

        String action = reviewRequired ? "needs_review" : created ? "created" : "source_merged";
        return new VocabularyCaptureResponse.Item(rawTerm, card.getCardUid(), action, status);
    }

    private VocabularyTemplateRegistry.TemplateDefinition resolveTemplate(Long userId, String requestedTemplate) {
        String templateKey = requestedTemplate;
        if (templateKey == null || templateKey.isBlank()) {
            UserVocabularyPreference preference = preferences.findPreferenceByUser(userId);
            templateKey = preference == null ? null : preference.getDefaultTemplateKey();
        }
        VocabularyTemplateRegistry.TemplateDefinition template = templateRegistry.require(templateKey);
        preferences.upsertDefaultTemplate(userId, template.key());
        return template;
    }

    private VocabularyCard newCard(
            Long userId,
            String rawTerm,
            String normalizedTerm,
            String language,
            VocabularyTemplateRegistry.TemplateDefinition template,
            boolean reviewRequired,
            LocalDateTime capturedAt) {
        VocabularyCard card = new VocabularyCard();
        card.setCardUid(uid("card_"));
        card.setUserId(userId);
        card.setLanguage(language);
        card.setOriginalTerm(rawTerm);
        card.setNormalizedTerm(normalizedTerm);
        card.setDisplayTerm(normalizedTerm);
        card.setTemplateKey(template.key());
        card.setTemplateVersion(template.version());
        card.setStatus(reviewRequired ? "needs_review" : "generating");
        card.setActiveRevisionUid(null);
        card.setLastCapturedAt(capturedAt);
        card.setDeletedAt(null);
        return card;
    }

    private VocabularyCardSource newSource(
            Long userId,
            String cardUid,
            String rawTerm,
            String idempotencyKey,
            VocabularyCaptureRequest.Source requestSource,
            LocalDateTime capturedAt) {
        VocabularyCaptureRequest.Source sourceValue = requestSource == null
                ? new VocabularyCaptureRequest.Source("manual", null, "手动输入", null, null, Map.of())
                : requestSource;
        VocabularyCardSource source = new VocabularyCardSource();
        source.setSourceUid(uid("src_"));
        source.setCardUid(cardUid);
        source.setUserId(userId);
        source.setSourceType(sourceValue.type() == null ? "manual" : sourceValue.type());
        source.setSourceRef(sourceValue.sourceRef());
        source.setSourceTitle(sourceValue.sourceTitle());
        source.setSourceUrl(sourceValue.sourceUrl());
        source.setContextText(limitContext(sourceValue.contextText()));
        source.setRawTerm(rawTerm);
        source.setIdempotencyKey(idempotencyKey);
        source.setCapturedAt(capturedAt);
        source.setMetadataJson(writeJson(sourceValue.metadata() == null ? Map.of() : sourceValue.metadata()));
        return source;
    }

    private VocabularyGenerationJob newJob(
            VocabularyCard card,
            VocabularyCardSource source,
            VocabularyTemplateRegistry.TemplateDefinition template,
            LocalDateTime capturedAt,
            String clientRequestId,
            int index) {
        VocabularyGenerationJob job = new VocabularyGenerationJob();
        job.setJobUid(uid("job_"));
        job.setCardUid(card.getCardUid());
        job.setBaseRevisionUid(card.getActiveRevisionUid());
        job.setTemplateKey(template.key());
        job.setTemplateVersion(template.version());
        job.setStatus("pending");
        job.setAttemptCount(0);

        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("clientRequestId", clientRequestId);
        requestData.put("index", index);
        requestData.put("sourceUid", source.getSourceUid());
        job.setRequestJson(writeJson(requestData));
        job.setResultRevisionUid(null);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setAvailableAt(capturedAt);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        return job;
    }

    private VocabularyTemplateRegistry.TemplateDefinition cardTemplate(
            VocabularyCard card,
            VocabularyTemplateRegistry.TemplateDefinition fallback) {
        if (card.getTemplateKey() == null || card.getTemplateKey().isBlank()) {
            return fallback;
        }
        return templateRegistry.require(card.getTemplateKey());
    }

    private String restoredStatus(VocabularyCard card, boolean reviewRequired) {
        if (reviewRequired) {
            return "needs_review";
        }
        return card.getActiveRevisionUid() == null ? "generating" : "ready";
    }

    private String canonicalLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("en-gb") || normalized.equals("en-us") ? "en" : normalized;
    }

    private String limitContext(String contextText) {
        if (contextText == null || contextText.length() <= MAX_CONTEXT_LENGTH) {
            return contextText;
        }
        return contextText.substring(0, MAX_CONTEXT_LENGTH);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("capture metadata must be JSON serializable", exception);
        }
    }

    private String uid(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
