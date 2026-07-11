package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardDetailResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardSummaryResponse;
import com.personalenglishai.backend.dto.vocabulary.UpdateVocabularyCardRequest;
import com.personalenglishai.backend.dto.vocabulary.ResolveVocabularyConflictRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyConflictResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyGenerationJobResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyRevisionListResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyRevisionResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateCatalogResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VocabularyCardService {
    private static final String DEFAULT_TEMPLATE_KEY = "basic";

    private final VocabularyCardMapper cards;
    private final VocabularySourceMapper sources;
    private final VocabularyRevisionMapper revisions;
    private final VocabularyGenerationJobMapper jobs;
    private final UserVocabularyPreferenceMapper preferences;
    private final VocabularyTemplateRegistry templateRegistry;
    private final ObjectMapper objectMapper;
    private final VocabularyRevisionWriteService revisionWriter;

    public VocabularyCardService(
            VocabularyCardMapper cards,
            VocabularySourceMapper sources,
            VocabularyRevisionMapper revisions,
            VocabularyGenerationJobMapper jobs,
            UserVocabularyPreferenceMapper preferences,
            VocabularyTemplateRegistry templateRegistry,
            ObjectMapper objectMapper,
            VocabularyRevisionWriteService revisionWriter) {
        this.cards = cards;
        this.sources = sources;
        this.revisions = revisions;
        this.jobs = jobs;
        this.preferences = preferences;
        this.templateRegistry = templateRegistry;
        this.objectMapper = objectMapper;
        this.revisionWriter = revisionWriter;
    }

    public VocabularyTemplateCatalogResponse templateCatalog(Long userId) {
        List<VocabularyTemplateResponse> items = templateRegistry.list();
        UserVocabularyPreference preference = preferences.findPreferenceByUser(userId);
        String preferredKey = preference == null ? null : preference.getDefaultTemplateKey();
        boolean supported = items.stream().anyMatch(item -> item.key().equals(preferredKey));
        return new VocabularyTemplateCatalogResponse(
                items,
                supported ? preferredKey : DEFAULT_TEMPLATE_KEY);
    }

    public AdminPageResponse<VocabularyCardSummaryResponse> list(
            Long userId,
            String keyword,
            String status,
            String sourceType,
            Integer page,
            Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 50));
        int offset = (safePage - 1) * safeSize;
        List<VocabularyCard> pageItems = cards.listByUser(
                userId, keyword, status, sourceType, offset, safeSize);
        Map<String, List<String>> sourceTypesByCardUid = sourceTypesByCardUid(userId, pageItems);
        List<VocabularyCardSummaryResponse> items = pageItems.stream()
                .map(card -> toSummary(card, sourceTypesByCardUid.getOrDefault(card.getCardUid(), List.of()),
                        candidateRevision(card, revisions.listRevisions(card.getCardUid()))))
                .toList();
        long total = cards.countByUser(userId, keyword, status, sourceType);
        return new AdminPageResponse<>(items, total, safePage, safeSize);
    }

    public VocabularyCardDetailResponse getDetail(Long userId, String cardUid) {
        VocabularyCard card = cards.findOwnedByUid(userId, cardUid);
        if (card == null) {
            throw new BizException(ErrorCode.VOCABULARY_CARD_NOT_FOUND);
        }

        List<VocabularyCardSource> ownedSources = ownedSources(userId, cardUid);
        List<VocabularyCardDetailResponse.SourceItem> sourceItems = ownedSources.stream()
                .map(this::toSourceItem)
                .toList();
        VocabularyGenerationJob latestJob = jobs.findLatestByCard(cardUid);
        VocabularyCardRevision candidate = candidateRevision(card, revisions.listRevisions(cardUid));
        return new VocabularyCardDetailResponse(
                card.getCardUid(),
                card.getDisplayTerm(),
                card.getNormalizedTerm(),
                card.getLanguage(),
                card.getTemplateKey(),
                card.getTemplateVersion(),
                card.getStatus(),
                card.getActiveRevisionUid(),
                sourceTypes(ownedSources),
                activeContent(card),
                sourceItems,
                latestJob == null ? null : latestJob.getStatus(),
                latestJob == null ? null : latestJob.getErrorMessage(),
                card.getLastCapturedAt(),
                card.getCreatedAt(),
                card.getUpdatedAt(),
                candidate == null ? null : candidate.getRevisionUid(),
                candidate == null ? null : revisionContent(candidate),
                conflictStatus(card, candidate));
    }

    public VocabularyCardDetailResponse update(Long userId, String cardUid, UpdateVocabularyCardRequest request) {
        VocabularyCard card = requireOwnedCard(userId, cardUid);
        VocabularyTemplateRegistry.TemplateDefinition template = templateRegistry.require(card.getTemplateKey());
        ObjectNode content = editableContent(card, request.content());
        templateRegistry.validate(template.key(), content);

        VocabularyCardRevision revision = new VocabularyCardRevision();
        revision.setRevisionUid(uid("rev_"));
        revision.setCardUid(cardUid);
        revision.setBaseRevisionUid(request.baseRevisionUid());
        revision.setAuthorType("user");
        revision.setTemplateKey(template.key());
        revision.setTemplateVersion(template.version());
        revision.setContentJson(writeJson(content));
        revision.setChangeSummary(request.changeSummary());
        VocabularyRevisionWriteService.WriteOutcome outcome =
                revisionWriter.appendAndActivate(userId, card, revision);
        if (outcome == VocabularyRevisionWriteService.WriteOutcome.STALE) {
            throw conflictFor(userId, cardUid);
        }
        return getDetail(userId, cardUid);
    }

    @Transactional
    public void delete(Long userId, String cardUid) {
        requireOwnedCard(userId, cardUid);
        if (cards.softDelete(userId, cardUid) != 1) {
            throw new BizException(ErrorCode.VOCABULARY_CARD_NOT_FOUND);
        }
        jobs.cancelActiveForCard(cardUid);
    }

    @Transactional
    public VocabularyGenerationJobResponse regenerate(Long userId, String cardUid) {
        VocabularyCard card = requireOwnedCard(userId, cardUid);
        VocabularyTemplateRegistry.TemplateDefinition template = templateRegistry.require(card.getTemplateKey());
        jobs.cancelActiveForCard(cardUid);
        VocabularyGenerationJob job = newGenerationJob(card, template, "regenerate");
        jobs.insertJob(job);
        return new VocabularyGenerationJobResponse(job.getJobUid(), job.getStatus());
    }

    @Transactional
    public VocabularyGenerationJobResponse retry(Long userId, String cardUid) {
        requireOwnedCard(userId, cardUid);
        VocabularyGenerationJob latest = jobs.findLatestByCard(cardUid);
        if (latest == null || !"failed".equals(latest.getStatus()) || jobs.retryFailed(latest.getJobUid()) != 1) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Only the latest failed generation can be retried");
        }
        return new VocabularyGenerationJobResponse(latest.getJobUid(), "pending");
    }

    public VocabularyRevisionListResponse revisions(Long userId, String cardUid) {
        VocabularyCard card = requireOwnedCard(userId, cardUid);
        List<VocabularyCardRevision> history = revisions.listRevisions(cardUid).stream()
                .filter(revision -> Objects.equals(cardUid, revision.getCardUid()))
                .toList();
        VocabularyCardRevision candidate = candidateRevision(card, history);
        List<VocabularyRevisionResponse> items = history.stream()
                .map(revision -> new VocabularyRevisionResponse(
                        revision.getRevisionUid(), revision.getBaseRevisionUid(), revision.getAuthorType(),
                        revision.getTemplateKey(), revision.getTemplateVersion(), revisionContent(revision),
                        revision.getChangeSummary(), Objects.equals(card.getActiveRevisionUid(), revision.getRevisionUid()),
                        candidate != null && Objects.equals(candidate.getRevisionUid(), revision.getRevisionUid()),
                        revision.getCreatedAt()))
                .toList();
        return new VocabularyRevisionListResponse(card.getActiveRevisionUid(),
                candidate == null ? null : candidate.getRevisionUid(), conflictStatus(card, candidate), items);
    }

    @Transactional
    public VocabularyCardDetailResponse resolveConflict(
            Long userId,
            String cardUid,
            String revisionUid,
            ResolveVocabularyConflictRequest request) {
        VocabularyCard card = requireOwnedCard(userId, cardUid);
        VocabularyCardRevision current = ownedRevision(card, card.getActiveRevisionUid());
        VocabularyCardRevision candidate = ownedRevision(card, revisionUid);
        if (!"needs_review".equals(card.getStatus()) || current == null || candidate == null
                || "ai".equals(candidate.getAuthorType()) == false
                || Objects.equals(candidate.getRevisionUid(), current.getRevisionUid())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Vocabulary conflict candidate is invalid");
        }

        switch (request.choice()) {
            case "keep_current" -> activateResolution(userId, card, current, current);
            case "use_ai" -> activateResolution(userId, card, current, candidate);
            case "merge_fields" -> {
                VocabularyCardRevision merged = mergedRevision(card, current, request.mergeFields());
                revisions.insertRevision(merged);
                activateResolution(userId, card, current, merged);
            }
            default -> throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Unsupported vocabulary conflict choice");
        }
        return getDetail(userId, cardUid);
    }

    private VocabularyCardSummaryResponse toSummary(
            VocabularyCard card,
            List<String> sourceTypes,
            VocabularyCardRevision candidate) {
        return new VocabularyCardSummaryResponse(
                card.getCardUid(),
                card.getDisplayTerm(),
                card.getNormalizedTerm(),
                card.getTemplateKey(),
                card.getStatus(),
                card.getActiveRevisionUid(),
                sourceTypes,
                card.getLastCapturedAt(),
                card.getUpdatedAt(),
                candidate == null ? null : candidate.getRevisionUid(),
                conflictStatus(card, candidate));
    }

    private Map<String, List<String>> sourceTypesByCardUid(Long userId, List<VocabularyCard> pageItems) {
        if (pageItems.isEmpty()) {
            return Map.of();
        }
        List<String> cardUids = pageItems.stream().map(VocabularyCard::getCardUid).toList();
        Map<String, List<String>> grouped = new HashMap<>();
        for (VocabularyCardSource source : sources.listDistinctSourceTypesByCardUids(userId, cardUids)) {
            if (!Objects.equals(userId, source.getUserId())
                    || !cardUids.contains(source.getCardUid())
                    || source.getSourceType() == null) {
                continue;
            }
            List<String> types = grouped.computeIfAbsent(source.getCardUid(), ignored -> new ArrayList<>());
            if (!types.contains(source.getSourceType())) {
                types.add(source.getSourceType());
            }
        }
        return grouped;
    }

    private List<String> sourceTypes(List<VocabularyCardSource> sourceItems) {
        return sourceItems.stream()
                .map(VocabularyCardSource::getSourceType)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<VocabularyCardSource> ownedSources(Long userId, String cardUid) {
        return sources.listSources(cardUid).stream()
                .filter(source -> Objects.equals(userId, source.getUserId()))
                .toList();
    }

    private JsonNode activeContent(VocabularyCard card) {
        if (card.getActiveRevisionUid() == null) {
            return null;
        }
        VocabularyCardRevision revision = revisions.findRevision(card.getActiveRevisionUid());
        if (revision == null || !Objects.equals(card.getCardUid(), revision.getCardUid())) {
            return null;
        }
        return revisionContent(revision);
    }

    private VocabularyCardDetailResponse.SourceItem toSourceItem(VocabularyCardSource source) {
        return new VocabularyCardDetailResponse.SourceItem(
                source.getSourceUid(),
                source.getSourceType(),
                source.getSourceRef(),
                source.getSourceTitle(),
                source.getSourceUrl(),
                source.getContextText(),
                source.getRawTerm(),
                parseJson(source.getMetadataJson(), "metadata_json"),
                source.getCapturedAt(),
                source.getCreatedAt());
    }

    private JsonNode parseJson(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ignored) {
            throw new IllegalStateException("invalid stored " + field);
        }
    }

    private VocabularyCard requireOwnedCard(Long userId, String cardUid) {
        VocabularyCard card = cards.findOwnedByUid(userId, cardUid);
        if (card == null) {
            throw new BizException(ErrorCode.VOCABULARY_CARD_NOT_FOUND);
        }
        return card;
    }

    private ObjectNode editableContent(VocabularyCard card, JsonNode requested) {
        if (requested == null || !requested.isObject()) {
            throw new IllegalArgumentException("vocabulary content must be an object");
        }
        ObjectNode content = ((ObjectNode) requested).deepCopy();
        content.put("term", card.getNormalizedTerm());
        return content;
    }

    private VocabularyCardRevision candidateRevision(
            VocabularyCard card,
            List<VocabularyCardRevision> history) {
        if (!"needs_review".equals(card.getStatus())) {
            return null;
        }
        return history.stream()
                .filter(revision -> Objects.equals(card.getCardUid(), revision.getCardUid()))
                .filter(revision -> !Objects.equals(card.getActiveRevisionUid(), revision.getRevisionUid()))
                .filter(revision -> "ai".equals(revision.getAuthorType()) || "user".equals(revision.getAuthorType()))
                .findFirst()
                .orElse(null);
    }

    private String conflictStatus(VocabularyCard card, VocabularyCardRevision candidate) {
        return candidate == null ? "none" : "needs_review";
    }

    private JsonNode revisionContent(VocabularyCardRevision revision) {
        return parseJson(revision.getContentJson(), "content_json");
    }

    private VocabularyRevisionConflictException conflictFor(Long userId, String cardUid) {
        VocabularyCard currentCard = requireOwnedCard(userId, cardUid);
        VocabularyCardRevision current = ownedRevision(currentCard, currentCard.getActiveRevisionUid());
        VocabularyCardRevision candidate = candidateRevision(currentCard, revisions.listRevisions(cardUid));
        return new VocabularyRevisionConflictException(new VocabularyConflictResponse(
                current == null ? null : current.getRevisionUid(),
                candidate == null ? null : candidate.getRevisionUid(),
                current == null ? null : revisionContent(current),
                candidate == null ? null : revisionContent(candidate),
                conflictStatus(currentCard, candidate)));
    }

    private VocabularyCardRevision ownedRevision(VocabularyCard card, String revisionUid) {
        if (revisionUid == null) {
            return null;
        }
        VocabularyCardRevision revision = revisions.findRevision(revisionUid);
        return revision != null && Objects.equals(card.getCardUid(), revision.getCardUid()) ? revision : null;
    }

    private void activateResolution(
            Long userId,
            VocabularyCard card,
            VocabularyCardRevision current,
            VocabularyCardRevision next) {
        if (cards.updateActiveRevision(userId, card.getCardUid(), current.getRevisionUid(), next.getRevisionUid(),
                "ready", next.getTemplateKey(), next.getTemplateVersion()) != 1) {
            throw conflictFor(userId, card.getCardUid());
        }
    }

    private VocabularyCardRevision mergedRevision(
            VocabularyCard card,
            VocabularyCardRevision current,
            Map<String, JsonNode> mergeFields) {
        ObjectNode content = editableContent(card, revisionContent(current));
        VocabularyTemplateRegistry.TemplateDefinition template = templateRegistry.require(current.getTemplateKey());
        Map<String, JsonNode> fields = mergeFields == null ? Map.of() : mergeFields;
        for (Map.Entry<String, JsonNode> field : fields.entrySet()) {
            if ("term".equals(field.getKey()) || !template.requiredFields().contains(field.getKey())) {
                throw new IllegalArgumentException("merge field is not allowed: " + field.getKey());
            }
            content.set(field.getKey(), field.getValue());
        }
        content.put("term", card.getNormalizedTerm());
        templateRegistry.validate(template.key(), content);

        VocabularyCardRevision merged = new VocabularyCardRevision();
        merged.setRevisionUid(uid("rev_"));
        merged.setCardUid(card.getCardUid());
        merged.setBaseRevisionUid(current.getRevisionUid());
        merged.setAuthorType("system_merge");
        merged.setTemplateKey(template.key());
        merged.setTemplateVersion(template.version());
        merged.setContentJson(writeJson(content));
        merged.setChangeSummary("Resolved vocabulary revision conflict");
        return merged;
    }

    private VocabularyGenerationJob newGenerationJob(
            VocabularyCard card,
            VocabularyTemplateRegistry.TemplateDefinition template,
            String action) {
        VocabularyGenerationJob job = new VocabularyGenerationJob();
        job.setJobUid(uid("job_"));
        job.setCardUid(card.getCardUid());
        job.setBaseRevisionUid(card.getActiveRevisionUid());
        job.setTemplateKey(template.key());
        job.setTemplateVersion(template.version());
        job.setStatus("pending");
        job.setAttemptCount(0);
        job.setRequestJson(writeJson(Map.of("action", action)));
        job.setAvailableAt(java.time.LocalDateTime.now());
        return job;
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("vocabulary content cannot be stored", exception);
        }
    }

    private String writeJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("vocabulary generation request cannot be stored", exception);
        }
    }

    private String uid(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
