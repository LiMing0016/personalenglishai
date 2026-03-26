package com.personalenglishai.backend.service.writing.impl;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScoreStagePromptPolicyRegistry {

    private final List<ScoreStagePromptPolicy> policies;

    public ScoreStagePromptPolicyRegistry(List<ScoreStagePromptPolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public String buildSystemPromptAddendum(String stage, String mode, String taskType) {
        ScoreStagePromptPolicy policy = find(stage, mode);
        return policy == null ? "" : policy.buildSystemPromptAddendum(taskType);
    }

    public String buildStageRules(String stage, String mode, String taskType) {
        ScoreStagePromptPolicy policy = find(stage, mode);
        return policy == null ? "" : policy.buildStageRules(taskType);
    }

    public String buildTaskAnchors(String stage, String mode, String taskType) {
        ScoreStagePromptPolicy policy = find(stage, mode);
        return policy == null ? "" : policy.buildTaskAnchors(taskType);
    }

    public String resolveFewShotExample(String stage, String mode, String taskType, String defaultFewShotExample) {
        ScoreStagePromptPolicy policy = find(stage, mode);
        return policy == null ? defaultFewShotExample : policy.buildFewShotExample(taskType);
    }

    private ScoreStagePromptPolicy find(String stage, String mode) {
        if (policies.isEmpty()) {
            return null;
        }
        for (ScoreStagePromptPolicy policy : policies) {
            if (policy != null && policy.supports(stage, mode)) {
                return policy;
            }
        }
        return null;
    }
}
