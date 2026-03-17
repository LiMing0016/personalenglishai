package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PolishRubricConfigService {

    private static final Logger log = LoggerFactory.getLogger(PolishRubricConfigService.class);
    private static final String CONFIG_PATH = "polish-rubric/polish-rubrics.json";

    private final ObjectMapper objectMapper;
    private final List<PolishRubricProfile> profiles;

    public PolishRubricConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.profiles = loadProfiles();
    }

    public PolishRubricProfile resolve(String stage, String mode, String taskType) {
        String normalizedStage = normalize(stage, "default");
        String normalizedMode = normalize(mode, "exam");
        String normalizedTaskType = normalize(taskType, "default");
        return profiles.stream()
                .filter(profile -> matches(profile.getMode(), normalizedMode))
                .filter(profile -> matches(profile.getStage(), normalizedStage) || matches(profile.getStage(), "default"))
                .filter(profile -> matches(profile.getTaskType(), normalizedTaskType) || matches(profile.getTaskType(), "default"))
                .sorted((left, right) -> Integer.compare(specificity(right, normalizedStage, normalizedTaskType), specificity(left, normalizedStage, normalizedTaskType)))
                .findFirst()
                .orElseGet(() -> defaultProfile(normalizedMode));
    }

    private int specificity(PolishRubricProfile profile, String stage, String taskType) {
        int score = 0;
        if (matches(profile.getStage(), stage)) score += 2;
        if (matches(profile.getTaskType(), taskType)) score += 1;
        return score;
    }

    private boolean matches(String actual, String expected) {
        return normalize(actual, "").equals(normalize(expected, ""));
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private List<PolishRubricProfile> loadProfiles() {
        try (InputStream inputStream = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            PolishRubricConfigRoot root = objectMapper.readValue(inputStream, PolishRubricConfigRoot.class);
            if (root != null && root.getProfiles() != null && !root.getProfiles().isEmpty()) {
                return root.getProfiles();
            }
        } catch (Exception e) {
            log.warn("Failed to load polish rubric config from {}: {}", CONFIG_PATH, e.getMessage());
        }
        return List.of(defaultProfile("exam"), defaultProfile("free"));
    }

    private PolishRubricProfile defaultProfile(String mode) {
        PolishRubricProfile profile = new PolishRubricProfile();
        String normalizedMode = normalize(mode, "exam");
        profile.setKey("default." + normalizedMode + ".default.polish.v1");
        profile.setStage("default");
        profile.setMode(normalizedMode);
        profile.setTaskType("default");
        profile.setBandRankMap(new LinkedHashMap<>(Map.of(
                "Band 5", 1,
                "Band 4", 2,
                "Band 3", 3,
                "Band 2", 4,
                "Band 1", 5
        )));
        profile.setBandScoreFloor(new LinkedHashMap<>(Map.of(
                "Band 5", 85,
                "Band 4", 70,
                "Band 3", 55,
                "Band 2", 40,
                "Band 1", 0
        )));
        RouteRule partial = new RouteRule();
        KeepFrameworkRule keep = new KeepFrameworkRule();
        keep.setStructureMinLevel("B");
        keep.setGrammarMinLevel("C");
        keep.setExpressionMinLevel("C");
        partial.setKeepFrameworkIf(keep);
        partial.setRouteIfKeepFramework("topic_correction_then_polish");
        partial.setRouteIfNotKeepFramework("corrected_rewrite");
        RouteRule aligned = new RouteRule();
        aligned.setDefaultRoute("rubric_polish");
        Map<String, RouteRule> routeRules = new LinkedHashMap<>();
        routeRules.put("aligned", aligned);
        if ("exam".equals(normalizedMode)) {
            routeRules.put("partial", partial);
            routeRules.put("off_topic", partial);
        } else {
            RouteRule freeRewrite = new RouteRule();
            freeRewrite.setDefaultRoute("corrected_rewrite");
            routeRules.put("partial", freeRewrite);
            routeRules.put("off_topic", freeRewrite);
        }
        profile.setRouteRules(routeRules);
        ForceRewriteRule forceRewriteRule = new ForceRewriteRule();
        forceRewriteRule.setRewriteUnderWordRatio(0.75d);
        forceRewriteRule.setMissingTaskActions(true);
        forceRewriteRule.setSevereFormatMismatch(true);
        forceRewriteRule.setMissingCoreMaterialCoverage(true);
        profile.setForceRewrite(forceRewriteRule);

        Map<String, TierProfile> tierProfiles = new LinkedHashMap<>();
        tierProfiles.put("basic", tierProfile(Map.of("5", 4, "4", 3, "3", 2, "2", 2, "1", 1), "low"));
        tierProfiles.put("steady", tierProfile(Map.of("5", 3, "4", 3, "3", 2, "2", 1, "1", 1), "medium"));
        tierProfiles.put("advanced", tierProfile(Map.of("5", 3, "4", 2, "3", 2, "2", 1, "1", 1), "high"));
        tierProfiles.put("perfect", tierProfile(Map.of("5", 1, "4", 1, "3", 1, "2", 1, "1", 1), "very_high"));
        profile.setTierProfiles(tierProfiles);
        SafetyGuard safetyGuard = new SafetyGuard();
        safetyGuard.setMustNotRegress(List.of("relevance", "task_completion", "coverage", "word_count_band", "final_score"));
        profile.setSafetyGuard(safetyGuard);
        return profile;
    }

    private TierProfile tierProfile(Map<String, Integer> targetBandRankBySource, String rewriteStrength) {
        TierProfile profile = new TierProfile();
        profile.setTargetBandRankBySource(new LinkedHashMap<>(targetBandRankBySource));
        profile.setRewriteStrength(rewriteStrength);
        profile.setCoreDimensions(List.of("task_achievement", "content_quality", "structure"));
        return profile;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolishRubricConfigRoot {
        private List<PolishRubricProfile> profiles = new ArrayList<>();

        public List<PolishRubricProfile> getProfiles() { return profiles; }
        public void setProfiles(List<PolishRubricProfile> profiles) { this.profiles = profiles; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolishRubricProfile {
        private String key;
        private String stage;
        private String mode;
        private String taskType;
        private Map<String, Integer> bandRankMap = new LinkedHashMap<>();
        private Map<String, Integer> bandScoreFloor = new LinkedHashMap<>();
        private Map<String, RouteRule> routeRules = new LinkedHashMap<>();
        private ForceRewriteRule forceRewrite = new ForceRewriteRule();
        private Map<String, TierProfile> tierProfiles = new LinkedHashMap<>();
        private SafetyGuard safetyGuard = new SafetyGuard();

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getStage() { return stage; }
        public void setStage(String stage) { this.stage = stage; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public Map<String, Integer> getBandRankMap() { return bandRankMap; }
        public void setBandRankMap(Map<String, Integer> bandRankMap) { this.bandRankMap = bandRankMap; }
        public Map<String, Integer> getBandScoreFloor() { return bandScoreFloor; }
        public void setBandScoreFloor(Map<String, Integer> bandScoreFloor) { this.bandScoreFloor = bandScoreFloor; }
        public Map<String, RouteRule> getRouteRules() { return routeRules; }
        public void setRouteRules(Map<String, RouteRule> routeRules) { this.routeRules = routeRules; }
        public ForceRewriteRule getForceRewrite() { return forceRewrite; }
        public void setForceRewrite(ForceRewriteRule forceRewrite) { this.forceRewrite = forceRewrite; }
        public Map<String, TierProfile> getTierProfiles() { return tierProfiles; }
        public void setTierProfiles(Map<String, TierProfile> tierProfiles) { this.tierProfiles = tierProfiles; }
        public SafetyGuard getSafetyGuard() { return safetyGuard; }
        public void setSafetyGuard(SafetyGuard safetyGuard) { this.safetyGuard = safetyGuard; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteRule {
        private String defaultRoute;
        private KeepFrameworkRule keepFrameworkIf;
        private String routeIfKeepFramework;
        private String routeIfNotKeepFramework;

        public String getDefaultRoute() { return defaultRoute; }
        public void setDefaultRoute(String defaultRoute) { this.defaultRoute = defaultRoute; }
        public KeepFrameworkRule getKeepFrameworkIf() { return keepFrameworkIf; }
        public void setKeepFrameworkIf(KeepFrameworkRule keepFrameworkIf) { this.keepFrameworkIf = keepFrameworkIf; }
        public String getRouteIfKeepFramework() { return routeIfKeepFramework; }
        public void setRouteIfKeepFramework(String routeIfKeepFramework) { this.routeIfKeepFramework = routeIfKeepFramework; }
        public String getRouteIfNotKeepFramework() { return routeIfNotKeepFramework; }
        public void setRouteIfNotKeepFramework(String routeIfNotKeepFramework) { this.routeIfNotKeepFramework = routeIfNotKeepFramework; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeepFrameworkRule {
        private String structureMinLevel;
        private String grammarMinLevel;
        private String expressionMinLevel;

        public String getStructureMinLevel() { return structureMinLevel; }
        public void setStructureMinLevel(String structureMinLevel) { this.structureMinLevel = structureMinLevel; }
        public String getGrammarMinLevel() { return grammarMinLevel; }
        public void setGrammarMinLevel(String grammarMinLevel) { this.grammarMinLevel = grammarMinLevel; }
        public String getExpressionMinLevel() { return expressionMinLevel; }
        public void setExpressionMinLevel(String expressionMinLevel) { this.expressionMinLevel = expressionMinLevel; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ForceRewriteRule {
        private Double rewriteUnderWordRatio;
        private Boolean missingTaskActions;
        private Boolean severeFormatMismatch;
        private Boolean missingCoreMaterialCoverage;

        public Double getRewriteUnderWordRatio() { return rewriteUnderWordRatio; }
        public void setRewriteUnderWordRatio(Double rewriteUnderWordRatio) { this.rewriteUnderWordRatio = rewriteUnderWordRatio; }
        public Boolean getMissingTaskActions() { return missingTaskActions; }
        public void setMissingTaskActions(Boolean missingTaskActions) { this.missingTaskActions = missingTaskActions; }
        public Boolean getSevereFormatMismatch() { return severeFormatMismatch; }
        public void setSevereFormatMismatch(Boolean severeFormatMismatch) { this.severeFormatMismatch = severeFormatMismatch; }
        public Boolean getMissingCoreMaterialCoverage() { return missingCoreMaterialCoverage; }
        public void setMissingCoreMaterialCoverage(Boolean missingCoreMaterialCoverage) { this.missingCoreMaterialCoverage = missingCoreMaterialCoverage; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TierProfile {
        private Map<String, Integer> targetBandRankBySource = new LinkedHashMap<>();
        private String rewriteStrength;
        private List<String> coreDimensions = new ArrayList<>();

        public Map<String, Integer> getTargetBandRankBySource() { return targetBandRankBySource; }
        public void setTargetBandRankBySource(Map<String, Integer> targetBandRankBySource) { this.targetBandRankBySource = targetBandRankBySource; }
        public String getRewriteStrength() { return rewriteStrength; }
        public void setRewriteStrength(String rewriteStrength) { this.rewriteStrength = rewriteStrength; }
        public List<String> getCoreDimensions() { return coreDimensions; }
        public void setCoreDimensions(List<String> coreDimensions) { this.coreDimensions = coreDimensions; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SafetyGuard {
        private List<String> mustNotRegress = new ArrayList<>();

        public List<String> getMustNotRegress() { return mustNotRegress; }
        public void setMustNotRegress(List<String> mustNotRegress) { this.mustNotRegress = mustNotRegress; }
    }
}
