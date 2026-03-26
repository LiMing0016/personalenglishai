package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisScoreRuntimeStateServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("应保存并读取 doc 级评分运行时状态")
    void shouldSaveAndLoadRuntimeState() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RedisScoreRuntimeStateService service = new RedisScoreRuntimeStateService(redisTemplate, new ObjectMapper());
        ScoreRuntimeState state = new ScoreRuntimeState(
                "gpt-4o",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-hash",
                "rubric-hash",
                "score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2",
                "resp_123",
                "essay-hash"
        );

        service.save("doc_abc", state);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(keyCaptor.getValue()).isEqualTo("peai:score:runtime:doc_abc");

        when(valueOperations.get("peai:score:runtime:doc_abc")).thenReturn(jsonCaptor.getValue());

        ScoreRuntimeState loaded = service.get("doc_abc");
        assertThat(loaded).isEqualTo(state);
    }
}
