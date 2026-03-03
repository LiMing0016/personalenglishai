package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.PolishRequest;
import com.personalenglishai.backend.dto.writing.PolishResponse;

public interface WritingPolishService {
    PolishResponse polish(PolishRequest request);
}
