package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ThirdPartyLayoutDocumentParseProvider implements DocumentParseProvider {
    private final boolean enabled;

    public ThirdPartyLayoutDocumentParseProvider(
            @Value("${app.document-parse.third-party.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return enabled && "PDF".equalsIgnoreCase(request.fileType());
    }

    @Override
    public DocumentParseProviderType providerType() {
        return DocumentParseProviderType.THIRD_PARTY_LAYOUT;
    }

    @Override
    public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
        throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "第三方高质量解析 Provider 尚未配置");
    }
}
