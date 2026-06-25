package com.personalenglishai.backend.service.learning;

import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeRequest;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeResponse;
import com.personalenglishai.backend.service.assistant.PythonAssistantClient;
import org.springframework.stereotype.Service;

@Service
public class LearningCanvasOrganizeService {
    private final PythonAssistantClient pythonAssistantClient;

    public LearningCanvasOrganizeService(PythonAssistantClient pythonAssistantClient) {
        this.pythonAssistantClient = pythonAssistantClient;
    }

    public LearningCanvasOrganizeResponse organize(LearningCanvasOrganizeRequest request) {
        return pythonAssistantClient.organizeLearningAsset(request);
    }
}
