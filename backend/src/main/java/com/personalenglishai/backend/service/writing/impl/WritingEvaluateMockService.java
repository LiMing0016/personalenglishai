package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 写作评分 mock 实现：固定返回结构，便于前端联调；后续可替换为 GPT 实现
 */
@Service
public class WritingEvaluateMockService implements WritingEvaluateService {

    @Override
    public WritingEvaluateResponse evaluate(WritingEvaluateRequest request) {
        String requestId = "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        WritingEvaluateResponse.ScoreDto score = new WritingEvaluateResponse.ScoreDto();
        score.setOverall(72);
        score.setTask(18);
        score.setCoherence(16);
        score.setLexical(14);
        score.setGrammar(24);

        String summary = "整体完成度不错，结构清晰。建议重点注意时态一致性与冠词使用，词汇上可适当增加衔接词。订正后可再次提交评估。";

        List<WritingEvaluateResponse.ErrorDto> errors = new ArrayList<>();
        errors.add(createError("e1", "grammar", "major", 0, 15, "Consider using past tense for past events."));
        errors.add(createError("e2", "word_choice", "minor", 20, 28, "Consider \"an\" before vowel sounds."));
        errors.add(createError("e3", "grammar", "minor", 35, 42, "Use \"do\" with homework."));

        WritingEvaluateResponse response = new WritingEvaluateResponse();
        response.setRequestId(requestId);
        response.setScore(score);
        response.setSummary(summary);
        response.setErrors(errors);
        return response;
    }

    private static WritingEvaluateResponse.ErrorDto createError(
            String id, String type, String severity, int start, int end, String suggestion) {
        WritingEvaluateResponse.ErrorDto dto = new WritingEvaluateResponse.ErrorDto();
        dto.setId(id);
        dto.setType(type);
        dto.setSeverity(severity);
        dto.setSpan(new WritingEvaluateResponse.SpanDto(start, end));
        dto.setSuggestion(suggestion);
        return dto;
    }
}
