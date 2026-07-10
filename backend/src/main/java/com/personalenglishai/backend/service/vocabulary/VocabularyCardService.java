package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardDetailResponse;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCardSummaryResponse;
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
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

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

    public VocabularyCardService(
            VocabularyCardMapper cards,
            VocabularySourceMapper sources,
            VocabularyRevisionMapper revisions,
            VocabularyGenerationJobMapper jobs,
            UserVocabularyPreferenceMapper preferences,
            VocabularyTemplateRegistry templateRegistry,
            ObjectMapper objectMapper) {
        this.cards = cards;
        this.sources = sources;
        this.revisions = revisions;
        this.jobs = jobs;
        this.preferences = preferences;
        this.templateRegistry = templateRegistry;
        this.objectMapper = objectMapper;
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
        List<VocabularyCardSummaryResponse> items = cards
                .listByUser(userId, keyword, status, sourceType, offset, safeSize)
                .stream()
                .map(card -> toSummary(userId, card))
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
                card.getUpdatedAt());
    }

    private VocabularyCardSummaryResponse toSummary(Long userId, VocabularyCard card) {
        return new VocabularyCardSummaryResponse(
                card.getCardUid(),
                card.getDisplayTerm(),
                card.getNormalizedTerm(),
                card.getTemplateKey(),
                card.getStatus(),
                card.getActiveRevisionUid(),
                sourceTypes(ownedSources(userId, card.getCardUid())),
                card.getLastCapturedAt(),
                card.getUpdatedAt());
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
        return parseJson(revision.getContentJson(), "content_json");
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
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid stored " + field, exception);
        }
    }
}
