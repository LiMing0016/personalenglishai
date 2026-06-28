package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.dto.learning.LearningNoteRequest;
import com.personalenglishai.backend.entity.learning.LearningNote;
import com.personalenglishai.backend.mapper.learning.LearningNoteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningNoteServiceTest {

    @Mock
    private LearningNoteMapper mapper;

    @Test
    void createVocabularyNoteNormalizesTypeAndTrimsTitle() {
        LearningNoteService service = serviceWithStableUid();
        LearningNoteRequest request = new LearningNoteRequest();
        request.setType("VOCABULARY");
        request.setTitle("  nuanced  ");
        request.setContentMarkdown("# nuanced");
        request.setSourceConversationId("conv-1");
        request.setSourceMessageId("msg-1");
        request.setSourceText("A nuanced answer considers different sides.");

        LearningNote stored = note("note-1", 7L, "vocabulary", "nuanced", "# nuanced");
        when(mapper.selectByUidForUser(eq(7L), eq("note-1"))).thenReturn(stored);

        var response = service.create(7L, request);

        ArgumentCaptor<LearningNote> noteCaptor = ArgumentCaptor.forClass(LearningNote.class);
        verify(mapper).insert(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getType()).isEqualTo("vocabulary");
        assertThat(noteCaptor.getValue().getTitle()).isEqualTo("nuanced");
        assertThat(noteCaptor.getValue().getContentMarkdown()).isEqualTo("# nuanced");
        assertThat(noteCaptor.getValue().getSourceConversationUid()).isEqualTo("conv-1");
        assertThat(noteCaptor.getValue().getSourceMessageUid()).isEqualTo("msg-1");
        assertThat(response.getNoteUid()).isEqualTo("note-1");
        assertThat(response.getType()).isEqualTo("vocabulary");
    }

    @Test
    void createRejectsBlankMarkdown() {
        LearningNoteService service = serviceWithStableUid();
        LearningNoteRequest request = new LearningNoteRequest();
        request.setType("vocabulary");
        request.setTitle("nuanced");
        request.setContentMarkdown(" ");

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentMarkdown");
    }

    @Test
    void listVocabularyNotesUsesSafePaging() {
        LearningNoteService service = serviceWithStableUid();
        when(mapper.selectByUserAndType(7L, "vocabulary", 0, 20))
                .thenReturn(List.of(note("note-1", 7L, "vocabulary", "nuanced", "# nuanced")));
        when(mapper.countByUserAndType(7L, "vocabulary")).thenReturn(1L);

        var page = service.list(7L, "vocabulary", 0, 200);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
    }

    private LearningNoteService serviceWithStableUid() {
        return new LearningNoteService(mapper) {
            @Override
            String createNoteUid() {
                return "note-1";
            }
        };
    }

    private LearningNote note(String uid, Long userId, String type, String title, String markdown) {
        LearningNote note = new LearningNote();
        note.setNoteUid(uid);
        note.setUserId(userId);
        note.setType(type);
        note.setTitle(title);
        note.setContentMarkdown(markdown);
        note.setStatus("active");
        note.setCreatedAt(LocalDateTime.of(2026, 6, 24, 10, 0));
        note.setUpdatedAt(LocalDateTime.of(2026, 6, 24, 10, 0));
        return note;
    }
}
