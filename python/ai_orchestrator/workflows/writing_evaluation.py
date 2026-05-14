from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from python.ai_orchestrator.schemas.routing import RouteRequest, RoutingDecision


class WritingEvaluationWorkflowResult(BaseModel):
    workflow: Literal["writing_evaluation"] = "writing_evaluation"
    status: Literal["needs_clarification", "ready"]
    missing_inputs: list[str] = Field(default_factory=list)
    message: str


def run_writing_evaluation_workflow(
    request: RouteRequest,
    decision: RoutingDecision,
) -> WritingEvaluationWorkflowResult:
    if decision.workflow != "writing_evaluation":
        raise ValueError("writing evaluation workflow requires a writing_evaluation route")

    missing_inputs: list[str] = []
    if not request.context.has_essay_text:
        missing_inputs.append("essay_text")
    if not request.context.has_topic_prompt:
        missing_inputs.append("topic_prompt")

    if missing_inputs:
        return WritingEvaluationWorkflowResult(
            status="needs_clarification",
            missing_inputs=missing_inputs,
            message="Writing evaluation needs the essay text and topic prompt before running.",
        )

    return WritingEvaluationWorkflowResult(
        status="ready",
        message="Writing evaluation inputs are complete.",
    )
