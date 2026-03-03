package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.impl.LanguageToolService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

class LanguageToolRealEssayTest {

    private LanguageToolService createService() throws Exception {
        LanguageToolService svc = new LanguageToolService();
        Field f = LanguageToolService.class.getDeclaredField("enabled");
        f.setAccessible(true);
        f.setBoolean(svc, true);
        return svc;
    }

    @Test
    void checkRealEssay() throws Exception {
        LanguageToolService svc = createService();

        String essay = "Reading is very important for students because it not only improves our knowledge but also helps us become more thoughtful people. In modern society, many students prefer using mobile phones instead of reading books, which has a negative impact on their development.\n" +
                "\n" +
                "Firstly, reading can broaden our horizons. When we read different kinds of books, such as science, history or literature, we can learn a lot of information that cannot\n" +
                "nnot be found in daily life. For example, by reading novels, we are able to understand other\n" +
                " people's feelings and think from their perspectives.\n" +
                "\n" +
                "Secondly, reading also helps students to build good habits of concentration. Nowadays, students are always distracted by short videos and social media, so they find it hard to focus on their studies. If they keep reading every day, they will gradually improve their attention abilities.\n" +
                "\n" +
                "However, some people think reading can be boring and a waste of time. They believe watching videos is more interesting than reading book. In my opinion, this idea is wrong. Although reading may seem difficult at the beginning, but it will bring long-term benefits.\n" +
                "\n" +
                "In conclusion, reading has a great effect on students' growth. We should develop a habit of reading from a young age and make it a part of our daily life. Only in this way can we become better people in the future.";

        List<WritingEvaluateResponse.ErrorDto> errors = svc.check(essay);

        System.out.println("=== LT found " + errors.size() + " errors ===");
        for (var e : errors) {
            System.out.printf("[%s] type=%-15s sev=%-6s span=[%d,%d]%n    orig=\"%s\"%n    sugg=\"%s\"%n    reason=%s%n%n",
                    e.getId(), e.getType(), e.getSeverity(),
                    e.getSpan().getStart(), e.getSpan().getEnd(),
                    e.getOriginal(), e.getSuggestion(), e.getReason());
        }
    }
}
