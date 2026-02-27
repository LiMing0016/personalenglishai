package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;

/**
 * 写作评分服务：先 mock，后续可接 GPT 等
 */
public interface WritingEvaluateService {

    /**
     * 对作文进行评分与错误检测
     */
    WritingEvaluateResponse evaluate(WritingEvaluateRequest request);
}
