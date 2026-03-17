package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.ai.client.QwenService;
import com.personalenglishai.backend.dto.writing.AuditTopicRequest;
import com.personalenglishai.backend.dto.writing.AuditTopicResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTopicServiceTest {

    @Mock
    private QwenService qwenService;

    @Test
    void shouldKeepOriginalTopicWhenModelReturnsGenericSummary() {
        AuditTopicService service = new AuditTopicService(qwenService);
        AuditTopicRequest request = new AuditTopicRequest();
        request.setTopic("Write an essay based on the following drawing. In your essay, you should first describe the drawing, then interpret its meaning, and give your comment on it.");

        when(qwenService.isEnabled()).thenReturn(true);
        when(qwenService.chat(anyString(), anyString()))
                .thenReturn("""
                        {"status":"complete","topic":"根据所给图表写一篇作文","genre":"看图作文","wordRange":"160-200","requirements":"first describe the drawing, then interpret its meaning, and give your comment on it.","message":null}
                        """);

        AuditTopicResponse response = service.audit(request);

        assertThat(response.getTopic()).isEqualTo(request.getTopic());
        assertThat(response.getGenre()).isEqualTo("看图作文");
        assertThat(response.getWordRange()).isEqualTo("160-200");
    }
}
