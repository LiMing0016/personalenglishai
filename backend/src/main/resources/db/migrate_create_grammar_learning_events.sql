CREATE TABLE IF NOT EXISTS grammar_learning_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(128) NOT NULL UNIQUE,

  user_id BIGINT NOT NULL,
  conversation_id VARCHAR(64) NULL,
  message_id VARCHAR(64) NULL,

  event_type VARCHAR(64) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,

  study_stage VARCHAR(32) NULL,
  assistant_mode VARCHAR(32) NULL,
  source_agent VARCHAR(64) NULL,
  task_type VARCHAR(64) NULL,

  content_origin VARCHAR(32) NOT NULL DEFAULT 'user_input',
  profile_eligible TINYINT(1) NOT NULL DEFAULT 1,
  confidence DECIMAL(4,3) NULL,

  schema_version VARCHAR(32) NULL,
  skill_version VARCHAR(32) NULL,
  taxonomy_version VARCHAR(32) NULL,
  prompt_version VARCHAR(32) NULL,
  model_version VARCHAR(64) NULL,

  grammar_question_type VARCHAR(64) NULL,
  grammar_error_type VARCHAR(64) NULL,
  style_issue_type VARCHAR(64) NULL,
  severity VARCHAR(16) NULL,
  sentence_hash VARCHAR(128) NULL,

  payload_json JSON NOT NULL,

  created_at_db DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  INDEX idx_user_type_time (user_id, event_type, occurred_at),
  INDEX idx_user_profile_time (user_id, profile_eligible, occurred_at),
  INDEX idx_conversation_message (conversation_id, message_id),
  INDEX idx_sentence_error (user_id, sentence_hash, grammar_error_type),
  INDEX idx_created_at_db (created_at_db)
);

CREATE TABLE IF NOT EXISTS user_grammar_profiles (
  user_id BIGINT NOT NULL,
  profile_scope VARCHAR(16) NOT NULL,
  aggregation_version VARCHAR(32) NOT NULL,
  source_max_occurred_at DATETIME(3) NULL,
  source_event_count INT NOT NULL DEFAULT 0,
  profile_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id, profile_scope)
);
