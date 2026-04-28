from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

GrammarQuestionType = Literal[
    "tense",
    "relative_clause",
    "adverbial_clause",
    "noun_clause",
    "non_finite_verb",
    "passive_voice",
    "subjunctive_mood",
    "inversion",
    "subject_verb_agreement",
    "article",
    "preposition",
    "comparison",
    "sentence_pattern",
    "other",
]

GrammarErrorType = Literal[
    "tense",
    "subject_verb_agreement",
    "word_order",
    "article",
    "preposition",
    "word_form",
    "collocation",
    "sentence_fragment",
    "run_on_sentence",
    "punctuation",
    "spelling",
    "other",
]

StyleIssueType = Literal["informal_expression", "word_choice", "cohesion", "academic_tone", "other"]

Severity = Literal["low", "medium", "high"]
ContentOrigin = Literal["user_input", "user_submission", "assistant_draft", "quoted_text", "exercise_prompt"]
ProfileScope = Literal["7d", "30d", "all"]


class GrammarEventContext(BaseModel):
    user_id: int
    message_id: str
    conversation_id: str | None = None
    study_stage: str | None = None
    assistant_mode: str | None = None


class GrammarEventBase(BaseModel):
    user_id: int
    conversation_id: str
    message_id: str
    occurred_at: datetime
    study_stage: str | None = None
    assistant_mode: str | None = None
    content_origin: ContentOrigin = "user_input"
    profile_eligible: bool = True
    confidence: float = Field(ge=0.0, le=1.0)
    schema_version: str = "grammar-event@v2"
    skill_version: str | None = None
    taxonomy_version: str | None = None
    prompt_version: str | None = None
    model_version: str | None = None


class GrammarQuestionEvent(GrammarEventBase):
    event_type: Literal["grammar_question_asked"] = "grammar_question_asked"
    grammar_question_type: GrammarQuestionType
    topic_label: str
    source_text: str


class GrammarErrorEvent(GrammarEventBase):
    event_type: Literal["grammar_error_detected"] = "grammar_error_detected"
    source_agent: str
    task_type: str
    grammar_error_type: GrammarErrorType
    severity: Severity
    span: str
    correction: str
    sentence_hash: str | None = None


class StyleSuggestionEvent(GrammarEventBase):
    event_type: Literal["style_suggestion_detected"] = "style_suggestion_detected"
    source_agent: str
    task_type: str
    style_issue_type: StyleIssueType
    span: str
    suggestion: str
    reason: str


class GrammarSampleCheckedEvent(GrammarEventBase):
    event_type: Literal["grammar_sample_checked"] = "grammar_sample_checked"
    source_agent: str
    task_type: str
    sentence_hash: str
    has_grammar_issue: bool


class GrammarTopicProfileItem(BaseModel):
    type: GrammarQuestionType
    label: str
    count: int
    last_seen: datetime


class GrammarErrorProfileItem(BaseModel):
    type: GrammarErrorType
    count: int
    weighted_score: float
    last_seen: datetime


class StyleSuggestionProfileItem(BaseModel):
    type: StyleIssueType
    count: int
    last_seen: datetime


class GrammarInterestProfile(BaseModel):
    top_topics: list[GrammarTopicProfileItem] = Field(default_factory=list)


class GrammarErrorProfile(BaseModel):
    top_errors: list[GrammarErrorProfileItem] = Field(default_factory=list)
    repeated_errors: list[GrammarErrorType] = Field(default_factory=list)
    checked_sentence_count: int = 0
    error_sentence_count: int = 0
    clean_sentence_rate: float | None = None


class StyleProfile(BaseModel):
    top_style_suggestions: list[StyleSuggestionProfileItem] = Field(default_factory=list)


class UserGrammarProfile(BaseModel):
    user_id: int
    profile_scope: ProfileScope = "30d"
    aggregation_version: str = "grammar-profile@v2"
    source_event_count: int = 0
    source_max_occurred_at: datetime | None = None
    grammar_interest_profile: GrammarInterestProfile = Field(default_factory=GrammarInterestProfile)
    grammar_error_profile: GrammarErrorProfile = Field(default_factory=GrammarErrorProfile)
    style_profile: StyleProfile = Field(default_factory=StyleProfile)
