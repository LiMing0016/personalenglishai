package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;

public interface WritingEvaluateTaskService {

    WritingEvaluateTaskResponse submit(WritingEvaluateRequest request);

    WritingEvaluateTaskResponse getTask(String requestId);
}
