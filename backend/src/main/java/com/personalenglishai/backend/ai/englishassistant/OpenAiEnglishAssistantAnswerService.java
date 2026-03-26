package com.personalenglishai.backend.ai.englishassistant;

import org.springframework.stereotype.Service;

@Service
public class OpenAiEnglishAssistantAnswerService implements EnglishAssistantAnswerService {

    private final OpenAiEnglishAssistantClient client;

    public OpenAiEnglishAssistantAnswerService(OpenAiEnglishAssistantClient client) {
        this.client = client;
    }

    @Override
    public EnglishAssistantAnswerResult answer(EnglishAssistantAnswerRequest request) {
        return client.answer(request);
    }

    @Override
    public EnglishAssistantAnswerResult streamAnswer(EnglishAssistantAnswerRequest request, EnglishAssistantStreamListener listener) {
        return client.streamAnswer(request, listener);
    }
}
