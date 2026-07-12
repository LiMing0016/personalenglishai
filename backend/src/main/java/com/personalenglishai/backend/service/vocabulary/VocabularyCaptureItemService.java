package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
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
import java.util.function.Supplier;

@Service
public class VocabularyCaptureItemService {
    static final int MAX_CONTEXT_LENGTH = 2_000;

    private final VocabularyCardMapper cards;
    private final VocabularySourceMapper sources;
    private final VocabularyGenerationJobMapper jobs;
    private final VocabularyTermNormalizer termNormalizer;
    private final VocabularyTemplateRegistry templateRegistry;
    private final ObjectMapper objectMapper;

    public VocabularyCaptureItemService(
            VocabularyCardMapper cards,
            VocabularySourceMapper sources,
            VocabularyGenerationJobMapper jobs,
            VocabularyTermNormalizer termNormalizer,
            VocabularyTemplateRegistry templateRegistry,
            ObjectMapper objectMapper) {
        this.cards = cards;
        this.sources = sources;
        this.jobs = jobs;
        this.termNormalizer = termNormalizer;
        this.templateRegistry = templateRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public CaptureOutcome captureOne(
            Long userId,
            VocabularyCaptureRequest request,
            Supplier<ResolvedVocabularyTheme> themeResolver,
            int index) {
        return captureOneInternal(userId, request, themeResolver, index);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public CaptureOutcome captureOneInCallerTransaction(
            Long userId,
            VocabularyCaptureRequest request,
            Supplier<ResolvedVocabularyTheme> themeResolver,
            int index) {
        return captureOneInternal(userId, request, themeResolver, index);
    }

    private CaptureOutcome captureOneInternal(
            Long userId,
            VocabularyCaptureRequest request,
            Supplier<ResolvedVocabularyTheme> themeResolver,
            int index) {
        String rawTerm = request.terms().get(index);
        String idempotencyKey = request.clientRequestId() + ":" + index;
        VocabularyCardSource existingSource = sources.findSourceByIdempotencyKey(userId, idempotencyKey);
        if (existingSource != null) {
            VocabularyCard existingCard = cards.findByUidIncludingDeleted(existingSource.getCardUid());
            if (existingCard != null && existingCard.getDeletedAt() != null) {
                return restoreIdempotentCard(userId, rawTerm, existingSource, existingCard, themeResolver);
            }
            String status = existingCard == null ? "captured" : existingCard.getStatus();
            return outcome(rawTerm, existingSource.getCardUid(), "source_merged", status, false);
        }

        ResolvedVocabularyTheme theme = themeResolver.get();
        VocabularyTemplateRegistry.TemplateDefinition selectedTemplate = templateFor(theme);
        String normalizedTerm = termNormalizer.normalize(rawTerm);
        boolean reviewRequired = termNormalizer.isReviewRequired(rawTerm, normalizedTerm);
        String language = canonicalLanguage(request.language());
        LocalDateTime capturedAt = LocalDateTime.now();

        VocabularyCard card = cards.findByIdentityIncludingDeleted(userId, language, normalizedTerm);
        boolean created = false;
        boolean restored = false;
        if (card == null) {
            card = newCard(
                    userId, rawTerm, normalizedTerm, language, selectedTemplate, theme, reviewRequired, capturedAt);
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
            int restoredCount = cards.restoreAndTouch(
                    userId, card.getCardUid(), normalizedTerm, status, capturedAt);
            if (restoredCount == 1) {
                restored = true;
            } else {
                card = cards.findByIdentityIncludingDeleted(userId, language, normalizedTerm);
                if (card == null || card.getDeletedAt() != null) {
                    throw new IllegalStateException("concurrent card restoration could not be resolved");
                }
                cards.touch(userId, card.getCardUid(), capturedAt);
                status = reviewRequired ? "needs_review" : card.getStatus();
            }
        } else {
            cards.touch(userId, card.getCardUid(), capturedAt);
            status = reviewRequired ? "needs_review" : card.getStatus();
        }
        if (reviewRequired && !created) {
            cards.markNeedsReview(userId, card.getCardUid());
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
            return outcome(rawTerm, winningSource.getCardUid(), "source_merged", status, true);
        }

        boolean shouldGenerate = !reviewRequired
                && (created || restored)
                && card.getActiveRevisionUid() == null;
        if (shouldGenerate) {
            ResolvedVocabularyTheme generationTheme = created ? theme : cardTheme(card, () -> theme);
            VocabularyTemplateRegistry.TemplateDefinition generationTemplate = templateFor(generationTheme);
            jobs.insertJob(newJob(
                    card, source, generationTemplate, generationTheme, capturedAt, request.clientRequestId(), index));
            status = "generating";
        }

        String action = reviewRequired ? "needs_review" : created ? "created" : "source_merged";
        return outcome(rawTerm, card.getCardUid(), action, status, true);
    }

    private CaptureOutcome restoreIdempotentCard(
            Long userId,
            String rawTerm,
            VocabularyCardSource source,
            VocabularyCard card,
            Supplier<ResolvedVocabularyTheme> themeResolver) {
        String normalizedTerm = termNormalizer.normalize(rawTerm);
        boolean reviewRequired = termNormalizer.isReviewRequired(rawTerm, normalizedTerm);
        LocalDateTime capturedAt = LocalDateTime.now();
        String status = restoredStatus(card, reviewRequired);
        int restored = cards.restoreAndTouch(
                userId, card.getCardUid(), normalizedTerm, status, capturedAt);
        if (restored == 1 && !reviewRequired && card.getActiveRevisionUid() == null) {
            ResolvedVocabularyTheme generationTheme = cardTheme(card, themeResolver);
            jobs.insertJob(newJob(
                    card, source, templateFor(generationTheme), generationTheme, capturedAt,
                    source.getIdempotencyKey(), 0));
        } else if (restored != 1) {
            VocabularyCard current = cards.findByUidIncludingDeleted(card.getCardUid());
            if (current == null || current.getDeletedAt() != null) {
                throw new IllegalStateException("concurrent idempotent card restoration could not be resolved");
            }
            status = current.getStatus();
        }
        return outcome(rawTerm, card.getCardUid(), "source_merged", status, restored == 1);
    }

    private VocabularyTemplateRegistry.TemplateDefinition templateFor(ResolvedVocabularyTheme theme) {
        return templateRegistry.require(theme.legacyTemplateKey());
    }

    private VocabularyCard newCard(
            Long userId,
            String rawTerm,
            String normalizedTerm,
            String language,
            VocabularyTemplateRegistry.TemplateDefinition template,
            ResolvedVocabularyTheme theme,
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
        card.setThemeUid(theme.themeUid());
        card.setThemeVersion(theme.version());
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
            ResolvedVocabularyTheme theme,
            LocalDateTime capturedAt,
            String clientRequestId,
            int index) {
        VocabularyGenerationJob job = new VocabularyGenerationJob();
        job.setJobUid(uid("job_"));
        job.setCardUid(card.getCardUid());
        job.setBaseRevisionUid(card.getActiveRevisionUid());
        job.setTemplateKey(template.key());
        job.setTemplateVersion(template.version());
        job.setThemeUid(theme.themeUid());
        job.setThemeVersion(theme.version());
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
        job.setLeaseToken(null);
        job.setLeaseExpiresAt(null);
        job.setFinishedAt(null);
        return job;
    }

    private ResolvedVocabularyTheme cardTheme(VocabularyCard card, Supplier<ResolvedVocabularyTheme> fallback) {
        if (card.getThemeUid() != null && !card.getThemeUid().isBlank() && card.getThemeVersion() != null) {
            String templateKey = card.getTemplateKey();
            if (templateKey == null || templateKey.isBlank()) {
                templateKey = fallback.get().legacyTemplateKey();
            }
            return new ResolvedVocabularyTheme(
                    card.getThemeUid(), card.getThemeVersion(), "", "", "", 1, templateKey);
        }
        return fallback.get();
    }

    private CaptureOutcome outcome(String term, String cardUid, String action, String status, boolean mutated) {
        return new CaptureOutcome(new VocabularyCaptureResponse.Item(term, cardUid, action, status), mutated);
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

    record CaptureOutcome(VocabularyCaptureResponse.Item response, boolean mutated) {
    }
}
