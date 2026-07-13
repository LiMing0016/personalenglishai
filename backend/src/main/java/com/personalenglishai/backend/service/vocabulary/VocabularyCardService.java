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
import com.personalenglishai.backend.dto.vocabulary.RegenerateVocabularyCardRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyConflictResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyGenerationJobResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyRevisionListResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyRevisionResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyThemeSnapshot;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateCatalogResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import com.personalenglishai.backend.entity.vocabulary.UserVocabularyPreference;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.entity.vocabulary.VocabularyGenerationJob;
import com.personalenglishai.backend.entity.vocabulary.VocabularyThemeRevision;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VocabularyCardService {
    private static final String DEFAULT_TEMPLATE_KEY = "basic";
    private static final int CONTENT_FORMAT_VERSION = 1;
    private static final int MAX_MARKDOWN_LENGTH = 20_000;
    private static final Pattern RAW_HTML = Pattern.compile(
            "(?is)<\\s*(?:!|\\?|/?\\s*[a-z])[^>]*>");

    private final VocabularyCardMapper cards;
    private final VocabularySourceMapper sources;
    private final VocabularyRevisionMapper revisions;
    private final VocabularyGenerationJobMapper jobs;
    private final UserVocabularyPreferenceMapper preferences;
    private final VocabularyThemeService themeService;
    private final VocabularyThemeMapper themes;
    private final VocabularyTemplateRegistry templateRegistry;
    private final VocabularyCoreContentCodec coreCodec;
    private final ObjectMapper objectMapper;
    private final VocabularyRevisionWriteService revisionWriter;

    public VocabularyCardService(
            VocabularyCardMapper cards,
            VocabularySourceMapper sources,
            VocabularyRevisionMapper revisions,
            VocabularyGenerationJobMapper jobs,
            UserVocabularyPreferenceMapper preferences,
            VocabularyThemeService themeService,
            VocabularyThemeMapper themes,
            VocabularyTemplateRegistry templateRegistry,
            VocabularyCoreContentCodec coreCodec,
            ObjectMapper objectMapper,
            VocabularyRevisionWriteService revisionWriter) {
        this.cards = cards;
        this.sources = sources;
        this.revisions = revisions;
        this.jobs = jobs;
        this.preferences = preferences;
        this.themeService = themeService;
        this.themes = themes;
        this.templateRegistry = templateRegistry;
        this.coreCodec = coreCodec;
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
        return list(userId, keyword, status, sourceType, "recent", page, size);
    }

    public AdminPageResponse<VocabularyCardSummaryResponse> list(
            Long userId,
            String keyword,
            String status,
            String sourceType,
            String sort,
            Integer page,
            Integer size) {
        if (!"recent".equals(sort) && !"az".equals(sort)) {
            throw new IllegalArgumentException("unsupported vocabulary sort");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 50));
        int offset = (safePage - 1) * safeSize;
        List<VocabularyCard> pageItems = cards.listByUser(
                userId, keyword, status, sourceType, sort, offset, safeSize);
        Map<String, SourceSummary> sourcesByCardUid = sourceSummariesByCardUid(userId, pageItems);
        Map<String, VocabularyGenerationJob> latestJobsByCardUid = latestJobsByCardUid(userId, pageItems);
        List<VocabularyCardSummaryResponse> items = pageItems.stream()
                .map(card -> {
                    List<VocabularyCardRevision> history = revisions.listRevisions(card.getCardUid());
                    return toSummary(
                            card,
                            sourcesByCardUid.getOrDefault(card.getCardUid(), SourceSummary.EMPTY),
                            history,
                            latestJobsByCardUid.get(card.getCardUid()));
                })
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
        VocabularyCardRevision activeRevision = activeRevision(card);
        RevisionProjection active = projection(card, activeRevision);
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
                active.content(),
                sourceItems,
                latestJob == null ? null : latestJob.getStatus(),
                latestJob == null ? null : latestJob.getErrorMessage(),
                latestJob == null ? null : latestJob.getGenerationOutcome(),
                latestJob == null ? null : latestJob.getWarning(),
                card.getLastCapturedAt(),
                card.getCreatedAt(),
                card.getUpdatedAt(),
                candidate == null ? null : candidate.getRevisionUid(),
                candidate == null ? null : revisionContent(candidate),
                conflictStatus(card, candidate),
                active.theme(),
                activeRevision == null ? null : activeRevision.getThemeVersion(),
                active.core(),
                active.markdown(),
                active.contentFormatVersion());
    }

    public VocabularyCardDetailResponse update(Long userId, String cardUid, UpdateVocabularyCardRequest request) {
        VocabularyCard card = requireOwnedCard(userId, cardUid);
        VocabularyTemplateRegistry.TemplateDefinition template = templateRegistry.require(card.getTemplateKey());
        ObjectNode core;
        ObjectNode legacyContent = null;
        if (request.core() != null) {
            core = editableCore(card, request.core());
        } else {
            legacyContent = editableContent(card, request.content());
            templateRegistry.validate(template.key(), legacyContent);
            core = coreCodec.fromLegacy(card.getNormalizedTerm(), legacyContent);
        }
        validateMarkdown(request.markdown());
        VocabularyCardRevision baseRevision = ownedRevision(card, request.baseRevisionUid());
        String themeUid = baseRevision != null && baseRevision.getThemeUid() != null
                ? baseRevision.getThemeUid()
                : card.getThemeUid();
        Integer themeVersion = baseRevision != null && baseRevision.getThemeVersion() != null
                ? baseRevision.getThemeVersion()
                : card.getThemeVersion();
        Integer contentFormatVersion = legacyContent == null
                ? baseRevision != null && baseRevision.getContentFormatVersion() != null
                        ? baseRevision.getContentFormatVersion()
                        : CONTENT_FORMAT_VERSION
                : null;

        VocabularyCardRevision revision = new VocabularyCardRevision();
        revision.setRevisionUid(uid("rev_"));
        revision.setCardUid(cardUid);
        revision.setBaseRevisionUid(request.baseRevisionUid());
        revision.setAuthorType("user");
        revision.setTemplateKey(template.key());
        revision.setTemplateVersion(template.version());
        revision.setThemeUid(themeUid);
        revision.setThemeVersion(themeVersion);
        revision.setCoreJson(writeJson(core));
        revision.setContentMarkdown(request.markdown());
        revision.setContentFormatVersion(contentFormatVersion);
        revision.setContentJson(legacyContent == null
                ? writeCompatibilityJson(core, request.markdown())
                : writeJson(legacyContent));
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
        return regenerate(userId, cardUid, (RegenerateVocabularyCardRequest) null);
    }

    @Transactional
    public VocabularyGenerationJobResponse regenerate(
            Long userId,
            String cardUid,
            RegenerateVocabularyCardRequest request) {
        VocabularyCard card = requireOwnedCard(userId, cardUid);
        ResolvedVocabularyTheme theme = regenerationTheme(userId, card, request);
        VocabularyTemplateRegistry.TemplateDefinition template = templateRegistry.require(theme.legacyTemplateKey());
        jobs.cancelActiveForCard(cardUid);
        VocabularyGenerationJob job = newGenerationJob(card, template, theme, "regenerate");
        jobs.insertJob(job);
        return new VocabularyGenerationJobResponse(job.getJobUid(), job.getStatus());
    }

    @Transactional
    public VocabularyGenerationJobResponse retry(Long userId, String cardUid) {
        requireOwnedCard(userId, cardUid);
        VocabularyGenerationJob latest = jobs.findLatestByCard(cardUid);
        if (latest == null || !"failed".equals(latest.getStatus())
                || jobs.retryFailed(cardUid, latest.getJobUid()) != 1) {
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
                .map(revision -> {
                    RevisionProjection projected = projection(card, revision);
                    return new VocabularyRevisionResponse(
                            revision.getRevisionUid(), revision.getBaseRevisionUid(), revision.getAuthorType(),
                            revision.getTemplateKey(), revision.getTemplateVersion(), projected.content(),
                            projected.theme(), revision.getThemeVersion(), projected.core(), projected.markdown(),
                            projected.contentFormatVersion(), revision.getChangeSummary(),
                            Objects.equals(card.getActiveRevisionUid(), revision.getRevisionUid()),
                            candidate != null && Objects.equals(candidate.getRevisionUid(), revision.getRevisionUid()),
                            revision.getCreatedAt());
                })
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
        VocabularyCardRevision currentCandidate = candidateRevision(card, revisions.listRevisions(cardUid));
        if (!"needs_review".equals(card.getStatus()) || current == null || candidate == null
                || currentCandidate == null
                || !Objects.equals(currentCandidate.getRevisionUid(), candidate.getRevisionUid())
                || Objects.equals(candidate.getRevisionUid(), current.getRevisionUid())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Vocabulary conflict candidate is invalid");
        }

        VocabularyCardRevision resolution = switch (request.choice()) {
            case "keep_current" -> resolutionRevision(
                    card, current, current, revisionContent(current), "Kept current vocabulary revision");
            case "use_ai" -> resolutionRevision(
                    card, current, candidate, revisionContent(candidate), "Accepted vocabulary conflict candidate");
            case "merge_fields" -> mergedRevision(card, current, request.mergeFields());
            default -> throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "Unsupported vocabulary conflict choice");
        };
        revisions.insertRevision(resolution);
        activateResolution(userId, card, current, resolution);
        return getDetail(userId, cardUid);
    }

    private VocabularyCardSummaryResponse toSummary(
            VocabularyCard card,
            SourceSummary sources,
            List<VocabularyCardRevision> history,
            VocabularyGenerationJob latestJob) {
        VocabularyCardRevision candidate = candidateRevision(card, history);
        VocabularyCardRevision activeRevision = activeRevision(card, history);
        RevisionProjection active = projection(card, activeRevision);
        return new VocabularyCardSummaryResponse(
                card.getCardUid(),
                card.getDisplayTerm(),
                card.getNormalizedTerm(),
                card.getTemplateKey(),
                card.getStatus(),
                card.getActiveRevisionUid(),
                sources.types(),
                card.getLastCapturedAt(),
                card.getUpdatedAt(),
                candidate == null ? null : candidate.getRevisionUid(),
                conflictStatus(card, candidate),
                latestJob == null ? null : latestJob.getStatus(),
                latestJob == null ? null : latestJob.getErrorMessage(),
                latestJob == null ? null : latestJob.getGenerationOutcome(),
                latestJob == null ? null : latestJob.getWarning(),
                coreCodec.summaryPhonetic(active.core()),
                coreCodec.summaryDefinition(active.core()),
                sources.count());
    }

    private Map<String, VocabularyGenerationJob> latestJobsByCardUid(
            Long userId,
            List<VocabularyCard> pageItems) {
        if (pageItems.isEmpty()) {
            return Map.of();
        }
        List<String> cardUids = pageItems.stream().map(VocabularyCard::getCardUid).toList();
        Map<String, VocabularyGenerationJob> latestByCardUid = new HashMap<>();
        for (VocabularyGenerationJob job : jobs.listLatestByCardUids(userId, cardUids)) {
            if (job != null && cardUids.contains(job.getCardUid())) {
                latestByCardUid.put(job.getCardUid(), job);
            }
        }
        return latestByCardUid;
    }

    private Map<String, SourceSummary> sourceSummariesByCardUid(Long userId, List<VocabularyCard> pageItems) {
        if (pageItems.isEmpty()) {
            return Map.of();
        }
        List<String> cardUids = pageItems.stream().map(VocabularyCard::getCardUid).toList();
        Map<String, List<String>> typesByCardUid = new HashMap<>();
        Map<String, Integer> countsByCardUid = new HashMap<>();
        for (VocabularyCardSource source : sources.listDistinctSourceTypesByCardUids(userId, cardUids)) {
            if (!Objects.equals(userId, source.getUserId())
                    || !cardUids.contains(source.getCardUid())
                    || source.getSourceType() == null) {
                continue;
            }
            List<String> types = typesByCardUid.computeIfAbsent(source.getCardUid(), ignored -> new ArrayList<>());
            if (!types.contains(source.getSourceType())) {
                types.add(source.getSourceType());
            }
            countsByCardUid.merge(
                    source.getCardUid(),
                    source.getSourceCount() == null ? 0 : source.getSourceCount(),
                    Math::max);
        }
        Map<String, SourceSummary> summaries = new HashMap<>();
        typesByCardUid.forEach((cardUid, types) -> summaries.put(
                cardUid, new SourceSummary(List.copyOf(types), countsByCardUid.getOrDefault(cardUid, 0))));
        return summaries;
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

    private VocabularyCardRevision activeRevision(VocabularyCard card) {
        if (card.getActiveRevisionUid() == null) {
            return null;
        }
        VocabularyCardRevision revision = revisions.findRevision(card.getActiveRevisionUid());
        if (revision == null || !Objects.equals(card.getCardUid(), revision.getCardUid())) {
            return null;
        }
        return revision;
    }

    private VocabularyCardRevision activeRevision(
            VocabularyCard card,
            List<VocabularyCardRevision> history) {
        if (card.getActiveRevisionUid() == null) {
            return null;
        }
        return history.stream()
                .filter(revision -> Objects.equals(card.getCardUid(), revision.getCardUid()))
                .filter(revision -> Objects.equals(card.getActiveRevisionUid(), revision.getRevisionUid()))
                .findFirst()
                .orElse(null);
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

    private ObjectNode editableCore(VocabularyCard card, JsonNode requested) {
        if (requested == null || !requested.isObject()) {
            throw new IllegalArgumentException("vocabulary core must be an object");
        }
        ObjectNode core = ((ObjectNode) requested).deepCopy();
        core.put("term", card.getNormalizedTerm());
        coreCodec.validate(card.getNormalizedTerm(), core);
        return core;
    }

    private void validateMarkdown(String markdown) {
        if (markdown != null && markdown.length() > MAX_MARKDOWN_LENGTH) {
            throw new IllegalArgumentException("vocabulary markdown must not exceed 20000 characters");
        }
        if (markdown != null && RAW_HTML.matcher(markdown).find()) {
            throw new IllegalArgumentException("vocabulary markdown must not contain raw HTML");
        }
    }

    private VocabularyCardRevision candidateRevision(
            VocabularyCard card,
            List<VocabularyCardRevision> history) {
        if (!"needs_review".equals(card.getStatus())) {
            return null;
        }
        String candidateRevisionUid = card.getConflictCandidateRevisionUid();
        if (candidateRevisionUid == null || candidateRevisionUid.isBlank()) {
            return null;
        }
        return history.stream()
                .filter(revision -> Objects.equals(card.getCardUid(), revision.getCardUid()))
                .filter(revision -> Objects.equals(candidateRevisionUid, revision.getRevisionUid()))
                .findFirst()
                .orElse(null);
    }

    private String conflictStatus(VocabularyCard card, VocabularyCardRevision candidate) {
        return candidate == null ? "none" : "needs_review";
    }

    private JsonNode revisionContent(VocabularyCardRevision revision) {
        return parseJson(revision.getContentJson(), "content_json");
    }

    private RevisionProjection projection(VocabularyCard card, VocabularyCardRevision revision) {
        if (revision == null) {
            return RevisionProjection.EMPTY;
        }
        JsonNode content = revisionContent(revision);
        ObjectNode core;
        if (revision.getCoreJson() == null || revision.getCoreJson().isBlank()) {
            core = coreCodec.fromLegacy(card.getNormalizedTerm(), content);
        } else {
            core = editableCore(card, parseJson(revision.getCoreJson(), "core_json"));
        }
        String markdown = revision.getContentMarkdown();
        if (markdown == null && content != null && content.path("markdown").isTextual()) {
            markdown = content.path("markdown").asText();
        }
        return new RevisionProjection(
                content,
                themeSnapshot(revision),
                core,
                markdown,
                revision.getContentFormatVersion());
    }

    private VocabularyThemeSnapshot themeSnapshot(VocabularyCardRevision revision) {
        if (revision.getThemeUid() == null || revision.getThemeUid().isBlank()
                || revision.getThemeVersion() == null) {
            return null;
        }
        VocabularyThemeRevision theme = themes.findRevision(
                revision.getThemeUid(), revision.getThemeVersion());
        if (theme == null) {
            return new VocabularyThemeSnapshot(revision.getThemeUid(), null, null);
        }
        return new VocabularyThemeSnapshot(
                revision.getThemeUid(), theme.getNameSnapshot(), theme.getPurpose());
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
                current == null ? null : current.getContentFormatVersion(),
                candidate == null ? null : candidate.getContentFormatVersion(),
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
                "ready", next.getTemplateKey(), next.getTemplateVersion(),
                next.getThemeUid(), next.getThemeVersion()) != 1) {
            throw conflictFor(userId, card.getCardUid());
        }
    }

    private VocabularyCardRevision mergedRevision(
            VocabularyCard card,
            VocabularyCardRevision current,
            Map<String, JsonNode> mergeFields) {
        if (isNewFormatRevision(current)) {
            return mergedNewFormatRevision(card, current, mergeFields);
        }
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

        return resolutionRevision(
                card, current, current, content, "Merged vocabulary conflict fields");
    }

    private VocabularyCardRevision mergedNewFormatRevision(
            VocabularyCard card,
            VocabularyCardRevision current,
            Map<String, JsonNode> mergeFields) {
        ObjectNode core = editableCore(card, parseJson(current.getCoreJson(), "core_json"));
        String markdown = current.getContentMarkdown();
        JsonNode compatibility = revisionContent(current);
        if (markdown == null && compatibility != null && compatibility.path("markdown").isTextual()) {
            markdown = compatibility.path("markdown").asText();
        }
        Map<String, JsonNode> fields = mergeFields == null ? Map.of() : mergeFields;
        for (Map.Entry<String, JsonNode> field : fields.entrySet()) {
            switch (field.getKey()) {
                case "core" -> core = editableCore(card, field.getValue());
                case "markdown" -> markdown = mergedMarkdown(field.getValue());
                default -> throw new IllegalArgumentException("merge field is not allowed: " + field.getKey());
            }
        }
        validateMarkdown(markdown);
        return buildResolutionRevision(
                card, current, current, core, null, markdown,
                current.getContentFormatVersion(), "Merged vocabulary conflict fields");
    }

    private String mergedMarkdown(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("merged markdown must be text or null");
        }
        return value.textValue();
    }

    private VocabularyCardRevision resolutionRevision(
            VocabularyCard card,
            VocabularyCardRevision current,
            VocabularyCardRevision contentRevision,
            JsonNode selectedContent,
            String changeSummary) {
        VocabularyTemplateRegistry.TemplateDefinition template =
                templateRegistry.require(contentRevision.getTemplateKey());
        ObjectNode core;
        ObjectNode legacy = null;
        String markdown = contentRevision.getContentMarkdown();
        boolean newFormat = isNewFormatRevision(contentRevision);
        if (!newFormat) {
            legacy = editableContent(card, selectedContent);
            templateRegistry.validate(template.key(), legacy);
            core = coreCodec.fromLegacy(card.getNormalizedTerm(), legacy);
        } else {
            core = editableCore(card, parseJson(contentRevision.getCoreJson(), "core_json"));
            if (markdown == null && selectedContent != null && selectedContent.path("markdown").isTextual()) {
                markdown = selectedContent.path("markdown").asText();
            }
            validateMarkdown(markdown);
        }
        return buildResolutionRevision(
                card, current, contentRevision, core, legacy, markdown,
                newFormat
                        ? contentRevision.getContentFormatVersion() == null
                                ? CONTENT_FORMAT_VERSION
                                : contentRevision.getContentFormatVersion()
                        : null,
                changeSummary);
    }

    private boolean isNewFormatRevision(VocabularyCardRevision revision) {
        if (revision.getContentFormatVersion() == null) {
            return false;
        }
        JsonNode content = revisionContent(revision);
        return content != null
                && content.isObject()
                && content.path("schemaVersion").isInt()
                && content.path("phonetics").isArray()
                && content.path("senses").isArray();
    }

    private VocabularyCardRevision buildResolutionRevision(
            VocabularyCard card,
            VocabularyCardRevision current,
            VocabularyCardRevision contentRevision,
            ObjectNode core,
            ObjectNode legacy,
            String markdown,
            Integer contentFormatVersion,
            String changeSummary) {
        VocabularyTemplateRegistry.TemplateDefinition template =
                templateRegistry.require(contentRevision.getTemplateKey());
        VocabularyCardRevision resolution = new VocabularyCardRevision();
        resolution.setRevisionUid(uid("rev_"));
        resolution.setCardUid(card.getCardUid());
        resolution.setBaseRevisionUid(current.getRevisionUid());
        resolution.setAuthorType("system_merge");
        resolution.setTemplateKey(template.key());
        resolution.setTemplateVersion(template.version());
        resolution.setThemeUid(contentRevision.getThemeUid());
        resolution.setThemeVersion(contentRevision.getThemeVersion());
        resolution.setContentJson(legacy == null
                ? writeCompatibilityJson(core, markdown)
                : writeJson(legacy));
        resolution.setCoreJson(writeJson(core));
        resolution.setContentMarkdown(markdown);
        resolution.setContentFormatVersion(contentFormatVersion);
        resolution.setChangeSummary(changeSummary);
        return resolution;
    }

    private VocabularyGenerationJob newGenerationJob(
            VocabularyCard card,
            VocabularyTemplateRegistry.TemplateDefinition template,
            ResolvedVocabularyTheme theme,
            String action) {
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
        job.setRequestJson(writeJson(Map.of("action", action)));
        job.setAvailableAt(java.time.LocalDateTime.now());
        return job;
    }

    private ResolvedVocabularyTheme regenerationTheme(
            Long userId,
            VocabularyCard card,
            RegenerateVocabularyCardRequest request) {
        if (request != null && Boolean.TRUE.equals(request.useLatestThemeVersion())) {
            String themeUid = request.themeUid();
            if ((themeUid == null || themeUid.isBlank())
                    && card.getThemeUid() != null && !card.getThemeUid().isBlank()) {
                themeUid = card.getThemeUid();
            }
            return themeService.resolve(userId, themeUid, request.templateKey());
        }
        if (request != null && request.templateKey() != null && !request.templateKey().isBlank()) {
            return themeService.resolve(userId, null, request.templateKey());
        }
        VocabularyCardRevision activeRevision = activeRevision(card);
        String frozenThemeUid = activeRevision != null && activeRevision.getThemeUid() != null
                ? activeRevision.getThemeUid()
                : card.getThemeUid();
        Integer frozenThemeVersion = activeRevision != null && activeRevision.getThemeVersion() != null
                ? activeRevision.getThemeVersion()
                : card.getThemeVersion();
        String frozenTemplateKey = activeRevision != null
                && activeRevision.getTemplateKey() != null
                && !activeRevision.getTemplateKey().isBlank()
                ? activeRevision.getTemplateKey()
                : card.getTemplateKey();
        if (frozenThemeUid != null && !frozenThemeUid.isBlank() && frozenThemeVersion != null) {
            String templateKey = frozenTemplateKey == null || frozenTemplateKey.isBlank()
                    ? DEFAULT_TEMPLATE_KEY
                    : frozenTemplateKey;
            return new ResolvedVocabularyTheme(
                    frozenThemeUid, frozenThemeVersion, "", "", "", 1, templateKey);
        }
        VocabularyTemplateRegistry.TemplateDefinition template = templateRegistry.require(frozenTemplateKey);
        return new ResolvedVocabularyTheme(
                "theme_system_" + template.key(), 1, template.name(), "", "", 1, template.key());
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("vocabulary content cannot be stored", exception);
        }
    }

    private String writeCompatibilityJson(JsonNode core, String markdown) {
        ObjectNode compatibility = ((ObjectNode) core).deepCopy();
        if (markdown == null) {
            compatibility.putNull("markdown");
        } else {
            compatibility.put("markdown", markdown);
        }
        return writeJson(compatibility);
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

    private record SourceSummary(List<String> types, int count) {
        private static final SourceSummary EMPTY = new SourceSummary(List.of(), 0);
    }

    private record RevisionProjection(
            JsonNode content,
            VocabularyThemeSnapshot theme,
            JsonNode core,
            String markdown,
            Integer contentFormatVersion) {
        private static final RevisionProjection EMPTY =
                new RevisionProjection(null, null, null, null, null);
    }
}
