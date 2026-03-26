package com.personalenglishai.backend.ai.englishassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisEnglishAssistantConversationStore implements EnglishAssistantConversationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisEnglishAssistantConversationStore.class);
    private static final String KEY_PREFIX = "peai:english-assistant:state:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisEnglishAssistantConversationStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public EnglishAssistantConversationState getState(String conversationId) {
        if (isBlank(conversationId)) {
            return new EnglishAssistantConversationState(null, null, null, null, null);
        }
        try {
            String raw = redisTemplate.opsForValue().get(key(conversationId));
            if (isBlank(raw)) {
                return new EnglishAssistantConversationState(null, null, null, null, null);
            }
            RedisStateDocument doc = objectMapper.readValue(raw, RedisStateDocument.class);
            return new EnglishAssistantConversationState(
                    blankToNull(doc.generalLastResponseId),
                    blankToNull(doc.draftLastResponseId),
                    blankToNull(doc.lastDraftHash),
                    blankToNull(doc.generalLastAssistantOutput),
                    blankToNull(doc.draftLastAssistantOutput),
                    blankToNull(doc.lastArtifactChain),
                    blankToNull(doc.lastArtifactResponseId),
                    blankToNull(doc.lastArtifactText),
                    blankToNull(doc.lastArtifactTaskType),
                    doc.generalRecentTurns,
                    doc.draftRecentTurns,
                    blankToNull(doc.generalSummary),
                    blankToNull(doc.draftSummary),
                    doc.generalTurnCount,
                    doc.draftTurnCount,
                    doc.generalSoftOverflowCount,
                    doc.draftSoftOverflowCount
            );
        } catch (Exception e) {
            log.warn("english assistant conversation state read failed conversationId={} error={}", conversationId, e.getMessage());
            return new EnglishAssistantConversationState(null, null, null, null, null);
        }
    }

    @Override
    public void saveGeneralState(String conversationId,
                                 String responseId,
                                 String assistantOutput,
                                 String artifactText,
                                 String artifactTaskType,
                                 EnglishAssistantTurn turn,
                                 String summary,
                                 int turnCount,
                                 int softOverflowCount) {
        if (isBlank(conversationId) || isBlank(responseId)) {
            return;
        }
        EnglishAssistantConversationState current = getState(conversationId);
        write(conversationId, new RedisStateDocument(
                responseId,
                current.draftLastResponseId(),
                current.lastDraftHash(),
                blankToNull(assistantOutput),
                current.draftLastAssistantOutput(),
                artifactText == null ? current.lastArtifactChain() : "general",
                artifactText == null ? current.lastArtifactResponseId() : responseId,
                blankToNull(artifactText == null ? current.lastArtifactText() : artifactText),
                blankToNull(artifactText == null ? current.lastArtifactTaskType() : artifactTaskType),
                appendTurn(current.generalRecentTurns(), turn),
                current.draftRecentTurns(),
                blankToNull(summary),
                current.draftSummary(),
                turnCount,
                current.draftTurnCount(),
                softOverflowCount,
                current.draftSoftOverflowCount()
        ));
    }

    @Override
    public void saveDraftState(String conversationId,
                               String responseId,
                               String draftHash,
                               String assistantOutput,
                               String artifactText,
                               String artifactTaskType,
                               EnglishAssistantTurn turn,
                               String summary,
                               int turnCount,
                               int softOverflowCount) {
        if (isBlank(conversationId) || isBlank(responseId)) {
            return;
        }
        EnglishAssistantConversationState current = getState(conversationId);
        write(conversationId, new RedisStateDocument(
                current.generalLastResponseId(),
                responseId,
                blankToNull(draftHash),
                current.generalLastAssistantOutput(),
                blankToNull(assistantOutput),
                artifactText == null ? current.lastArtifactChain() : "draft",
                artifactText == null ? current.lastArtifactResponseId() : responseId,
                blankToNull(artifactText == null ? current.lastArtifactText() : artifactText),
                blankToNull(artifactText == null ? current.lastArtifactTaskType() : artifactTaskType),
                current.generalRecentTurns(),
                appendTurn(current.draftRecentTurns(), turn),
                current.generalSummary(),
                blankToNull(summary),
                current.generalTurnCount(),
                turnCount,
                current.generalSoftOverflowCount(),
                softOverflowCount
        ));
    }

    @Override
    public void clearDraftState(String conversationId) {
        if (isBlank(conversationId)) {
            return;
        }
        EnglishAssistantConversationState current = getState(conversationId);
        boolean preserveArtifact = !"draft".equals(current.lastArtifactChain());
        write(conversationId, new RedisStateDocument(
                current.generalLastResponseId(),
                null,
                null,
                current.generalLastAssistantOutput(),
                null,
                preserveArtifact ? current.lastArtifactChain() : null,
                preserveArtifact ? current.lastArtifactResponseId() : null,
                preserveArtifact ? current.lastArtifactText() : null,
                preserveArtifact ? current.lastArtifactTaskType() : null,
                current.generalRecentTurns(),
                java.util.List.of(),
                current.generalSummary(),
                null,
                current.generalTurnCount(),
                0,
                current.generalSoftOverflowCount(),
                0
        ));
    }

    private void write(String conversationId, RedisStateDocument document) {
        try {
            redisTemplate.opsForValue().set(key(conversationId), objectMapper.writeValueAsString(document), TTL);
        } catch (Exception e) {
            log.warn("english assistant conversation state write failed conversationId={} error={}", conversationId, e.getMessage());
        }
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private java.util.List<EnglishAssistantTurn> appendTurn(java.util.List<EnglishAssistantTurn> turns,
                                                            EnglishAssistantTurn turn) {
        java.util.List<EnglishAssistantTurn> result = new java.util.ArrayList<>(turns == null ? java.util.List.of() : turns);
        if (turn != null) {
            result.add(turn);
        }
        int maxTurns = 4;
        if (result.size() > maxTurns) {
            return java.util.List.copyOf(result.subList(result.size() - maxTurns, result.size()));
        }
        return java.util.List.copyOf(result);
    }

    private static class RedisStateDocument {
        public String generalLastResponseId;
        public String draftLastResponseId;
        public String lastDraftHash;
        public String generalLastAssistantOutput;
        public String draftLastAssistantOutput;
        public String lastArtifactChain;
        public String lastArtifactResponseId;
        public String lastArtifactText;
        public String lastArtifactTaskType;
        public java.util.List<EnglishAssistantTurn> generalRecentTurns;
        public java.util.List<EnglishAssistantTurn> draftRecentTurns;
        public String generalSummary;
        public String draftSummary;
        public int generalTurnCount;
        public int draftTurnCount;
        public int generalSoftOverflowCount;
        public int draftSoftOverflowCount;

        public RedisStateDocument() {
        }

        public RedisStateDocument(String generalLastResponseId,
                                  String draftLastResponseId,
                                  String lastDraftHash,
                                  String generalLastAssistantOutput,
                                  String draftLastAssistantOutput,
                                  String lastArtifactChain,
                                  String lastArtifactResponseId,
                                  String lastArtifactText,
                                  String lastArtifactTaskType,
                                  java.util.List<EnglishAssistantTurn> generalRecentTurns,
                                  java.util.List<EnglishAssistantTurn> draftRecentTurns,
                                  String generalSummary,
                                  String draftSummary,
                                  int generalTurnCount,
                                  int draftTurnCount,
                                  int generalSoftOverflowCount,
                                  int draftSoftOverflowCount) {
            this.generalLastResponseId = generalLastResponseId;
            this.draftLastResponseId = draftLastResponseId;
            this.lastDraftHash = lastDraftHash;
            this.generalLastAssistantOutput = generalLastAssistantOutput;
            this.draftLastAssistantOutput = draftLastAssistantOutput;
            this.lastArtifactChain = lastArtifactChain;
            this.lastArtifactResponseId = lastArtifactResponseId;
            this.lastArtifactText = lastArtifactText;
            this.lastArtifactTaskType = lastArtifactTaskType;
            this.generalRecentTurns = generalRecentTurns;
            this.draftRecentTurns = draftRecentTurns;
            this.generalSummary = generalSummary;
            this.draftSummary = draftSummary;
            this.generalTurnCount = generalTurnCount;
            this.draftTurnCount = draftTurnCount;
            this.generalSoftOverflowCount = generalSoftOverflowCount;
            this.draftSoftOverflowCount = draftSoftOverflowCount;
        }
    }
}
