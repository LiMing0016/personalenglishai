package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAssetDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationKnowledgeChunkDto;
import com.personalenglishai.backend.entity.translation.TranslationDocumentAssetRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentElementRecord;
import com.personalenglishai.backend.entity.translation.TranslationDocumentParseSnapshotRecord;
import com.personalenglishai.backend.entity.translation.TranslationKnowledgeChunkRecord;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentKnowledgeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TranslationDocumentKnowledgeStore {
    private final ObjectMapper objectMapper;
    private final TranslationDocumentKnowledgeMapper mapper;

    public TranslationDocumentKnowledgeStore(ObjectMapper objectMapper, TranslationDocumentKnowledgeMapper mapper) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Transactional
    public void save(TranslationDocumentParseResponse response) {
        if (response == null || isBlank(response.getDocumentId())) {
            return;
        }
        mapper.deleteByDocumentId(response.getDocumentId());
        mapper.insertSnapshot(toSnapshotRecord(response));
        for (TranslationDocumentElementDto element : response.getElements()) {
            mapper.insertElement(toElementRecord(response.getDocumentId(), element));
        }
        for (TranslationKnowledgeChunkDto chunk : response.getKnowledgeChunks()) {
            mapper.insertChunk(toChunkRecord(response.getDocumentId(), chunk));
        }
        for (TranslationDocumentAssetDto asset : response.getAssets()) {
            mapper.insertAsset(toAssetRecord(response.getDocumentId(), asset));
        }
    }

    public Optional<TranslationDocumentParseResponse> findByDocumentId(String documentId) {
        if (isBlank(documentId)) {
            return Optional.empty();
        }
        TranslationDocumentParseSnapshotRecord record = mapper.findSnapshotByDocumentId(documentId);
        if (record == null || isBlank(record.getResponseJson())) {
            return Optional.empty();
        }
        return Optional.of(read(record.getResponseJson(), TranslationDocumentParseResponse.class));
    }

    public List<TranslationKnowledgeChunkRecord> findChunksByDocumentId(String documentId) {
        if (isBlank(documentId)) {
            return List.of();
        }
        return mapper.selectChunksByDocumentId(documentId);
    }

    private TranslationDocumentParseSnapshotRecord toSnapshotRecord(TranslationDocumentParseResponse response) {
        TranslationDocumentParseSnapshotRecord record = new TranslationDocumentParseSnapshotRecord();
        record.setDocumentId(response.getDocumentId());
        record.setFileName(response.getFileName());
        record.setSourceType(response.getSourceType());
        record.setParseStatus(response.getParseStatus());
        record.setOcrStatus(response.getOcrStatus());
        record.setProvider(response.getProvider());
        record.setParseMode(response.getParseMode());
        record.setFallbackUsed(response.isFallbackUsed());
        record.setPageCount(response.getPageCount());
        record.setBlockCount(response.getBlockCount());
        record.setResponseJson(write(response));
        record.setDiagnosisJson(write(response.getDiagnosis()));
        record.setQualityJson(write(response.getQuality()));
        record.setLanguageProfileJson(write(response.getLanguageProfile()));
        record.setParseJobJson(write(response.getParseJob()));
        return record;
    }

    private TranslationDocumentElementRecord toElementRecord(String documentId, TranslationDocumentElementDto element) {
        TranslationDocumentElementRecord record = new TranslationDocumentElementRecord();
        record.setDocumentId(documentId);
        record.setElementId(element.getId());
        record.setElementType(element.getType());
        record.setElementOrder(element.getOrder());
        record.setPageNumber(element.getPageNumber());
        record.setText(element.getText());
        record.setBbox(element.getBbox());
        record.setProvider(element.getProvider());
        record.setConfidence(element.getConfidence());
        record.setRecognitionStatus(element.getRecognitionStatus());
        record.setQualityScore(element.getQualityScore());
        record.setMetadataJson(write(element.getMetadata()));
        return record;
    }

    private TranslationKnowledgeChunkRecord toChunkRecord(String documentId, TranslationKnowledgeChunkDto chunk) {
        TranslationKnowledgeChunkRecord record = new TranslationKnowledgeChunkRecord();
        record.setDocumentId(documentId);
        record.setChunkId(chunk.getId());
        record.setChunkOrder(chunk.getChunkOrder());
        record.setChunkType(chunk.getChunkType());
        record.setContent(chunk.getContent());
        record.setSummary(chunk.getSummary());
        record.setSourceElementIdsJson(write(chunk.getSourceElementIds()));
        record.setPageNumbersJson(write(chunk.getPageNumbers()));
        record.setFirstPageNumber(chunk.getPageNumbers().isEmpty() ? null : chunk.getPageNumbers().get(0));
        record.setTokenCount(chunk.getTokenCount());
        record.setQualityScore(chunk.getQualityScore());
        record.setEmbeddingStatus(chunk.getEmbeddingStatus());
        record.setGranularity(chunk.getGranularity());
        record.setStartElementOrder(chunk.getStartElementOrder());
        record.setEndElementOrder(chunk.getEndElementOrder());
        record.setSectionPathJson(write(chunk.getSectionPath()));
        record.setParentChunkId(chunk.getParentChunkId());
        record.setPrevChunkId(chunk.getPrevChunkId());
        record.setNextChunkId(chunk.getNextChunkId());
        return record;
    }

    private TranslationDocumentAssetRecord toAssetRecord(String documentId, TranslationDocumentAssetDto asset) {
        TranslationDocumentAssetRecord record = new TranslationDocumentAssetRecord();
        record.setDocumentId(documentId);
        record.setAssetId(asset.getId());
        record.setAssetType(asset.getAssetType());
        record.setPageNumber(asset.getPageNumber());
        record.setSourceElementId(asset.getSourceElementId());
        record.setBbox(asset.getBbox());
        record.setRecognizedText(asset.getRecognizedText());
        record.setProvider(asset.getProvider());
        record.setRecognitionStatus(asset.getRecognitionStatus());
        record.setConfidence(asset.getConfidence());
        record.setMetadataJson(write(asset.getMetadata()));
        return record;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize translation document knowledge", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize translation document knowledge", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
