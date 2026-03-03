package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.impl.LanguageToolService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

class LanguageToolDemoTest {

    private LanguageToolService createService() throws Exception {
        LanguageToolService svc = new LanguageToolService();
        Field f = LanguageToolService.class.getDeclaredField("enabled");
        f.setAccessible(true);
        f.setBoolean(svc, true);
        return svc;
    }

    @Test
    void demoFullErrors() throws Exception {
        LanguageToolService svc = createService();

        // 模拟一篇典型中国高中生作文（含常见错误）
        String essay = """
                Last weekend I go to the park with my freinds. We have a very good time there. \
                The weather is sunny and warm. We played football and flied kites. \
                I think outdoor activities is very important for students. \
                Firstly it can make us healthy. Secondly it can help us relax. \
                Everyone have their own hobbys. My mother always tell me to do more exercise. \
                I am agree with her. In my opinion we should do more excercise in our daily life. \
                He go to school everyday. She don't like running. \
                I hope everyone can join us next time and has a wonderful experience.""";

        List<WritingEvaluateResponse.ErrorDto> errors = svc.check(essay);

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  LanguageTool 检测结果 — 共 " + errors.size() + " 个错误");
        System.out.println("═══════════════════════════════════════════════════════════════");
        for (var e : errors) {
            System.out.println();
            System.out.printf("  [%s] type=%-15s severity=%-6s category=%s%n",
                    e.getId(), e.getType(), e.getSeverity(), e.getCategory());
            System.out.printf("  span: [%d, %d]%n",
                    e.getSpan().getStart(), e.getSpan().getEnd());
            System.out.printf("  original:   \"%s\"%n", e.getOriginal());
            System.out.printf("  suggestion: \"%s\"%n", e.getSuggestion());
            System.out.printf("  reason:     %s%n", e.getReason());
            System.out.println("  ───────────────────────────────────────────────────────────");
        }
    }
}
