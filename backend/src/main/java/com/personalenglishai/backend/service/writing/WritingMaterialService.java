package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingMaterialRequest;
import com.personalenglishai.backend.dto.writing.WritingMaterialResponse;

public interface WritingMaterialService {
    WritingMaterialResponse generate(WritingMaterialRequest request);
}
