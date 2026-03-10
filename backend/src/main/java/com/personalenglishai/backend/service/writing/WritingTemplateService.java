package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingTemplateRequest;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse;

public interface WritingTemplateService {
    WritingTemplateResponse extract(WritingTemplateRequest request);
}
