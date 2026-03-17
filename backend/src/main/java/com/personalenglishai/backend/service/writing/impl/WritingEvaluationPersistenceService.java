package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentScoreSummary;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.EssayEvaluationDimension;
import com.personalenglishai.backend.mapper.DocumentScoreSummaryMapper;
import com.personalenglishai.backend.mapper.EssayEvaluationDimensionMapper;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.service.document.DocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WritingEvaluationPersistenceService {

    private final EssayEvaluationMapper essayEvaluationMapper;
    private final EssayEvaluationDimensionMapper essayEvaluationDimensionMapper;
    private final DocumentScoreSummaryMapper documentScoreSummaryMapper;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    public WritingEvaluationPersistenceService(
            EssayEvaluationMapper essayEvaluationMapper,
            EssayEvaluationDimensionMapper essayEvaluationDimensionMapper,
            DocumentScoreSummaryMapper documentScoreSummaryMapper,
            DocumentService documentService,
            ObjectMapper objectMapper
    ) {
        this.essayEvaluationMapper = essayEvaluationMapper;
        this.essayEvaluationDimensionMapper = essayEvaluationDimensionMapper;
        this.documentScoreSummaryMapper = documentScoreSummaryMapper;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public EssayEvaluation persistSuccessfulEvaluation(
            WritingEvaluateRequest request,
            String mode,
            WritingEvaluateResponse response,
            RubricActiveResponse rubric,
            String effectiveStage,
            String modelVersion
    ) throws Exception {
        if (request == null || request.getUserId() == null || response == null) {
            return null;
        }

        Document doc = resolveDocument(request.getUserId(), request.getDocumentId());
        TextStats textStats = buildTextStats(request.getEssay());
        ErrorStats errorStats = buildErrorStats(response.getErrors());
        WritingEvaluateResponse.ExamPolicyDto examPolicy = response.getExamPolicy();
        BandRange examBand = "exam".equals(mode)
                ? resolveBandRange(response.getScore() != null ? response.getScore().getOverall() : null)
                : null;
        DirectionAssessmentSnapshot direction = examPolicy != null
                ? toDirectionSnapshot(examPolicy.getDirectionAssessment())
                : "exam".equals(mode)
                ? buildDirectionAssessment(request, response, textStats)
                : null;
        ScoreAdjustmentSnapshot adjustment = examPolicy != null
                ? toScoreAdjustmentSnapshot(examPolicy)
                : "exam".equals(mode)
                ? buildScoreAdjustment(request, textStats, errorStats, direction, examBand)
                : null;

        EssayEvaluation record = new EssayEvaluation();
        record.setUserId(request.getUserId());
        record.setDocumentId(doc != null ? doc.getId() : null);
        record.setMode(mode);
        record.setTaskPrompt(trimToNull(request.getTaskPrompt()));
        record.setEssayText(request.getEssay() == null ? "" : request.getEssay());
        if (response.getGaokaoScore() != null) {
            record.setGaokaoScore(response.getGaokaoScore().getScore());
            record.setMaxScore(response.getGaokaoScore().getMaxScore());
            record.setBand(trimToNull(response.getGaokaoScore().getBand()));
        }
        if (response.getScore() != null) {
            record.setOverallScore(response.getScore().getOverall());
        }
        record.setStudyStage(trimToNull(effectiveStage));
        record.setRubricKey(rubric != null ? trimToNull(rubric.getRubricKey()) : null);
        record.setExamPolicyKey(examPolicy != null ? trimToNull(examPolicy.getPolicyKey()) : "exam".equals(mode) ? buildExamPolicyKey(effectiveStage) : null);
        record.setModelVersion(trimToNull(modelVersion));
        record.setEvaluatedRevision(doc != null ? doc.getLatestRevision() : null);
        if (examBand != null) {
            record.setExamBandLabel(examBand.label());
            record.setExamBandMin(examBand.min());
            record.setExamBandMax(examBand.max());
        }
        if (direction != null) {
            record.setDirectionRelevance(direction.relevance());
            record.setDirectionTaskCompletion(direction.taskCompletion());
            record.setDirectionCoverage(direction.coverage());
            record.setDirectionMaxBand(direction.maxBand());
            record.setDirectionReasonsJson(objectMapper.writeValueAsString(direction.reasons()));
        }
        if (adjustment != null) {
            record.setCapScore(adjustment.capScore());
            record.setDeductionTotal(adjustment.deductionTotal());
            record.setPenaltyFlagsJson(objectMapper.writeValueAsString(adjustment.flags()));
            record.setAdjustmentReasonsJson(objectMapper.writeValueAsString(adjustment.reasons()));
        }
        record.setWordCount(textStats.wordCount());
        record.setSentenceCount(textStats.sentenceCount());
        record.setParagraphCount(textStats.paragraphCount());
        record.setTotalErrorCount(errorStats.total());
        record.setMajorErrorCount(errorStats.major());
        record.setMinorErrorCount(errorStats.minor());
        record.setGrammarErrorCount(errorStats.grammar());
        record.setSpellingErrorCount(errorStats.spelling());
        record.setVocabularyErrorCount(errorStats.lexical());
        record.setLexicalErrorCount(errorStats.lexical());
        record.setPunctuationErrorCount(errorStats.punctuation());
        record.setSyntaxErrorCount(errorStats.syntax());
        applyLegacyDimensionColumns(record, response.getDimensionScores());
        record.setResultJson(objectMapper.writeValueAsString(response));

        essayEvaluationMapper.insert(record);

        List<EssayEvaluationDimension> dimensions = buildDimensionRows(record.getId(), rubric, response);
        if (!dimensions.isEmpty()) {
            essayEvaluationDimensionMapper.insertBatch(dimensions);
        }

        if (doc != null) {
            syncWritingMetadata(doc, request.getUserId(), request, mode);
            if (record.getOverallScore() != null) {
                documentService.updateScoreStats(doc.getId(), record.getOverallScore());
            }
            upsertDocumentSummary(record);
        }

        return record;
    }

    private void applyLegacyDimensionColumns(EssayEvaluation record, Map<String, Integer> dims) {
        if (record == null || dims == null) {
            return;
        }
        record.setContentQuality(dims.get("content_quality"));
        record.setTaskAchievement(dims.get("task_achievement"));
        record.setStructureScore(dims.get("structure"));
        record.setVocabularyScore(dims.get("vocabulary"));
        record.setGrammarScore(dims.get("grammar"));
        record.setExpressionScore(dims.get("expression"));
    }

    private List<EssayEvaluationDimension> buildDimensionRows(Long evaluationId, RubricActiveResponse rubric, WritingEvaluateResponse response) {
        List<EssayEvaluationDimension> rows = new ArrayList<>();
        if (evaluationId == null || rubric == null || rubric.getDimensions() == null) {
            return rows;
        }

        Map<String, Integer> scoreMap = response != null && response.getDimensionScores() != null
                ? response.getDimensionScores() : Map.of();
        Map<String, String> gradeMap = response != null && response.getGrades() != null
                ? response.getGrades() : Map.of();
        Map<String, WritingEvaluateResponse.AnalysisDto> analysisMap = response != null && response.getAnalysis() != null
                ? response.getAnalysis() : Map.of();

        int sortOrder = 1;
        for (RubricActiveResponse.DimensionDto dimension : rubric.getDimensions()) {
            EssayEvaluationDimension row = new EssayEvaluationDimension();
            row.setEvaluationId(evaluationId);
            row.setDimensionKey(dimension.getDimensionKey());
            row.setDimensionLabelSnapshot(dimension.getDisplayName() == null ? dimension.getDimensionKey() : dimension.getDisplayName());
            row.setSortOrder(sortOrder++);
            row.setScore(scoreMap.get(dimension.getDimensionKey()));
            row.setGrade(gradeMap.get(dimension.getDimensionKey()));
            WritingEvaluateResponse.AnalysisDto analysis = analysisMap.get(dimension.getDimensionKey());
            if (analysis != null) {
                row.setStrength(trimToNull(analysis.getStrength()));
                row.setWeakness(trimToNull(analysis.getWeakness()));
                row.setSuggestion(trimToNull(analysis.getSuggestion()));
            }
            rows.add(row);
        }
        return rows;
    }

    private void upsertDocumentSummary(EssayEvaluation record) {
        if (record == null || record.getDocumentId() == null) {
            return;
        }

        DocumentScoreSummary summary = documentScoreSummaryMapper.selectByDocumentId(record.getDocumentId());
        if (summary == null) {
            summary = new DocumentScoreSummary();
            summary.setDocumentId(record.getDocumentId());
            summary.setUserId(record.getUserId());
            summary.setFirstEvaluationId(record.getId());
            summary.setLatestEvaluationId(record.getId());
            summary.setBestEvaluationId(record.getId());
            summary.setFirstOverallScore(record.getOverallScore());
            summary.setLatestOverallScore(record.getOverallScore());
            summary.setBestOverallScore(record.getOverallScore());
            summary.setLatestBandLabel(resolveSummaryBandLabel(record));
            summary.setLatestWordCount(record.getWordCount());
            summary.setLatestTotalErrorCount(record.getTotalErrorCount());
            summary.setLatestMajorErrorCount(record.getMajorErrorCount());
            summary.setLatestMinorErrorCount(record.getMinorErrorCount());
            documentScoreSummaryMapper.insert(summary);
            return;
        }

        summary.setUserId(record.getUserId());
        if (summary.getFirstEvaluationId() == null) {
            summary.setFirstEvaluationId(record.getId());
        }
        if (summary.getFirstOverallScore() == null) {
            summary.setFirstOverallScore(record.getOverallScore());
        }
        summary.setLatestEvaluationId(record.getId());
        summary.setLatestOverallScore(record.getOverallScore());
        summary.setLatestBandLabel(resolveSummaryBandLabel(record));
        summary.setLatestWordCount(record.getWordCount());
        summary.setLatestTotalErrorCount(record.getTotalErrorCount());
        summary.setLatestMajorErrorCount(record.getMajorErrorCount());
        summary.setLatestMinorErrorCount(record.getMinorErrorCount());

        Integer existingBest = summary.getBestOverallScore();
        Integer currentOverall = record.getOverallScore();
        if (summary.getBestEvaluationId() == null
                || existingBest == null
                || (currentOverall != null && currentOverall > existingBest)) {
            summary.setBestEvaluationId(record.getId());
            summary.setBestOverallScore(currentOverall);
        }

        documentScoreSummaryMapper.updateByDocumentId(summary);
    }

    private String resolveSummaryBandLabel(EssayEvaluation record) {
        if (record == null) {
            return null;
        }
        return trimToNull(record.getExamBandLabel()) != null ? record.getExamBandLabel() : trimToNull(record.getBand());
    }

    private void syncWritingMetadata(Document doc, Long userId, WritingEvaluateRequest request, String mode) {
        if (doc == null || userId == null) {
            return;
        }
        DocumentService.StartMetadata metadata = new DocumentService.StartMetadata();
        metadata.setMode(mode);
        metadata.setTitleSnapshot(doc.getTitle());
        metadata.setStudyStage(trimToNull(request.getStudyStage()));
        metadata.setTopicTitle(trimToNull(request.getTopicTitle()));
        metadata.setPromptText(trimToNull(request.getTaskPrompt()));
        metadata.setGenre(trimToNull(request.getGenre()));
        metadata.setSourceType(null);
        metadata.setExamType(trimToNull(request.getExamType()));
        metadata.setTaskType(trimToNull(request.getTaskType()));
        metadata.setMinWords(request.getMinWords());
        metadata.setRecommendedMaxWords(request.getRecommendedMaxWords());
        metadata.setMaxScore(request.getMaxScore());
        documentService.upsertWritingMetadata(doc, userId, metadata);
    }

    private Document resolveDocument(Long userId, String publicId) {
        if (userId == null || publicId == null || publicId.isBlank()) {
            return null;
        }
        String tenantId = String.valueOf(userId);
        return documentService.findByPublicId(tenantId, "default", publicId);
    }

    private TextStats buildTextStats(String essay) {
        String text = essay == null ? "" : essay.trim();
        if (text.isEmpty()) {
            return new TextStats(0, 0, 0);
        }
        int wordCount = text.split("\\s+").length;
        int sentenceCount = text.split("(?<=[.!?。！？])\\s+").length;
        int paragraphCount = text.split("(?:\\r?\\n){2,}").length;
        return new TextStats(wordCount, Math.max(sentenceCount, 1), Math.max(paragraphCount, 1));
    }

    private ErrorStats buildErrorStats(List<WritingEvaluateResponse.ErrorDto> errors) {
        if (errors == null || errors.isEmpty()) {
            return new ErrorStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
        int total = 0;
        int major = 0;
        int minor = 0;
        int grammar = 0;
        int lexical = 0;
        int spelling = 0;
        int punctuation = 0;
        int syntax = 0;
        for (WritingEvaluateResponse.ErrorDto error : errors) {
            if (error == null) {
                continue;
            }
            if ("suggestion".equalsIgnoreCase(error.getCategory())) {
                continue;
            }
            total++;            if ("major".equalsIgnoreCase(error.getSeverity())) {
                major++;
            } else {
                minor++;
            }
            String type = error.getType() == null ? "" : error.getType().trim().toLowerCase(Locale.ROOT);
            switch (type) {
                case "spelling", "morphology" -> spelling++;
                case "word_choice", "collocation", "part_of_speech" -> lexical++;
                case "punctuation" -> punctuation++;
                case "syntax", "logic" -> syntax++;
                default -> grammar++;
            }
        }
        return new ErrorStats(total, major, minor, grammar, lexical, spelling, punctuation, syntax);
    }
    private DirectionAssessmentSnapshot toDirectionSnapshot(WritingEvaluateResponse.DirectionAssessmentDto dto) {
        if (dto == null) {
            return null;
        }
        return new DirectionAssessmentSnapshot(
                dto.getRelevance(),
                dto.getTaskCompletion(),
                dto.getCoverage(),
                dto.getMaxBand(),
                dto.getReasons() == null ? List.of() : dto.getReasons()
        );
    }

    private ScoreAdjustmentSnapshot toScoreAdjustmentSnapshot(WritingEvaluateResponse.ExamPolicyDto dto) {
        if (dto == null) {
            return null;
        }
        return new ScoreAdjustmentSnapshot(
                dto.getCapScore(),
                dto.getDeductionTotal() == null ? 0 : dto.getDeductionTotal(),
                dto.getFlags() == null ? Map.of() : dto.getFlags(),
                dto.getReasons() == null ? List.of() : dto.getReasons()
        );
    }

    private DirectionAssessmentSnapshot buildDirectionAssessment(
            WritingEvaluateRequest request,
            WritingEvaluateResponse response,
            TextStats textStats
    ) {
        int taskScore = 60;
        if (response.getDimensionScores() != null) {
            if (response.getDimensionScores().get("task_achievement") != null) {
                taskScore = response.getDimensionScores().get("task_achievement");
            } else if (response.getDimensionScores().get("content_quality") != null) {
                taskScore = response.getDimensionScores().get("content_quality");
            }
        }

        double ratio = request.getMinWords() != null && request.getMinWords() > 0
                ? (double) textStats.wordCount() / request.getMinWords()
                : -1;

        String relevance = taskScore >= 85 ? "fully_on_topic"
                : taskScore >= 70 ? "mostly_on_topic"
                : taskScore >= 55 ? "partially_off_topic"
                : "seriously_off_topic";

        String coverage = taskScore >= 85 ? "all_key_points"
                : taskScore >= 70 ? "most_key_points"
                : taskScore >= 55 ? "partial_key_points"
                : "few_key_points";

        String taskCompletion;
        if (ratio >= 1.0) {
            taskCompletion = "fully_completed";
        } else if (ratio >= 0.85) {
            taskCompletion = "mostly_completed";
        } else if (ratio >= 0.70) {
            taskCompletion = "partially_completed";
        } else if (ratio >= 0) {
            taskCompletion = "seriously_incomplete";
        } else {
            taskCompletion = taskScore >= 85 ? "fully_completed"
                    : taskScore >= 70 ? "mostly_completed"
                    : taskScore >= 55 ? "partially_completed"
                    : "seriously_incomplete";
        }

        int maxBand = Math.min(Math.min(relevanceToBand(relevance), coverageToBand(coverage)), completionToBand(taskCompletion));
        List<String> reasons = new ArrayList<>();
        reasons.add(relevanceReason(relevance));
        reasons.add(completionReason(taskCompletion));
        reasons.add(coverageReason(coverage));
        reasons.add("方向门槛最高允许进入 " + bandLabel(maxBand));
        return new DirectionAssessmentSnapshot(relevance, taskCompletion, coverage, bandLabel(maxBand), reasons);
    }

    private ScoreAdjustmentSnapshot buildScoreAdjustment(
            WritingEvaluateRequest request,
            TextStats textStats,
            ErrorStats errorStats,
            DirectionAssessmentSnapshot direction,
            BandRange currentBand
    ) {
        Integer capScore = null;
        int deductions = 0;
        Map<String, Boolean> flags = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();

        if (direction != null && currentBand != null) {
            int directionBandNo = bandNumber(direction.maxBand());
            if (directionBandNo > 0 && directionBandNo < currentBand.bandNo()) {
                capScore = bandMax(directionBandNo);
                flags.put("direction_band_limited", true);
                reasons.add("因方向门槛限制，最高档位不超过 " + direction.maxBand());
            }
        }

        if (request.getMinWords() != null && request.getMinWords() > 0) {
            double ratio = (double) textStats.wordCount() / request.getMinWords();
            if (ratio < 0.70) {
                capScore = minCap(capScore, 54);
                deductions += 8;
                flags.put("severe_under_word_count", true);
                reasons.add("字数严重不足，触发封顶与扣分");
            } else if (ratio < 0.85) {
                capScore = minCap(capScore, 69);
                deductions += 8;
                flags.put("moderate_under_word_count", true);
                reasons.add("字数不足，最高不超过 Band 3");
            } else if (ratio < 1.0) {
                deductions += 3;
                flags.put("light_under_word_count", true);
                reasons.add("字数略不足，触发轻度扣分");
            }
        }

        if ("partially_completed".equals(direction != null ? direction.taskCompletion() : null)) {
            capScore = minCap(capScore, 69);
            flags.put("task_partially_completed", true);
            reasons.add("任务完成度不足，最高不超过 Band 3");
        } else if ("seriously_incomplete".equals(direction != null ? direction.taskCompletion() : null)) {
            capScore = minCap(capScore, 54);
            flags.put("task_seriously_incomplete", true);
            reasons.add("任务明显未完成，最高不超过 Band 2");
        }

        if (errorStats.major() >= 8) {
            deductions += 8;
            flags.put("heavy_major_errors", true);
            reasons.add("重大错误较多，触发重度扣分");
        } else if (errorStats.major() >= 4) {
            deductions += 4;
            flags.put("medium_major_errors", true);
            reasons.add("重大错误累计，触发额外扣分");
        }

        if (flags.isEmpty()) {
            return new ScoreAdjustmentSnapshot(null, 0, Map.of(), List.of());
        }
        return new ScoreAdjustmentSnapshot(capScore, deductions, flags, reasons);
    }


    private BandRange resolveBandRange(Integer overall) {
        int score = overall == null ? 0 : overall;
        if (score >= 85) return new BandRange("Band 5", 85, 100, 5);
        if (score >= 70) return new BandRange("Band 4", 70, 84, 4);
        if (score >= 55) return new BandRange("Band 3", 55, 69, 3);
        if (score >= 40) return new BandRange("Band 2", 40, 54, 2);
        return new BandRange("Band 1", 0, 39, 1);
    }

    private int relevanceToBand(String relevance) {
        return switch (relevance) {
            case "fully_on_topic" -> 5;
            case "mostly_on_topic" -> 4;
            case "partially_off_topic" -> 2;
            default -> 1;
        };
    }

    private int coverageToBand(String coverage) {
        return switch (coverage) {
            case "all_key_points" -> 5;
            case "most_key_points" -> 4;
            case "partial_key_points" -> 3;
            default -> 2;
        };
    }

    private int completionToBand(String completion) {
        return switch (completion) {
            case "fully_completed" -> 5;
            case "mostly_completed" -> 4;
            case "partially_completed" -> 3;
            default -> 2;
        };
    }

    private String relevanceReason(String relevance) {
        return switch (relevance) {
            case "fully_on_topic" -> "内容切题度较高";
            case "mostly_on_topic" -> "内容基本切题";
            case "partially_off_topic" -> "存在一定偏题风险";
            default -> "存在明显跑题风险";
        };
    }

    private String completionReason(String completion) {
        return switch (completion) {
            case "fully_completed" -> "任务完成度较高";
            case "mostly_completed" -> "任务基本完成";
            case "partially_completed" -> "任务覆盖不完整";
            default -> "任务完成度明显不足";
        };
    }

    private String coverageReason(String coverage) {
        return switch (coverage) {
            case "all_key_points" -> "要点覆盖完整";
            case "most_key_points" -> "大部分要点已覆盖";
            case "partial_key_points" -> "仅覆盖部分要点";
            default -> "要点覆盖较少";
        };
    }

    private String bandLabel(int bandNo) {
        return "Band " + Math.max(1, Math.min(5, bandNo));
    }

    private int bandNumber(String bandLabel) {
        if (bandLabel == null || bandLabel.isBlank()) {
            return 0;
        }
        if (bandLabel.endsWith("5")) return 5;
        if (bandLabel.endsWith("4")) return 4;
        if (bandLabel.endsWith("3")) return 3;
        if (bandLabel.endsWith("2")) return 2;
        if (bandLabel.endsWith("1")) return 1;
        return 0;
    }

    private int bandMax(int bandNo) {
        return switch (bandNo) {
            case 5 -> 100;
            case 4 -> 84;
            case 3 -> 69;
            case 2 -> 54;
            default -> 39;
        };
    }

    private Integer minCap(Integer current, Integer next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        return Math.min(current, next);
    }

    private String buildExamPolicyKey(String effectiveStage) {
        String stage = trimToNull(effectiveStage);
        return stage == null ? null : stage + "-exam-policy-v1";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record TextStats(int wordCount, int sentenceCount, int paragraphCount) {}

    private record ErrorStats(int total, int major, int minor, int grammar, int lexical, int spelling,
                              int punctuation, int syntax) {}

    private record BandRange(String label, int min, int max, int bandNo) {}

    private record DirectionAssessmentSnapshot(String relevance, String taskCompletion, String coverage,
                                               String maxBand, List<String> reasons) {}

    private record ScoreAdjustmentSnapshot(Integer capScore, int deductionTotal,
                                           Map<String, Boolean> flags, List<String> reasons) {}
}

