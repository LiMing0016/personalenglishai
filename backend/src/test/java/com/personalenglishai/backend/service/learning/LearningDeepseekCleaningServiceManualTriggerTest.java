package com.personalenglishai.backend.service.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.entity.learning.LearningExtractionRun;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import com.personalenglishai.backend.mapper.learning.LearningEvidenceMapper;
import com.personalenglishai.backend.mapper.learning.LearningExtractionRunMapper;
import com.personalenglishai.backend.mapper.learning.LearningRawCandidateMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningDeepseekCleaningServiceManualTriggerTest {

    @Test
    void processPendingRunsForUserDayQueriesThatDayAndProcessesRuns() {
        LearningExtractionRunMapper runMapper = mock(LearningExtractionRunMapper.class);
        LearningDeepseekCleaningService service = newService(runMapper, mock(AssistantMessageMapper.class), mock(OpenAiClient.class));

        LearningExtractionRun run = new LearningExtractionRun();
        run.setRunUid("lrun-1");
        run.setMessageUid("missing-message");
        when(runMapper.selectPendingByExtractorAndUserCreatedRange(
                eq("deepseek"),
                eq(1L),
                eq(LocalDateTime.of(2026, 5, 17, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 18, 0, 0)),
                eq(20))).thenReturn(List.of(run));

        int processed = service.processPendingRunsForUserDay(1L, LocalDate.of(2026, 5, 17), 20);

        assertThat(processed).isZero();
        verify(runMapper).selectPendingByExtractorAndUserCreatedRange(
                "deepseek",
                1L,
                LocalDateTime.of(2026, 5, 17, 0, 0),
                LocalDateTime.of(2026, 5, 18, 0, 0),
                20);
        verify(runMapper).updateFailed("lrun-1", "assistant message not found or empty");
    }

    @Test
    void processMessageCreatesPendingRunWhenMessageHasNoDeepseekRun() {
        LearningExtractionRunMapper runMapper = mock(LearningExtractionRunMapper.class);
        AssistantMessageMapper messageMapper = mock(AssistantMessageMapper.class);
        LearningDeepseekCleaningService service = newService(runMapper, messageMapper, mock(OpenAiClient.class));

        AssistantMessage message = new AssistantMessage();
        message.setMessageUid("msg-1");
        message.setConversationUid("conv-1");
        message.setUserId(1L);
        message.setRole("assistant");
        message.setContent("");
        when(messageMapper.findByMessageUid("msg-1")).thenReturn(message);

        boolean processed = service.processMessage("msg-1");

        assertThat(processed).isFalse();
        verify(runMapper).insert(any(LearningExtractionRun.class));
        verify(runMapper).updateFailed(any(), eq("assistant message not found or empty"));
    }

    private LearningDeepseekCleaningService newService(
            LearningExtractionRunMapper runMapper,
            AssistantMessageMapper messageMapper,
            OpenAiClient openAiClient) {
        return new LearningDeepseekCleaningService(
                runMapper,
                mock(LearningRawCandidateMapper.class),
                mock(LearningEvidenceMapper.class),
                messageMapper,
                openAiClient,
                new ObjectMapper(),
                "deepseek-chat");
    }
}
