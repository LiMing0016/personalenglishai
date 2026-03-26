package com.personalenglishai.backend.ai.englishassistant;

public interface EnglishAssistantAnswerService {

    EnglishAssistantAnswerResult answer(EnglishAssistantAnswerRequest request);

    EnglishAssistantAnswerResult streamAnswer(EnglishAssistantAnswerRequest request, EnglishAssistantStreamListener listener);
}
