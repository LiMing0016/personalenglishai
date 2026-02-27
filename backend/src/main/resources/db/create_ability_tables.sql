-- user_ability_profile: current ability profile, one row per user
CREATE TABLE IF NOT EXISTS user_ability_profile (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'users.id',
  stage TINYINT UNSIGNED NOT NULL COMMENT '1=high_school,2=CET4,3=CET6,4=postgrad',

  task_score       DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '0-100 smoothed',
  coherence_score  DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '0-100 smoothed',
  grammar_score    DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '0-100 smoothed',
  vocabulary_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '0-100 smoothed',
  structure_score  DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '0-100 smoothed',
  variety_score    DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '0-100 smoothed',

  assessed_score   DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '0-100 aggregated',

  confidence       DECIMAL(3,2) DEFAULT NULL COMMENT '0-1 reserved',
  sample_count     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'effective essay count',

  model_version    VARCHAR(20) NOT NULL DEFAULT 'v1',
  rubric_version   VARCHAR(20) NOT NULL DEFAULT 'v1',

  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id),
  KEY idx_stage (stage),

  CONSTRAINT chk_stage_uap CHECK (stage IN (1,2,3,4)),
  CONSTRAINT chk_task_uap       CHECK (task_score BETWEEN 0 AND 100),
  CONSTRAINT chk_coherence_uap  CHECK (coherence_score BETWEEN 0 AND 100),
  CONSTRAINT chk_grammar_uap    CHECK (grammar_score BETWEEN 0 AND 100),
  CONSTRAINT chk_vocab_uap      CHECK (vocabulary_score BETWEEN 0 AND 100),
  CONSTRAINT chk_structure_uap  CHECK (structure_score BETWEEN 0 AND 100),
  CONSTRAINT chk_variety_uap    CHECK (variety_score BETWEEN 0 AND 100),
  CONSTRAINT chk_assessed_uap   CHECK (assessed_score BETWEEN 0 AND 100),
  CONSTRAINT chk_confidence_uap CHECK (confidence IS NULL OR (confidence BETWEEN 0 AND 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- writing_assessment: one record per evaluation event
CREATE TABLE IF NOT EXISTS writing_assessment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  essay_id BIGINT UNSIGNED NOT NULL COMMENT 'essay id',
  stage TINYINT UNSIGNED NOT NULL COMMENT '1=high_school,2=CET4,3=CET6,4=postgrad',

  task_score       DECIMAL(5,2) NOT NULL,
  coherence_score  DECIMAL(5,2) NOT NULL,
  grammar_score    DECIMAL(5,2) NOT NULL,
  vocabulary_score DECIMAL(5,2) NOT NULL,
  structure_score  DECIMAL(5,2) NOT NULL,
  variety_score    DECIMAL(5,2) NOT NULL,

  ai_total DECIMAL(5,2) DEFAULT NULL COMMENT 'AI total score for record only',

  word_count INT UNSIGNED DEFAULT NULL,
  grammar_error_count INT UNSIGNED DEFAULT NULL,
  advanced_vocab_count INT UNSIGNED DEFAULT NULL,
  sentence_count INT UNSIGNED DEFAULT NULL,

  model_version  VARCHAR(20) NOT NULL DEFAULT 'v1',
  rubric_version VARCHAR(20) NOT NULL DEFAULT 'v1',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_essay (essay_id),

  CONSTRAINT chk_stage_wa CHECK (stage IN (1,2,3,4)),
  CONSTRAINT chk_task_wa       CHECK (task_score BETWEEN 0 AND 100),
  CONSTRAINT chk_coherence_wa  CHECK (coherence_score BETWEEN 0 AND 100),
  CONSTRAINT chk_grammar_wa    CHECK (grammar_score BETWEEN 0 AND 100),
  CONSTRAINT chk_vocab_wa      CHECK (vocabulary_score BETWEEN 0 AND 100),
  CONSTRAINT chk_structure_wa  CHECK (structure_score BETWEEN 0 AND 100),
  CONSTRAINT chk_variety_wa    CHECK (variety_score BETWEEN 0 AND 100),
  CONSTRAINT chk_ai_total_wa   CHECK (ai_total IS NULL OR (ai_total BETWEEN 0 AND 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

