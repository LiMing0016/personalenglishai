from unittest.mock import patch

from python.ai_orchestrator.agents.vocabulary_image_recognition import (
    build_vocabulary_image_recognition_agent,
)
from python.ai_orchestrator.prompts import agents as prompt_agents
from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.vocabulary_image_recognition import (
    VocabularyImageRecognitionModelOutput,
)


def test_prompt_contains_required_extraction_and_output_rules() -> None:
    instructions = load_agent_instructions("vocabulary_image_recognition")

    for section in ("Goal", "Extraction order", "Spelling policy", "Output", "Prohibitions"):
        assert f"# {section}" in instructions

    assert "1. Isolated English vocabulary candidates" in instructions
    assert "2. English words in vocabulary lists or tables" in instructions
    assert "3. Other visible English words only when they are clearly intended for study" in instructions
    assert "visible evidence" in instructions
    assert "Do not silently correct" in instructions
    assert "Do not generate definitions" in instructions
    assert "Do not output Markdown" in instructions
    assert "at most 30 candidates" in instructions
    assert "at most 3 suggestions" in instructions


def test_prompt_is_registered_as_structured_background_work() -> None:
    assert prompt_agents._PROMPT_FILES["vocabulary_image_recognition"] == (
        "agent_instructions/vocabulary_image_recognition.md"
    )
    assert "vocabulary_image_recognition" in prompt_agents._STRUCTURED_OUTPUT_ONLY_AGENT_KEYS
    assert "vocabulary_image_recognition" in prompt_agents._BACKGROUND_JOB_AGENT_KEYS

    instructions = load_agent_instructions("vocabulary_image_recognition")
    assert "基于 OpenAI Agents SDK" not in instructions
    assert "用户画像上下文" not in instructions
    assert "所有面向用户的学习助手回复" not in instructions


def test_agent_factory_uses_resolved_prompt_and_structured_output_type() -> None:
    prompt_kwargs = {"instructions": "recognize vocabulary from an image"}
    with patch(
        "python.ai_orchestrator.agents.vocabulary_image_recognition.resolve_agent_prompt_kwargs",
        return_value=prompt_kwargs,
    ) as resolve:
        agent = build_vocabulary_image_recognition_agent("test-model")

    resolve.assert_called_once_with("vocabulary_image_recognition")
    assert agent.name == "Vocabulary image recognition"
    assert agent.model == "test-model"
    assert agent.output_type is VocabularyImageRecognitionModelOutput
    assert agent.instructions == prompt_kwargs["instructions"]
