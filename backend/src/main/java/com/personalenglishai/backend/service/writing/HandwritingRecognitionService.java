package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageRequest;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageResponse;

public interface HandwritingRecognitionService {

    RecognizeHandwritingImageResponse recognize(RecognizeHandwritingImageRequest request);
}
