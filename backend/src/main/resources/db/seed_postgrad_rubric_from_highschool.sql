-- Seed the formal postgrad exam rubric.
-- 用于已有数据库移除占位版 postgrad-v1，并补齐正式版 postgrad-exam-v1。

DELETE FROM rubric_level
WHERE rubric_version_id IN (
  SELECT id FROM (
    SELECT id FROM rubric_version
    WHERE rubric_key IN ('postgrad-v1', 'postgrad-exam-v1') AND stage = 'postgrad'
  ) target_versions
);

DELETE FROM rubric_dimension
WHERE rubric_version_id IN (
  SELECT id FROM (
    SELECT id FROM rubric_version
    WHERE rubric_key IN ('postgrad-v1', 'postgrad-exam-v1') AND stage = 'postgrad'
  ) target_versions
);

DELETE FROM rubric_version
WHERE rubric_key IN ('postgrad-v1', 'postgrad-exam-v1') AND stage = 'postgrad';

INSERT INTO rubric_version (rubric_key, stage, is_active)
VALUES ('postgrad-exam-v1', 'postgrad', 1);

SET @target_rv_id = LAST_INSERT_ID();

INSERT INTO rubric_dimension (rubric_version_id, mode, dimension_key, display_name, sort_order) VALUES
(@target_rv_id, 'exam', 'content_quality',  '内容质量',   1),
(@target_rv_id, 'exam', 'task_achievement', '任务完成度', 2),
(@target_rv_id, 'exam', 'structure',        '篇章结构',   3),
(@target_rv_id, 'exam', 'vocabulary',       '词汇丰富度', 4),
(@target_rv_id, 'exam', 'grammar',          '语法准确性', 5),
(@target_rv_id, 'exam', 'expression',       '语言自然度', 6);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@target_rv_id,'exam','task_achievement','A',90,
 'Fully completes the required task. For task1, purpose, audience, tone, format, and all key points are accurate and complete. For task2, the essay clearly describes the material, interprets its meaning, and offers relevant commentary tightly connected to the prompt.'),
(@target_rv_id,'exam','task_achievement','B',75,
 'Completes most of the required task. For task1, the main communicative purpose and most key points are covered with only minor omissions. For task2, description, interpretation, and commentary are all present, though one step may be less fully developed.'),
(@target_rv_id,'exam','task_achievement','C',60,
 'Completes the core task but with visible gaps. For task1, some required points, format, or tone are incomplete. For task2, the essay is generally on task but often under-describes the material or gives commentary that is too general.'),
(@target_rv_id,'exam','task_achievement','D',42,
 'Only partially completes the task. For task1, key communicative actions are missing or tone and format are inappropriate. For task2, the essay usually describes without explaining, comments without describing the material, or drifts noticeably away from the required task.'),
(@target_rv_id,'exam','task_achievement','E',20,
 'Fails to complete the required task. The essay is seriously off topic, functionally incomplete, or only weakly related to the prompt. For task2, material interpretation is largely absent. For task1, the functional writing purpose is mostly missing.'),
(@target_rv_id,'exam','content_quality','A',90,
 'Content is substantial, relevant, and well developed. For task2, the essay extracts the core meaning of the material and develops a mature, meaningful response. For task1, the information is specific, useful, and clearly serves the communicative purpose.'),
(@target_rv_id,'exam','content_quality','B',75,
 'Content is solid and generally effective. Ideas are relevant and developed with some supporting detail, though depth or precision may be slightly limited.'),
(@target_rv_id,'exam','content_quality','C',60,
 'Content is basically adequate but underdeveloped. Ideas are present, yet explanation, support, or detail remains surface-level and conventional.'),
(@target_rv_id,'exam','content_quality','D',42,
 'Content is weak, repetitive, or overly general. Support is limited, and the essay offers little effective development beyond basic statements.'),
(@target_rv_id,'exam','content_quality','E',20,
 'Content is seriously insufficient. The essay relies on empty formulaic statements, provides almost no useful information, and fails to communicate a meaningful response.'),
(@target_rv_id,'exam','structure','A',90,
 'The essay is clearly and effectively organized. For task1, information order and format are natural and appropriate. For task2, the introduction, development, and conclusion are distinct, and the progression from description to interpretation to commentary is smooth and coherent.'),
(@target_rv_id,'exam','structure','B',75,
 'Organization is generally clear. Paragraphing and sequencing mostly support the task well, with only minor weaknesses in transitions or emphasis.'),
(@target_rv_id,'exam','structure','C',60,
 'A basic structure is present, but transitions, paragraphing, or logical flow feel mechanical or uneven. The reader can follow the essay with some effort.'),
(@target_rv_id,'exam','structure','D',42,
 'Organization is loose or poorly controlled. Paragraph function is unclear, sequencing is not ideal, and coherence is noticeably weakened.'),
(@target_rv_id,'exam','structure','E',20,
 'The essay has little or no effective structure. Sentences or ideas are piled together without clear progression, making the writing difficult to follow.'),
(@target_rv_id,'exam','vocabulary','A',90,
 'Vocabulary is accurate, appropriate, and clearly written in a postgraduate exam register. Word choice is varied, collocations are natural, and spelling problems are rare or absent.'),
(@target_rv_id,'exam','vocabulary','B',75,
 'Vocabulary is generally accurate and appropriate, with some range and awareness of formal written English. Occasional collocation or spelling issues do not reduce overall quality.'),
(@target_rv_id,'exam','vocabulary','C',60,
 'Vocabulary is basically sufficient, but range is limited, repetition is noticeable, and some word choice or collocation problems appear.'),
(@target_rv_id,'exam','vocabulary','D',42,
 'Vocabulary is narrow and often repetitive. Inaccurate word choice, weak collocations, or spelling problems noticeably reduce clarity and quality.'),
(@target_rv_id,'exam','vocabulary','E',20,
 'Vocabulary control is very weak. Frequent wrong word choices, serious collocation problems, and pervasive spelling issues obstruct understanding.'),
(@target_rv_id,'exam','grammar','A',90,
 'Grammar control is strong and stable. Complex structures, including clauses and non-finite forms, are used naturally and accurately, with only rare minor slips.'),
(@target_rv_id,'exam','grammar','B',75,
 'Grammar is generally accurate and well controlled. The essay shows some variety in structure, and errors are limited and do not interfere with understanding.'),
(@target_rv_id,'exam','grammar','C',60,
 'Grammar is acceptable overall, but sentence patterns are often simple and visible errors occur in tense, agreement, articles, or prepositions. Meaning remains generally clear.'),
(@target_rv_id,'exam','grammar','D',42,
 'Grammar problems are frequent. Sentence control is unstable, and errors in basic structures and more complex forms noticeably weaken the essay.'),
(@target_rv_id,'exam','grammar','E',20,
 'Grammar control is very poor. Errors are dense enough to make many sentences unstable or hard to understand.'),
(@target_rv_id,'exam','expression','A',90,
 'Expression is natural, formal, and mature. The writing fits the postgraduate exam context well and shows little to no Chinglish, translation-heavy phrasing, or template dependence.'),
(@target_rv_id,'exam','expression','B',75,
 'Expression is mostly natural and appropriately formal. Occasional awkward phrasing appears, but the overall tone and style fit the exam task.'),
(@target_rv_id,'exam','expression','C',60,
 'Expression is understandable but not consistently natural. The writing may sound somewhat formulaic, translated, or insufficiently formal, though meaning is still conveyed.'),
(@target_rv_id,'exam','expression','D',42,
 'Expression is often awkward or stiff. Chinglish, unnatural phrasing, and formulaic writing are common enough to reduce the overall impression.'),
(@target_rv_id,'exam','expression','E',20,
 'Expression is very unnatural. Frequent translation-like sentences, inappropriate tone, or severe awkwardness make the writing difficult to read as acceptable English prose.');


