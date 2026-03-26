package com.personalenglishai.backend.ai.englishassistant;

public interface EnglishAssistantStreamListener {

    void onDelta(String text);
}
