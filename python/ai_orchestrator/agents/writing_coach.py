from __future__ import annotations

from agents import Agent

from ..prompts.resolver import resolve_agent_prompt_kwargs
from ..schemas.assistant_request import AssistantRequest
from ..schemas.writing_coach import StructuredWritingCoachAction
from ..schemas.writing_coach import WritingCoachFinalDraftOutput
from ..schemas.writing_coach import WritingCoachNextSectionOutput
from ..schemas.writing_coach import WritingCoachOutlineOutput
from ..schemas.writing_coach import WritingCoachPolishOutput
from ..schemas.writing_coach import WritingCoachTopicRelevanceOutput
from ..schemas.writing_coach import WritingCoachTopicAnalysisOutput


_STAGE_OUTPUT_TYPES = {
    "analyze": WritingCoachTopicAnalysisOutput,
    "outline": WritingCoachOutlineOutput,
    "next": WritingCoachNextSectionOutput,
    "topic": WritingCoachTopicRelevanceOutput,
    "polish": WritingCoachPolishOutput,
    "draft": WritingCoachFinalDraftOutput,
}

_STAGE_AGENT_NAMES = {
    "analyze": "Writing Coach Topic Analysis Agent",
    "outline": "Writing Coach Outline Agent",
    "next": "Writing Coach Next Section Agent",
    "topic": "Writing Coach Topic Relevance Agent",
    "polish": "Writing Coach Polish Agent",
    "draft": "Writing Coach Final Draft Agent",
}


def structured_writing_coach_action(request: AssistantRequest) -> StructuredWritingCoachAction | None:
    if request.intent != "first_draft_coach":
        return None
    action = request.writing_coach_context.action if request.writing_coach_context else None
    if action in _STAGE_OUTPUT_TYPES:
        return action
    return None


def writing_coach_stage_agent_name(action: StructuredWritingCoachAction) -> str:
    return _STAGE_AGENT_NAMES[action]


def create_writing_coach_stage_agent(action: StructuredWritingCoachAction, model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("writing_coach_stage", dynamic=True)
    return Agent(
        name=writing_coach_stage_agent_name(action),
        model=model,
        output_type=_STAGE_OUTPUT_TYPES[action],
        **prompt_kwargs,
    )
