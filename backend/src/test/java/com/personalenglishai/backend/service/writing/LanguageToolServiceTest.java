package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.impl.LanguageToolService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguageToolServiceTest {

    private LanguageToolService createService(boolean enabled) throws Exception {
        LanguageToolService svc = new LanguageToolService();
        Field f = LanguageToolService.class.getDeclaredField("enabled");
        f.setAccessible(true);
        f.setBoolean(svc, enabled);
        return svc;
    }

    @Test
    void checkFindsGrammarErrors() throws Exception {
        LanguageToolService svc = createService(true);
        // "I go to school yesterday" — tense error
        // "He have a book" — subject-verb agreement
        String essay = "I go to school yesterday. He have a book.";
        List<WritingEvaluateResponse.ErrorDto> errors = svc.check(essay);

        assertNotNull(errors);
        assertFalse(errors.isEmpty(), "Should find at least one error");

        // Every error must have span, original, category=error
        for (var e : errors) {
            assertNotNull(e.getSpan(), "span must not be null");
            assertTrue(e.getSpan().getStart() >= 0);
            assertTrue(e.getSpan().getEnd() > e.getSpan().getStart());
            assertNotNull(e.getOriginal());
            assertEquals("error", e.getCategory());
            assertNotNull(e.getType());
            assertNotNull(e.getReason());
        }
    }

    @Test
    void disabledReturnsEmpty() throws Exception {
        LanguageToolService svc = createService(false);
        List<WritingEvaluateResponse.ErrorDto> errors = svc.check("He have a book.");
        assertTrue(errors.isEmpty());
    }

    @Test
    void blankEssayReturnsEmpty() throws Exception {
        LanguageToolService svc = createService(true);
        assertTrue(svc.check("").isEmpty());
        assertTrue(svc.check(null).isEmpty());
        assertTrue(svc.check("   ").isEmpty());
    }
}
