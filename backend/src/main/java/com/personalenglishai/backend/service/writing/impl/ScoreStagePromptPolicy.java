package com.personalenglishai.backend.service.writing.impl;

public interface ScoreStagePromptPolicy {

    boolean supports(String stage, String mode);

    String buildSystemPromptAddendum(String taskType);

    String buildStageRules(String taskType);

    String buildTaskAnchors(String taskType);

    String buildFewShotExample(String taskType);
}
