package com.personalenglishai.backend.service.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextResult;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.entity.learning.LearningRawCandidate;
import com.personalenglishai.backend.entity.learning.LearningExtractionRun;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import com.personalenglishai.backend.mapper.learning.LearningEvidenceMapper;
import com.personalenglishai.backend.mapper.learning.LearningExtractionRunMapper;
import com.personalenglishai.backend.mapper.learning.LearningRawCandidateMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningDeepseekCleaningServiceTest {

    @Test
    void processRunCallsModelAndPersistsCandidates() {
        LearningExtractionRunMapper runMapper = mock(LearningExtractionRunMapper.class);
        LearningRawCandidateMapper candidateMapper = mock(LearningRawCandidateMapper.class);
        LearningEvidenceMapper evidenceMapper = mock(LearningEvidenceMapper.class);
        AssistantMessageMapper messageMapper = mock(AssistantMessageMapper.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);

        LearningExtractionRun run = new LearningExtractionRun();
        run.setRunUid("lrun-1");
        run.setUserId(1L);
        run.setConversationUid("conv-1");
        run.setMessageUid("msg-1");

        AssistantMessage message = new AssistantMessage();
        message.setMessageUid("msg-1");
        message.setRole("assistant");
        message.setContent("You can say: contribute to a more positive global perception.");
        when(messageMapper.findByMessageUid("msg-1")).thenReturn(message);
        when(openAiClient.createTextResponse(any())).thenReturn(new OpenAiResponsesTextResult(
                "resp-1",
                "{\"candidates\":[{\"type\":\"phrase\",\"text\":\"positive global perception\",\"reason\":\"useful writing phrase\",\"confidence\":0.82}]}",
                100,
                20,
                30,
                null,
                130,
                500));

        LearningRawCandidate persisted = new LearningRawCandidate();
        persisted.setCandidateUid("lcand-1");
        when(candidateMapper.findByDedupeKey(eq(1L), eq("phrase"), eq("positive global perception"), eq("deepseek")))
                .thenReturn(persisted);

        LearningDeepseekCleaningService service = new LearningDeepseekCleaningService(
                runMapper,
                candidateMapper,
                evidenceMapper,
                messageMapper,
                openAiClient,
                new ObjectMapper(),
                "deepseek-chat");

        service.processRun(run);

        verify(runMapper).markProcessing("lrun-1");
        verify(openAiClient).createTextResponse(any());
        verify(candidateMapper).insertOrUpdateOccurrence(any());
        verify(evidenceMapper).insert(any());
        verify(runMapper).updateCompleted(eq("lrun-1"), eq("deepseek-chat"), eq(100L), eq(30L), any());
    }
}
