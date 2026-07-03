from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

RoutingIntent = Literal[
    "polish",
    "sentence_structure",
    "vocab",
    "translation",
    "scoring",
    "practice_design",
    "ability_profile",
    "learning_planner",
]


class HandoffRoutingMetadata(BaseModel):
    intent: RoutingIntent = Field(description="Normalized routing intent chosen by the router.")
    reason: str = Field(description="Brief reason for choosing this specialist.")
    confidence: float = Field(ge=0.0, le=1.0, description="Router confidence from 0 to 1.")


RouteDecisionIntent = Literal[
    "writing_evaluation",
    "first_draft_coach",
    "realtime_sentence_feedback",
    "polish",
    "sentence_structure",
    "vocab",
    "translation",
    "scoring",
    "practice_design",
    "ability_profile",
    "learning_planner",
    "free_chat",
    "out_of_scope",
]
RouteType = Literal["run_workflow", "ask_clarification", "answer_direct", "out_of_scope"]
WorkflowName = Literal[
    "writing_evaluation",
    "first_draft_coach",
    "realtime_sentence_feedback",
    "specialist_single_turn",
]
TargetAgent = Literal[
    "writing_evaluation",
    "first_draft_coach",
    "realtime_sentence_feedback",
    "polish",
    "sentence_structure",
    "vocab",
    "translation",
    "scoring",
    "practice_design",
    "ability_profile",
    "learning_planner",
]


class RouteConversationHistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class RouteRequestContext(BaseModel):
    essay_text: str | None = None
    topic_prompt: str | None = None
    selected_text: str | None = None
    current_page: str | None = None
    conversation_history: list["RouteConversationHistoryMessage"] = Field(default_factory=list)
    active_task: dict[str, Any] | None = None

    @property
    def has_essay_text(self) -> bool:
        return bool((self.essay_text or "").strip())

    @property
    def has_topic_prompt(self) -> bool:
        return bool((self.topic_prompt or "").strip())

    @property
    def has_selected_text(self) -> bool:
        return bool((self.selected_text or "").strip())


class RouteRequest(BaseModel):
    message: str
    conversation_id: str | None = None
    user_id: str | None = None
    study_stage: str | None = None
    assistant_mode: str | None = None
    context: RouteRequestContext = Field(default_factory=RouteRequestContext)


class RoutingNormalizedInputs(BaseModel):
    has_essay_text: bool = False
    has_topic_prompt: bool = False
    has_selected_text: bool = False
    current_page: str | None = None


class RoutingDecision(BaseModel):
    intent: RouteDecisionIntent
    route_type: RouteType
    workflow: WorkflowName | None = None
    target_agent: TargetAgent | None = None
    confidence: float = Field(ge=0.0, le=1.0)
    required_inputs: list[str] = Field(default_factory=list)
    missing_inputs: list[str] = Field(default_factory=list)
    normalized_inputs: RoutingNormalizedInputs = Field(default_factory=RoutingNormalizedInputs)
    reason: str

    @model_validator(mode="after")
    def validate_route_contract(self) -> "RoutingDecision":
        if self.route_type == "run_workflow":
            if self.workflow is None:
                raise ValueError("run_workflow decisions require workflow")
            if self.target_agent is None:
                raise ValueError("run_workflow decisions require target_agent")

        if self.route_type == "ask_clarification" and not self.missing_inputs:
            raise ValueError("ask_clarification decisions require missing_inputs")

        if self.route_type == "out_of_scope" and self.intent != "out_of_scope":
            raise ValueError("out_of_scope route_type requires out_of_scope intent")

        return self
