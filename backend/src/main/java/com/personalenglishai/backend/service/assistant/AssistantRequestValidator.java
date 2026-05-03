package com.personalenglishai.backend.service.assistant;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.dto.assistant.AssistantAttachmentRef;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssistantRequestValidator {
    public void validateForAgentRun(AssistantRequest request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        if (isBlank(request.getClientMessageId())) {
            throw invalid("clientMessageId 不能为空");
        }
        List<AssistantAttachmentRef> attachments = request.getAttachments() == null
                ? List.of()
                : request.getAttachments();
        if (attachments.size() > 5) {
            throw invalid("附件最多支持 5 个");
        }
        boolean hasMessage = request.getMessage() != null && !isBlank(request.getMessage().getText());
        boolean hasSelection = request.getSelection() != null && !isBlank(request.getSelection().getText());
        boolean hasAttachments = !attachments.isEmpty();
        if (!hasMessage && !hasSelection && !hasAttachments) {
            throw invalid("message.text、selection.text、attachments 至少需要一个");
        }
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.COMMON_VALIDATION_ERROR, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
