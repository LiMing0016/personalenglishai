package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.vocabulary.VocabularyConflictResponse;

public class VocabularyRevisionConflictException extends BizException {
    private final VocabularyConflictResponse conflict;

    public VocabularyRevisionConflictException(VocabularyConflictResponse conflict) {
        super(ErrorCode.VOCABULARY_REVISION_CONFLICT);
        this.conflict = conflict;
    }

    public VocabularyConflictResponse getConflict() {
        return conflict;
    }
}
