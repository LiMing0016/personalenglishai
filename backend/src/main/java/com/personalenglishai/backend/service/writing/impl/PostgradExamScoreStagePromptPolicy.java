package com.personalenglishai.backend.service.writing.impl;

import org.springframework.stereotype.Component;

@Component
public class PostgradExamScoreStagePromptPolicy implements ScoreStagePromptPolicy {

    private static final String TASK1_FEW_SHOT_EXAMPLE = """
            [评分示例 — postgrad exam task1 功能写作]
            题目类型：投诉/建议类书信。
            高分作文必须同时满足：写作目的明确、对象意识正确、语气得体、格式基本规范、关键信息完整。
            若缺少称呼、请求动作或结尾，task_achievement 不得给高档。
            示例输出片段：
            {"mode":"exam","grades":{"task_achievement":"B","content_quality":"B","structure":"B","vocabulary":"C","grammar":"C","expression":"B"}}
            """;

    private static final String TASK2_FEW_SHOT_EXAMPLE = """
            [评分示例 — postgrad exam task2 材料作文]
            题目类型：图画/图表/现象评论类大作文。
            高分作文必须同时完成：描述材料、解读含义、给出评论。
            如果只有空泛评论而没有描述材料，task_achievement 不得给高档。
            示例输出片段：
            {"mode":"exam","grades":{"task_achievement":"B","content_quality":"B","structure":"B","vocabulary":"B","grammar":"C","expression":"B"}}
            """;

    @Override
    public boolean supports(String stage, String mode) {
        return "postgrad".equals(stage) && "exam".equals(mode);
    }

    @Override
    public String buildSystemPromptAddendum(String taskType) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[考研评分场景]\n");
        sb.append("当前学段为 postgrad exam。必须坚持任务与内容优先，先判断是否切题、是否完成任务，再判断语言质量。\n");
        if ("task1".equals(taskType)) {
            sb.append("task1 是功能写作，不得按泛议论文标准打分。\n");
        } else if ("task2".equals(taskType)) {
            sb.append("task2 是材料作文，不得因为语言漂亮就忽略材料描述缺失。\n");
        } else {
            sb.append("taskType 未明确时，只能保守推断任务类型，评分应偏严。\n");
        }
        return sb.toString();
    }

    @Override
    public String buildStageRules(String taskType) {
        StringBuilder sb = new StringBuilder();
        sb.append("[POSTGRAD_STAGE_RULES]\n");
        sb.append("这是 postgrad exam 写作评分场景，统一按 100 分制和 Band 1-5 的考研语义理解评分结果。\n");
        sb.append("必须先判任务方向和任务完成度，再判内容质量和语言质量。\n");
        sb.append("语言质量不能覆盖严重偏题、材料未解读或任务未完成。\n");
        if ("task1".equals(taskType)) {
            sb.append("当前任务类型为 task1：功能写作。重点检查写作目的、收信对象和身份关系、语气、格式、要点覆盖。\n");
        } else if ("task2".equals(taskType)) {
            sb.append("当前任务类型为 task2：材料作文。重点检查描述材料、解读含义、给出评论三个动作是否完整。\n");
        } else {
            sb.append("当前 taskType 未明确。若无法从题目可靠判断，只能保守评分，不得高估 task_achievement。\n");
        }
        return sb.toString().trim();
    }

    @Override
    public String buildTaskAnchors(String taskType) {
        if ("task1".equals(taskType)) {
            return "考研 task1 判分锚点：功能写作；必须检查写作目的、收信对象和身份关系、语气、格式、要点覆盖；不要按泛议论文标准打分。\n";
        }
        if ("task2".equals(taskType)) {
            return "考研 task2 判分锚点：必须完成“描述材料 + 解读含义 + 给出评论”；不要只给空泛评论而忽略材料描述。\n";
        }
        return "考研主路径建议显式提供 taskType。若缺失，只能保守推断 task1/task2，不得高估任务完成度。\n";
    }

    @Override
    public String buildFewShotExample(String taskType) {
        if ("task1".equals(taskType)) {
            return TASK1_FEW_SHOT_EXAMPLE;
        }
        if ("task2".equals(taskType)) {
            return TASK2_FEW_SHOT_EXAMPLE;
        }
        return TASK1_FEW_SHOT_EXAMPLE + "\n\n" + TASK2_FEW_SHOT_EXAMPLE;
    }
}
