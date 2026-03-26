package com.personalenglishai.backend.ai.englishassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisEnglishAssistantConversationStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void saveAndLoadStateShouldUseSingleRedisDocument() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RedisEnglishAssistantConversationStore store = new RedisEnglishAssistantConversationStore(
                redisTemplate,
                new ObjectMapper()
        );

        store.saveDraftState(
                "conv-1",
                "resp-draft-1",
                "hash-1",
                "draft-output",
                "artifact-output",
                "generate",
                new EnglishAssistantTurn("user-1", "assistant-1", "current_draft", "ask"),
                "draft summary",
                1,
                0
        );

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("peai:english-assistant:state:conv-1"),
                valueCaptor.capture(),
                eq(Duration.ofHours(24))
        );
        String savedJson = valueCaptor.getValue();
        assertThat(savedJson).contains("\"draftLastResponseId\":\"resp-draft-1\"");
        assertThat(savedJson).contains("\"draftSummary\":\"draft summary\"");
        assertThat(savedJson).contains("\"draftRecentTurns\"");
        assertThat(savedJson).contains("\"userMessage\":\"user-1\"");
        assertThat(savedJson).contains("\"lastArtifactChain\":\"draft\"");
        assertThat(savedJson).contains("\"lastArtifactResponseId\":\"resp-draft-1\"");

        when(valueOperations.get("peai:english-assistant:state:conv-1"))
                .thenReturn("""
                        {"generalLastResponseId":"resp-general-1","draftLastResponseId":"resp-draft-2","lastDraftHash":"hash-2",
                        "generalLastAssistantOutput":"general-output","draftLastAssistantOutput":"draft-output-2",
                        "lastArtifactChain":"draft","lastArtifactResponseId":"resp-draft-2",
                        "lastArtifactText":"draft-output-2","lastArtifactTaskType":"generate",
                        "generalRecentTurns":[{"userMessage":"u1","assistantMessage":"a1","scope":"english_general","taskType":"ask"}],
                        "draftRecentTurns":[{"userMessage":"u2","assistantMessage":"a2","scope":"current_draft","taskType":"evaluate"}],
                        "generalSummary":"general summary","draftSummary":"draft summary",
                        "generalTurnCount":3,"draftTurnCount":2,"generalSoftOverflowCount":1,"draftSoftOverflowCount":0}
                        """);

        EnglishAssistantConversationState state = store.getState("conv-1");

        assertThat(state.generalLastResponseId()).isEqualTo("resp-general-1");
        assertThat(state.draftLastResponseId()).isEqualTo("resp-draft-2");
        assertThat(state.lastDraftHash()).isEqualTo("hash-2");
        assertThat(state.generalLastAssistantOutput()).isEqualTo("general-output");
        assertThat(state.draftLastAssistantOutput()).isEqualTo("draft-output-2");
        assertThat(state.lastArtifactChain()).isEqualTo("draft");
        assertThat(state.lastArtifactResponseId()).isEqualTo("resp-draft-2");
        assertThat(state.lastArtifactText()).isEqualTo("draft-output-2");
        assertThat(state.lastArtifactTaskType()).isEqualTo("generate");
        assertThat(state.generalRecentTurns()).isEqualTo(List.of(new EnglishAssistantTurn("u1", "a1", "english_general", "ask")));
        assertThat(state.draftRecentTurns()).isEqualTo(List.of(new EnglishAssistantTurn("u2", "a2", "current_draft", "evaluate")));
        assertThat(state.generalSummary()).isEqualTo("general summary");
        assertThat(state.draftSummary()).isEqualTo("draft summary");
        assertThat(state.generalTurnCount()).isEqualTo(3);
        assertThat(state.draftTurnCount()).isEqualTo(2);
        assertThat(state.generalSoftOverflowCount()).isEqualTo(1);
        assertThat(state.draftSoftOverflowCount()).isEqualTo(0);
    }

    @Test
    void clearDraftStateShouldDropDraftArtifactPointer() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("peai:english-assistant:state:conv-2"))
                .thenReturn("""
                        {"generalLastResponseId":"resp-general-1","draftLastResponseId":"resp-draft-2","lastDraftHash":"hash-2",
                        "generalLastAssistantOutput":"general-output","draftLastAssistantOutput":"draft-output-2",
                        "lastArtifactChain":"draft","lastArtifactResponseId":"resp-draft-artifact",
                        "lastArtifactText":"artifact text","lastArtifactTaskType":"generate",
                        "generalRecentTurns":[],"draftRecentTurns":[],"generalSummary":"general summary","draftSummary":"draft summary",
                        "generalTurnCount":1,"draftTurnCount":2,"generalSoftOverflowCount":0,"draftSoftOverflowCount":1}
                        """);

        RedisEnglishAssistantConversationStore store = new RedisEnglishAssistantConversationStore(
                redisTemplate,
                new ObjectMapper()
        );

        store.clearDraftState("conv-2");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("peai:english-assistant:state:conv-2"),
                valueCaptor.capture(),
                eq(Duration.ofHours(24))
        );
        String savedJson = valueCaptor.getValue();
        assertThat(savedJson).contains("\"draftLastResponseId\":null");
        assertThat(savedJson).contains("\"draftLastAssistantOutput\":null");
        assertThat(savedJson).contains("\"lastArtifactChain\":null");
        assertThat(savedJson).contains("\"lastArtifactResponseId\":null");
        assertThat(savedJson).contains("\"lastArtifactText\":null");
    }
}
