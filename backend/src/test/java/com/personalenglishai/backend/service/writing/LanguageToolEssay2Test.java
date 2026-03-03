package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.impl.LanguageToolService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

class LanguageToolEssay2Test {

    private LanguageToolService createService() throws Exception {
        LanguageToolService svc = new LanguageToolService();
        Field f = LanguageToolService.class.getDeclaredField("enabled");
        f.setAccessible(true);
        f.setBoolean(svc, true);
        return svc;
    }

    @Test
    void checkEssay2() throws Exception {
        LanguageToolService svc = createService();

        String essay = "Nowadays, more and more students choose to do part-time jobs while they are studying at school. Some people believes that this experience can help students develop important skills, while others think it may have negative effects on their academic performance. In my opinion, students can benefit from part-time jobs if they manage their time properly.\n" +
                "\n" +
                "First of all, doing part-time jobs give students the opportunity to learn how to communicate with different kinds of people. For example, students who works in restaurants or shops can improve their social skills and become more confident. This kind of experience are difficult to gain only from textbooks.\n" +
                "\n" +
                "However, there are also some disadvantages. If students spends too much time on work, they may not have enough time to focus on their studies. As a result, their grades might be affected. Besides, some jobs are too tired for students, which makes them feel stressful and exhausting.\n" +
                "\n" +
                "In addition, working too early may influence to students' values. Some students may care more about earning money than improving themselves. Although part-time jobs can provide useful experiences, but students should not ignore the importance of education.\n" +
                "\n" +
                "In conclusion, part-time jobs has both advantages and disadvantages. Students should make a careful decision before taking such jobs, and balance between work and study in order to achieve a better future.";

        List<WritingEvaluateResponse.ErrorDto> errors = svc.check(essay);

        System.out.println("=== LT found " + errors.size() + " errors ===\n");
        for (var e : errors) {
            System.out.printf("[%s] type=%-15s sev=%-6s span=[%d,%d]%n    orig=\"%s\"%n    sugg=\"%s\"%n    reason=%s%n%n",
                    e.getId(), e.getType(), e.getSeverity(),
                    e.getSpan().getStart(), e.getSpan().getEnd(),
                    e.getOriginal(), e.getSuggestion(), e.getReason());
        }
    }
}
