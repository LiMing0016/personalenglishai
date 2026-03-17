-- HighSchool Rubric v1 — 高考英语写作评分标准
-- 对齐 2024 年高考英语写作阅卷原则（应用文15分 / 读后续写25分）
-- MySQL 8 compatible

CREATE TABLE IF NOT EXISTS rubric_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rubric_key VARCHAR(64) NOT NULL,
  stage VARCHAR(32) NOT NULL,
  is_active TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rubric_dimension (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rubric_version_id BIGINT NOT NULL,
  mode VARCHAR(16) NOT NULL,
  dimension_key VARCHAR(32) NOT NULL,
  display_name VARCHAR(32) NOT NULL,
  sort_order INT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dimension (rubric_version_id, mode, dimension_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rubric_level (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rubric_version_id BIGINT NOT NULL,
  mode VARCHAR(16) NOT NULL,
  dimension_key VARCHAR(32) NOT NULL,
  `level` CHAR(1) NOT NULL,
  level_score INT NOT NULL,
  criteria TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_level (rubric_version_id, mode, dimension_key, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------------
-- rubric_version
-- -------------------------------------------------------
INSERT INTO rubric_version (rubric_key, stage, is_active)
SELECT 'highschool-v1', 'highschool', 1
WHERE NOT EXISTS (
  SELECT 1 FROM rubric_version
  WHERE rubric_key = 'highschool-v1' AND stage = 'highschool' AND is_active = 1
);

SET @rv_id = (
  SELECT id FROM rubric_version
  WHERE rubric_key = 'highschool-v1' AND stage = 'highschool' AND is_active = 1
  ORDER BY id DESC LIMIT 1
);

-- -------------------------------------------------------
-- rubric_dimension
-- free mode: 应用文（书信/通知/邀请函等，满分15分）
-- exam mode: 考试写作（读后续写/命题作文，满分25分）
-- -------------------------------------------------------
INSERT INTO rubric_dimension (rubric_version_id, mode, dimension_key, display_name, sort_order)
SELECT @rv_id, src.mode, src.dimension_key, src.display_name, src.sort_order
FROM (
  SELECT 'free' AS mode, 'content_quality'   AS dimension_key, '内容质量'   AS display_name, 1 AS sort_order UNION ALL
  SELECT 'free',         'structure',         '篇章结构',                                      2              UNION ALL
  SELECT 'free',         'vocabulary',        '词汇丰富度',                                    3              UNION ALL
  SELECT 'free',         'grammar',           '语法准确性',                                    4              UNION ALL
  SELECT 'free',         'expression',        '语言自然度',                                    5              UNION ALL
  SELECT 'exam',         'content_quality',   '内容质量',                                      1              UNION ALL
  SELECT 'exam',         'task_achievement',  '任务完成度',                                    2              UNION ALL
  SELECT 'exam',         'structure',         '篇章结构',                                      3              UNION ALL
  SELECT 'exam',         'vocabulary',        '词汇丰富度',                                    4              UNION ALL
  SELECT 'exam',         'grammar',           '语法准确性',                                    5              UNION ALL
  SELECT 'exam',         'expression',        '语言自然度',                                    6
) src
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), sort_order = VALUES(sort_order);

-- -------------------------------------------------------
-- rubric_level — 真实高考水平描述
-- level_score: A=90 B=75 C=60 D=42 E=20
--   free/应用文对应档位：A≈13-15/15  B≈10-12/15  C≈7-9/15  D≈4-6/15  E≈0-3/15
--   exam/读后续写对应：  A≈21-25/25  B≈16-20/25  C≈11-15/25  D≈6-10/25  E≈0-5/25
-- -------------------------------------------------------

-- ============ free mode ============

-- grammar (free)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'free','grammar','A',90,
 'Expert grammatical control. Accurately and naturally uses varied complex structures (relative clauses, adverbial clauses, non-finite verbs, inversion). Errors, if any, are minor slips that do not affect comprehension. Corresponds to near-perfect gaokao essay quality.'),
(@rv_id,'free','grammar','B',75,
 'Good grammatical control. Mostly accurate with occasional minor errors that do not impede communication. Mix of simple and complex sentences with reasonable variety. Occasional tense or agreement slip but overall clear.'),
(@rv_id,'free','grammar','C',60,
 'Adequate grammatical control. Noticeable errors in tense, subject-verb agreement, or preposition use; meaning is generally clear. Relies mainly on simple sentences. Typical of a passing gaokao essay around the average mark range.'),
(@rv_id,'free','grammar','D',42,
 'Limited grammatical control. Frequent errors including tense confusion, wrong prepositions, and subject-verb disagreement that regularly impede understanding. Heavy reliance on simple sentence patterns. Chinese-learner errors are prominent.'),
(@rv_id,'free','grammar','E',20,
 'Very limited grammatical control. Pervasive errors throughout the essay. Almost no correct complete sentences. Meaning is largely unintelligible due to grammatical breakdown.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- vocabulary (free)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'free','vocabulary','A',90,
 'Rich, accurate, and natural vocabulary range. Uses precise higher-register words and idiomatic phrases appropriate to context. No misspellings. Avoids over-reliance on basic words (get/have/very/good). Word choice feels native-like.'),
(@rv_id,'free','vocabulary','B',75,
 'Good vocabulary with some variety. Generally accurate word choice with occasional errors in collocation or spelling that do not obscure meaning. Some higher-level words attempted, mostly correctly.'),
(@rv_id,'free','vocabulary','C',60,
 'Limited vocabulary. Over-reliance on basic words (e.g., good/bad/very/have/make/get/big/small). Noticeable repetition and some misspellings, but the main message is still largely conveyed.'),
(@rv_id,'free','vocabulary','D',42,
 'Very limited vocabulary. Heavy reliance on a small set of basic words. Multiple misspellings. Chinglish word choices are frequent (e.g., "I very like", "have a very good time to do"). Often obscures meaning.'),
(@rv_id,'free','vocabulary','E',20,
 'Extremely limited vocabulary. Pervasive misspellings and wrong word selections. Unable to convey basic meaning. Vocabulary is a major barrier to understanding.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- structure (free)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'free','structure','A',90,
 'Well-organized with clear introduction, body, and conclusion. Logical flow throughout. Smooth and varied transitions that enhance readability. Each paragraph has a clear topic sentence and relevant supporting details.'),
(@rv_id,'free','structure','B',75,
 'Generally well-organized with a recognizable structure. Mostly logical flow with some effective transitions. Minor issues with paragraph coherence or topic sentence clarity, but overall organization is evident.'),
(@rv_id,'free','structure','C',60,
 'Basic structure is present but transitions are mechanical or repetitive (e.g., overuse of "Firstly... Secondly... Thirdly..."). Paragraph organization is uneven. Logic can be followed but requires effort.'),
(@rv_id,'free','structure','D',42,
 'Poorly organized. Paragraphs lack clear focus or are missing entirely. Little or no effective use of transitional devices. Ideas are jumbled or disconnected, making the essay hard to follow.'),
(@rv_id,'free','structure','E',20,
 'No discernible structure. Disconnected sentences with no logical progression. Paragraphing is absent or random. Impossible to identify a clear line of argument or narrative.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- content_quality (free)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'free','content_quality','A',90,
 'Fully develops the topic with specific, relevant details and examples. Ideas are clear, nuanced, and compelling. Stays tightly on topic throughout. Content demonstrates genuine engagement with the prompt.'),
(@rv_id,'free','content_quality','B',75,
 'Develops the topic adequately with some supporting details. Ideas are generally clear and relevant. Minor lapses in focus or depth, but the overall argument or narrative is coherent and on topic.'),
(@rv_id,'free','content_quality','C',60,
 'Addresses the topic at a surface level. Ideas are present but not well-developed. Supporting details are vague or insufficient. Occasional off-topic sentences, but the reader can still identify the main point.'),
(@rv_id,'free','content_quality','D',42,
 'Underdeveloped content. Ideas are unclear or contradictory. Little supporting evidence or examples. Significant portions are off-topic or irrelevant.'),
(@rv_id,'free','content_quality','E',20,
 'Does not meaningfully address the topic. Almost no relevant content. Fails to communicate a clear central idea. The essay appears unrelated to the prompt.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- expression (free)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'free','expression','A',90,
 'Highly natural and fluent expression. Varied sentence patterns that hold the reader''s attention. Effectively avoids Chinglish and Chinese-to-English literal translation. Reads like writing by a proficient English user.'),
(@rv_id,'free','expression','B',75,
 'Mostly natural expression with occasional awkward phrasing or mild Chinglish. Minor unnaturalness does not significantly disrupt reading flow. The overall style is acceptable for a high school essay.'),
(@rv_id,'free','expression','C',60,
 'Somewhat unnatural expression. Noticeable Chinglish or literal translation from Chinese thought patterns. The writing often feels stilted, but meaning is generally understood with some effort.'),
(@rv_id,'free','expression','D',42,
 'Unnatural and stiff expression. Heavy Chinglish throughout. Reads as if translated word-for-word from Chinese. Frequently disrupts understanding and detracts significantly from the essay quality.'),
(@rv_id,'free','expression','E',20,
 'Extremely unnatural expression. Almost entirely Chinglish. Reading is very difficult due to non-English phrasing patterns. The essay cannot be followed without significant guessing.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- ============ exam mode ============

-- grammar (exam)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'exam','grammar','A',90,
 'Expert grammatical control. Accurately and naturally uses varied complex structures (relative clauses, adverbial clauses, non-finite verbs, inversion). Errors, if any, are minor slips that do not affect comprehension.'),
(@rv_id,'exam','grammar','B',75,
 'Good grammatical control. Mostly accurate with occasional minor errors that do not impede communication. Mix of simple and complex sentences with reasonable variety.'),
(@rv_id,'exam','grammar','C',60,
 'Adequate grammatical control. Noticeable errors in tense, subject-verb agreement, or preposition use; meaning is generally clear. Relies mainly on simple sentences.'),
(@rv_id,'exam','grammar','D',42,
 'Limited grammatical control. Frequent errors including tense confusion, wrong prepositions, and subject-verb disagreement that regularly impede understanding.'),
(@rv_id,'exam','grammar','E',20,
 'Very limited grammatical control. Pervasive errors throughout the essay. Almost no correct complete sentences. Meaning is largely unintelligible.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- vocabulary (exam)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'exam','vocabulary','A',90,
 'Rich, accurate, and natural vocabulary range. Uses precise higher-register words and idiomatic phrases. No misspellings. Avoids over-reliance on basic words. Word choice feels natural for the exam task.'),
(@rv_id,'exam','vocabulary','B',75,
 'Good vocabulary with some variety. Generally accurate word choice with occasional errors in collocation or spelling that do not obscure meaning.'),
(@rv_id,'exam','vocabulary','C',60,
 'Limited vocabulary. Over-reliance on basic words. Noticeable repetition and some misspellings, but the main message is still largely conveyed.'),
(@rv_id,'exam','vocabulary','D',42,
 'Very limited vocabulary. Heavy reliance on a small set of basic words. Multiple misspellings. Chinglish word choices frequent and often obscure meaning.'),
(@rv_id,'exam','vocabulary','E',20,
 'Extremely limited vocabulary. Pervasive misspellings and wrong word selections. Unable to convey basic meaning.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- structure (exam)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'exam','structure','A',90,
 'Well-organized with clear and logical progression. Smooth and varied transitions. Each paragraph has a clear focus and relevant supporting details. Overall coherence is excellent.'),
(@rv_id,'exam','structure','B',75,
 'Generally well-organized with mostly logical flow and some effective transitions. Minor issues with paragraph coherence, but overall organization is clear.'),
(@rv_id,'exam','structure','C',60,
 'Basic structure is present but transitions are mechanical or repetitive. Paragraph organization is uneven. Logic can be followed but requires effort.'),
(@rv_id,'exam','structure','D',42,
 'Poorly organized. Paragraphs lack clear focus. Little or no effective transitions. Ideas are jumbled or disconnected.'),
(@rv_id,'exam','structure','E',20,
 'No discernible structure. Disconnected sentences with no logical progression. Impossible to identify a clear line of argument.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- content_quality (exam)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'exam','content_quality','A',90,
 'Fully develops the topic with specific, relevant details. Ideas are clear and compelling. Stays tightly on topic throughout. Content demonstrates genuine engagement with the prompt.'),
(@rv_id,'exam','content_quality','B',75,
 'Develops the topic adequately with some supporting details. Ideas are generally clear and relevant. Minor lapses in focus or depth.'),
(@rv_id,'exam','content_quality','C',60,
 'Addresses the topic at a surface level. Ideas are present but not well-developed. Supporting details are vague or insufficient.'),
(@rv_id,'exam','content_quality','D',42,
 'Underdeveloped content. Ideas are unclear or contradictory. Little supporting evidence. Significant portions are off-topic.'),
(@rv_id,'exam','content_quality','E',20,
 'Does not meaningfully address the topic. Almost no relevant content. Fails to communicate a clear central idea.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- expression (exam)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'exam','expression','A',90,
 'Highly natural and fluent expression. Varied sentence patterns. Effectively avoids Chinglish. Reads like writing by a proficient English user.'),
(@rv_id,'exam','expression','B',75,
 'Mostly natural expression with occasional awkward phrasing or mild Chinglish. Minor unnaturalness does not significantly disrupt reading flow.'),
(@rv_id,'exam','expression','C',60,
 'Somewhat unnatural expression. Noticeable Chinglish or literal translation from Chinese thought patterns. Meaning is generally understood with some effort.'),
(@rv_id,'exam','expression','D',42,
 'Unnatural and stiff expression. Heavy Chinglish throughout. Reads as if translated word-for-word from Chinese.'),
(@rv_id,'exam','expression','E',20,
 'Extremely unnatural expression. Almost entirely Chinglish. Reading is very difficult due to non-English phrasing patterns.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- task_achievement (exam only)
INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@rv_id,'exam','task_achievement','A',90,
 'Fully addresses ALL required writing points with no omissions. Correct format throughout (salutation, closing, appropriate paragraphing for letters/notices). Word count meets requirements. Perfectly fits the task scenario and register.'),
(@rv_id,'exam','task_achievement','B',75,
 'Addresses most required writing points with only minor omissions of secondary details. Format is mostly correct. Word count approximately meets requirements. Fits the task scenario well.'),
(@rv_id,'exam','task_achievement','C',60,
 'Addresses some required writing points but with notable gaps (e.g., missing one key point). Format is partially correct (e.g., missing salutation OR closing). Word count slightly below requirement.'),
(@rv_id,'exam','task_achievement','D',42,
 'Partially addresses the task but with major gaps: only 1-2 of the required points are covered, or the essay is loosely related to the topic but drifts significantly. Format has multiple errors. Word count clearly insufficient.'),
(@rv_id,'exam','task_achievement','E',20,
 'COMPLETELY OFF-TOPIC or fails entirely: the essay topic is entirely different from the required task (e.g., task requires writing about New Year celebrations but the essay discusses environmental protection or another unrelated subject). Any essay that does not engage with the required topic at all MUST receive E. Format absent. Word count irrelevant.')
ON DUPLICATE KEY UPDATE level_score=VALUES(level_score), criteria=VALUES(criteria);

-- -------------------------------------------------------
-- postgrad rubric v1 (exam only)
-- 正式考研写作标准：postgrad-exam-v1
-- 只保留 exam 模式，不再保留占位 postgrad-v1。
-- -------------------------------------------------------
DELETE FROM rubric_level
WHERE rubric_version_id IN (
  SELECT id FROM (
    SELECT id FROM rubric_version
    WHERE rubric_key = 'postgrad-v1' AND stage = 'postgrad'
  ) legacy_versions
);

DELETE FROM rubric_dimension
WHERE rubric_version_id IN (
  SELECT id FROM (
    SELECT id FROM rubric_version
    WHERE rubric_key = 'postgrad-v1' AND stage = 'postgrad'
  ) legacy_versions
);

DELETE FROM rubric_version
WHERE rubric_key = 'postgrad-v1' AND stage = 'postgrad';

INSERT INTO rubric_version (rubric_key, stage, is_active)
SELECT 'postgrad-exam-v1', 'postgrad', 1
WHERE NOT EXISTS (
  SELECT 1 FROM rubric_version
  WHERE rubric_key = 'postgrad-exam-v1' AND stage = 'postgrad' AND is_active = 1
);

SET @postgrad_rv_id = (
  SELECT id FROM rubric_version
  WHERE rubric_key = 'postgrad-exam-v1' AND stage = 'postgrad' AND is_active = 1
  ORDER BY id DESC LIMIT 1
);

INSERT INTO rubric_dimension (rubric_version_id, mode, dimension_key, display_name, sort_order)
SELECT @postgrad_rv_id, src.mode, src.dimension_key, src.display_name, src.sort_order
FROM (
  SELECT 'exam' AS mode, 'content_quality'   AS dimension_key, '内容质量'   AS display_name, 1 AS sort_order UNION ALL
  SELECT 'exam',         'task_achievement',  '任务完成度',                                    2              UNION ALL
  SELECT 'exam',         'structure',         '篇章结构',                                      3              UNION ALL
  SELECT 'exam',         'vocabulary',        '词汇丰富度',                                    4              UNION ALL
  SELECT 'exam',         'grammar',           '语法准确性',                                    5              UNION ALL
  SELECT 'exam',         'expression',        '语言自然度',                                    6
) src
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), sort_order = VALUES(sort_order);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@postgrad_rv_id,'exam','task_achievement','A',90,
 'Fully completes the required task. For task1, purpose, audience, tone, format, and all key points are accurate and complete. For task2, the essay clearly describes the material, interprets its meaning, and offers relevant commentary tightly connected to the prompt.'),
(@postgrad_rv_id,'exam','task_achievement','B',75,
 'Completes most of the required task. For task1, the main communicative purpose and most key points are covered with only minor omissions. For task2, description, interpretation, and commentary are all present, though one step may be less fully developed.'),
(@postgrad_rv_id,'exam','task_achievement','C',60,
 'Completes the core task but with visible gaps. For task1, some required points, format, or tone are incomplete. For task2, the essay is generally on task but often under-describes the material or gives commentary that is too general.'),
(@postgrad_rv_id,'exam','task_achievement','D',42,
 'Only partially completes the task. For task1, key communicative actions are missing or tone and format are inappropriate. For task2, the essay usually describes without explaining, comments without describing the material, or drifts noticeably away from the required task.'),
(@postgrad_rv_id,'exam','task_achievement','E',20,
 'Fails to complete the required task. The essay is seriously off topic, functionally incomplete, or only weakly related to the prompt. For task2, material interpretation is largely absent. For task1, the functional writing purpose is mostly missing.')
ON DUPLICATE KEY UPDATE level_score = VALUES(level_score), criteria = VALUES(criteria);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@postgrad_rv_id,'exam','content_quality','A',90,
 'Content is substantial, relevant, and well developed. For task2, the essay extracts the core meaning of the material and develops a mature, meaningful response. For task1, the information is specific, useful, and clearly serves the communicative purpose.'),
(@postgrad_rv_id,'exam','content_quality','B',75,
 'Content is solid and generally effective. Ideas are relevant and developed with some supporting detail, though depth or precision may be slightly limited.'),
(@postgrad_rv_id,'exam','content_quality','C',60,
 'Content is basically adequate but underdeveloped. Ideas are present, yet explanation, support, or detail remains surface-level and conventional.'),
(@postgrad_rv_id,'exam','content_quality','D',42,
 'Content is weak, repetitive, or overly general. Support is limited, and the essay offers little effective development beyond basic statements.'),
(@postgrad_rv_id,'exam','content_quality','E',20,
 'Content is seriously insufficient. The essay relies on empty formulaic statements, provides almost no useful information, and fails to communicate a meaningful response.')
ON DUPLICATE KEY UPDATE level_score = VALUES(level_score), criteria = VALUES(criteria);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@postgrad_rv_id,'exam','structure','A',90,
 'The essay is clearly and effectively organized. For task1, information order and format are natural and appropriate. For task2, the introduction, development, and conclusion are distinct, and the progression from description to interpretation to commentary is smooth and coherent.'),
(@postgrad_rv_id,'exam','structure','B',75,
 'Organization is generally clear. Paragraphing and sequencing mostly support the task well, with only minor weaknesses in transitions or emphasis.'),
(@postgrad_rv_id,'exam','structure','C',60,
 'A basic structure is present, but transitions, paragraphing, or logical flow feel mechanical or uneven. The reader can follow the essay with some effort.'),
(@postgrad_rv_id,'exam','structure','D',42,
 'Organization is loose or poorly controlled. Paragraph function is unclear, sequencing is not ideal, and coherence is noticeably weakened.'),
(@postgrad_rv_id,'exam','structure','E',20,
 'The essay has little or no effective structure. Sentences or ideas are piled together without clear progression, making the writing difficult to follow.')
ON DUPLICATE KEY UPDATE level_score = VALUES(level_score), criteria = VALUES(criteria);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@postgrad_rv_id,'exam','vocabulary','A',90,
 'Vocabulary is accurate, appropriate, and clearly written in a postgraduate exam register. Word choice is varied, collocations are natural, and spelling problems are rare or absent.'),
(@postgrad_rv_id,'exam','vocabulary','B',75,
 'Vocabulary is generally accurate and appropriate, with some range and awareness of formal written English. Occasional collocation or spelling issues do not reduce overall quality.'),
(@postgrad_rv_id,'exam','vocabulary','C',60,
 'Vocabulary is basically sufficient, but range is limited, repetition is noticeable, and some word choice or collocation problems appear.'),
(@postgrad_rv_id,'exam','vocabulary','D',42,
 'Vocabulary is narrow and often repetitive. Inaccurate word choice, weak collocations, or spelling problems noticeably reduce clarity and quality.'),
(@postgrad_rv_id,'exam','vocabulary','E',20,
 'Vocabulary control is very weak. Frequent wrong word choices, serious collocation problems, and pervasive spelling issues obstruct understanding.')
ON DUPLICATE KEY UPDATE level_score = VALUES(level_score), criteria = VALUES(criteria);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@postgrad_rv_id,'exam','grammar','A',90,
 'Grammar control is strong and stable. Complex structures, including clauses and non-finite forms, are used naturally and accurately, with only rare minor slips.'),
(@postgrad_rv_id,'exam','grammar','B',75,
 'Grammar is generally accurate and well controlled. The essay shows some variety in structure, and errors are limited and do not interfere with understanding.'),
(@postgrad_rv_id,'exam','grammar','C',60,
 'Grammar is acceptable overall, but sentence patterns are often simple and visible errors occur in tense, agreement, articles, or prepositions. Meaning remains generally clear.'),
(@postgrad_rv_id,'exam','grammar','D',42,
 'Grammar problems are frequent. Sentence control is unstable, and errors in basic structures and more complex forms noticeably weaken the essay.'),
(@postgrad_rv_id,'exam','grammar','E',20,
 'Grammar control is very poor. Errors are dense enough to make many sentences unstable or hard to understand.')
ON DUPLICATE KEY UPDATE level_score = VALUES(level_score), criteria = VALUES(criteria);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, `level`, level_score, criteria) VALUES
(@postgrad_rv_id,'exam','expression','A',90,
 'Expression is natural, formal, and mature. The writing fits the postgraduate exam context well and shows little to no Chinglish, translation-heavy phrasing, or template dependence.'),
(@postgrad_rv_id,'exam','expression','B',75,
 'Expression is mostly natural and appropriately formal. Occasional awkward phrasing appears, but the overall tone and style fit the exam task.'),
(@postgrad_rv_id,'exam','expression','C',60,
 'Expression is understandable but not consistently natural. The writing may sound somewhat formulaic, translated, or insufficiently formal, though meaning is still conveyed.'),
(@postgrad_rv_id,'exam','expression','D',42,
 'Expression is often awkward or stiff. Chinglish, unnatural phrasing, and formulaic writing are common enough to reduce the overall impression.'),
(@postgrad_rv_id,'exam','expression','E',20,
 'Expression is very unnatural. Frequent translation-like sentences, inappropriate tone, or severe awkwardness make the writing difficult to read as acceptable English prose.')
ON DUPLICATE KEY UPDATE level_score = VALUES(level_score), criteria = VALUES(criteria);


