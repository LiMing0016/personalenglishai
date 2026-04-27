CREATE TABLE IF NOT EXISTS writing_task_metadata (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  document_public_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,

  study_stage VARCHAR(32) NULL,
  assistant_mode VARCHAR(32) NULL,
  prompt_text TEXT NULL,
  task_type VARCHAR(64) NOT NULL,
  central_task TEXT NOT NULL,

  must_answer_points_json JSON NOT NULL,
  writing_focus_json JSON NOT NULL,
  risk_points_json JSON NOT NULL,
  recommended_structure_json JSON NOT NULL,
  rubric_focus_json JSON NOT NULL,

  metadata_version VARCHAR(64) NOT NULL,
  rubric_source VARCHAR(64) NOT NULL,

  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  UNIQUE KEY uk_writing_task_metadata_document (document_id),
  INDEX idx_writing_task_metadata_user (user_id),
  INDEX idx_writing_task_metadata_public_doc (document_public_id)
);
