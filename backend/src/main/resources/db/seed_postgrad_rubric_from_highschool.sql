-- Seed postgrad rubric by cloning the current highschool rubric.
-- 用于已有数据库快速补齐 postgrad 的 active rubric，避免评分 fallback 到 highschool。

SET @source_rv_id = (
  SELECT id FROM rubric_version
  WHERE rubric_key = 'highschool-v1' AND stage = 'highschool' AND is_active = 1
  ORDER BY id DESC LIMIT 1
);

INSERT INTO rubric_version (rubric_key, stage, is_active)
SELECT 'postgrad-v1', 'postgrad', 1
WHERE NOT EXISTS (
  SELECT 1 FROM rubric_version
  WHERE rubric_key = 'postgrad-v1' AND stage = 'postgrad' AND is_active = 1
);

SET @target_rv_id = (
  SELECT id FROM rubric_version
  WHERE rubric_key = 'postgrad-v1' AND stage = 'postgrad' AND is_active = 1
  ORDER BY id DESC LIMIT 1
);

INSERT INTO rubric_dimension (rubric_version_id, mode, dimension_key, display_name, sort_order)
SELECT @target_rv_id, d.mode, d.dimension_key, d.display_name, d.sort_order
FROM rubric_dimension d
WHERE d.rubric_version_id = @source_rv_id
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), sort_order = VALUES(sort_order);

INSERT INTO rubric_level (rubric_version_id, mode, dimension_key, level, level_score, criteria)
SELECT @target_rv_id, l.mode, l.dimension_key, l.level, l.level_score, l.criteria
FROM rubric_level l
WHERE l.rubric_version_id = @source_rv_id
ON DUPLICATE KEY UPDATE level_score = VALUES(level_score), criteria = VALUES(criteria);
