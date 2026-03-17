package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.RewriteApplyRequest;
import com.personalenglishai.backend.dto.writing.RewriteApplyResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;

import java.util.List;

public interface TrustedRewriteService {

    RewriteApplyResponse applyTrustedRewrite(Long userId, RewriteApplyRequest request);

    List<WritingEvaluateResponse.ErrorDto> filterTrustedTrinkaSuggestions(
            Long userId,
            String docId,
            String text,
            List<WritingEvaluateResponse.ErrorDto> errors);

    void clearTrustedRewrites(Long userId, String docId);
}
