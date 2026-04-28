from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal

from pydantic import BaseModel, Field

from .routing import RoutingIntent

ContinuationRelation = Literal[
    "new_task",
    "continue_previous_task",
    "modify_previous_output",
    "clarify_previous_task",
    "switch_task",
    "out_of_scope",
    "ambiguous",
]

ContinuationAction = Literal[
    "more_options",
    "expand_detail",
    "simplify",
    "make_harder",
    "rewrite_variant",
    "continue_sequence",
    "compare_options",
    "generate_practice",
    "none",
]

ActiveTaskStatus = Literal["active", "paused", "completed", "abandoned"]

TaskOutputType = Literal[
    "plan",
    "polished_text",
    "translation",
    "score_feedback",
    "vocab_explanation",
    "sentence_analysis",
    "practice_set",
    "ability_profile",
    "mixed_result",
]


def _now_utc() -> datetime:
    return datetime.now(timezone.utc)


class ActiveTaskState(BaseModel):
    conversation_id: str
    active_intent: RoutingIntent
    active_agent: str
    task_title: str
    task_summary: str
    user_goal: str | None = None
    last_user_message: str
    last_assistant_summary: str | None = None
    last_output_type: TaskOutputType
    continuation_capabilities: set[ContinuationAction] = Field(default_factory=set)
    status: ActiveTaskStatus = "active"
    turn_id: str
    updated_at: datetime = Field(default_factory=_now_utc)
    expires_at: datetime | None = None


class ContinuationClassifierInput(BaseModel):
    current_user_message: str
    active_task_state: ActiveTaskState | None = None
    recent_messages_summary: str | None = None
    study_stage: str | None = None
    assistant_mode: str | None = None


class ContinuationDecision(BaseModel):
    relation: ContinuationRelation
    resolved_intent: RoutingIntent | None = None
    continuation_action: ContinuationAction = "none"
    target_task_title: str | None = None
    reason: str
    confidence: float = Field(ge=0.0, le=1.0)
