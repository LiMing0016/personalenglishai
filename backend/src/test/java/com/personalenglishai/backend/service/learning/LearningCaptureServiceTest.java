package com.personalenglishai.backend.service.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.learning.LearningExtractionRun;
import com.personalenglishai.backend.entity.learning.LearningRawCandidate;
import com.personalenglishai.backend.mapper.learning.LearningEvidenceMapper;
import com.personalenglishai.backend.mapper.learning.LearningExtractionRunMapper;
import com.personalenglishai.backend.mapper.learning.LearningRawCandidateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningCaptureServiceTest {

    @Test
    void captureMessageCreatesLocalAndDeepseekRunsThenPersistsEvidenceCandidate() {
        LearningExtractionRunMapper runMapper = mock(LearningExtractionRunMapper.class);
        LearningRawCandidateMapper candidateMapper = mock(LearningRawCandidateMapper.class);
        LearningEvidenceMapper evidenceMapper = mock(LearningEvidenceMapper.class);
        LearningLocalCandidateExtractor extractor = mock(LearningLocalCandidateExtractor.class);

        when(runMapper.findByMessageAndExtractor(eq("msg-1"), eq("local"))).thenReturn(null);
        when(runMapper.findByMessageAndExtractor(eq("msg-1"), eq("deepseek"))).thenReturn(null);
        when(runMapper.findByRunUid(any())).thenAnswer(invocation -> {
            LearningExtractionRun run = new LearningExtractionRun();
            run.setRunUid(invocation.getArgument(0));
            return run;
        });
        when(extractor.extract(any())).thenReturn(List.of(new LearningLocalCandidateExtractor.ExtractedCandidate(
                "phrase",
                "global perceptions of Chinese manufacturing",
                "global perceptions of chinese manufacturing",
                1,
                BigDecimal.valueOf(72),
                "{\"signals\":[\"list_item\"]}")));
        LearningRawCandidate persisted = new LearningRawCandidate();
        persisted.setCandidateUid("lcand-1");
        persisted.setComparisonStatus("local_only");
        when(candidateMapper.findByDedupeKey(
                eq(1L),
                eq("phrase"),
                eq("global perceptions of chinese manufacturing"),
                eq("local"))).thenReturn(persisted);

        LearningCaptureService service = new LearningCaptureService(
                runMapper,
                candidateMapper,
                evidenceMapper,
                extractor,
                new ObjectMapper());

        service.captureMessage(
                1L,
                "conv-1",
                "msg-1",
                "assistant",
                "- global perceptions of Chinese manufacturing");

        ArgumentCaptor<LearningExtractionRun> runCaptor = ArgumentCaptor.forClass(LearningExtractionRun.class);
        verify(runMapper, org.mockito.Mockito.times(2)).insert(runCaptor.capture());
        assertThat(runCaptor.getAllValues())
                .extracting(LearningExtractionRun::getExtractorType)
                .containsExactly("local", "deepseek");

        verify(runMapper).markProcessing(any());
        verify(runMapper).updateCompleted(any(), eq("local-regex-v1"), eq(null), eq(null), any());
        verify(candidateMapper).insertOrUpdateOccurrence(any(LearningRawCandidate.class));
        verify(evidenceMapper).insert(any());
    }
}
