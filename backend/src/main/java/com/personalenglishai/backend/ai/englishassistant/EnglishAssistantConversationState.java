package com.personalenglishai.backend.ai.englishassistant;

public record EnglishAssistantConversationState(
        String generalLastResponseId,
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
        int draftSoftOverflowCount
) {

    public EnglishAssistantConversationState {
        generalRecentTurns = generalRecentTurns == null ? java.util.List.of() : java.util.List.copyOf(generalRecentTurns);
        draftRecentTurns = draftRecentTurns == null ? java.util.List.of() : java.util.List.copyOf(draftRecentTurns);
        generalSummary = blankToNull(generalSummary);
        draftSummary = blankToNull(draftSummary);
        generalLastResponseId = blankToNull(generalLastResponseId);
        draftLastResponseId = blankToNull(draftLastResponseId);
        lastDraftHash = blankToNull(lastDraftHash);
        generalLastAssistantOutput = blankToNull(generalLastAssistantOutput);
        draftLastAssistantOutput = blankToNull(draftLastAssistantOutput);
        lastArtifactChain = blankToNull(lastArtifactChain);
        lastArtifactResponseId = blankToNull(lastArtifactResponseId);
        lastArtifactText = blankToNull(lastArtifactText);
        lastArtifactTaskType = blankToNull(lastArtifactTaskType);
    }

    public EnglishAssistantConversationState(String generalLastResponseId,
                                             String draftLastResponseId,
                                             String lastDraftHash,
                                             String generalLastAssistantOutput,
                                             String draftLastAssistantOutput) {
        this(generalLastResponseId, draftLastResponseId, lastDraftHash, generalLastAssistantOutput, draftLastAssistantOutput,
                null, null, null, null,
                java.util.List.of(), java.util.List.of(), null, null, 0, 0, 0, 0);
    }

    public EnglishAssistantConversationState withoutDraftChain() {
        boolean preserveArtifact = !"draft".equals(lastArtifactChain);
        return new EnglishAssistantConversationState(
                generalLastResponseId,
                null,
                null,
                generalLastAssistantOutput,
                null,
                preserveArtifact ? lastArtifactChain : null,
                preserveArtifact ? lastArtifactResponseId : null,
                preserveArtifact ? lastArtifactText : null,
                preserveArtifact ? lastArtifactTaskType : null,
                generalRecentTurns,
                java.util.List.of(),
                generalSummary,
                null,
                generalTurnCount,
                0,
                generalSoftOverflowCount,
                0
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
