from __future__ import annotations

from datetime import datetime, timezone
import unittest

from pydantic import ValidationError

from python.ai_orchestrator.schemas.grammar_profile import (
    GrammarEventContext,
    GrammarErrorEvent,
    GrammarQuestionEvent,
    GrammarSampleCheckedEvent,
    StyleSuggestionEvent,
)
from python.ai_orchestrator.services.grammar_profile import build_grammar_profile, build_grammar_profiles
from python.ai_orchestrator.services.grammar_profile import build_stable_event_id


class GrammarProfileEventSchemaTest(unittest.TestCase):
    def test_grammar_question_event_uses_stable_topic_key(self) -> None:
        event = GrammarQuestionEvent(
            user_id=1,
            conversation_id="c-1",
            message_id="m-1",
            occurred_at=datetime(2026, 4, 25, tzinfo=timezone.utc),
            study_stage="toefl",
            assistant_mode="chat",
            grammar_question_type="relative_clause",
            topic_label="定语从句",
            source_text="which 和 that 有什么区别？",
            confidence=0.92,
        )

        self.assertEqual(event.event_type, "grammar_question_asked")
        self.assertEqual(event.grammar_question_type, "relative_clause")
        self.assertEqual(event.content_origin, "user_input")
        self.assertTrue(event.profile_eligible)
        self.assertEqual(event.schema_version, "grammar-event@v2")

    def test_grammar_error_event_rejects_unknown_error_type(self) -> None:
        with self.assertRaises(ValidationError):
            GrammarErrorEvent(
                user_id=1,
                conversation_id="c-1",
                message_id="m-1",
                occurred_at=datetime(2026, 4, 25, tzinfo=timezone.utc),
                study_stage="toefl",
                assistant_mode="writing",
                source_agent="polish",
                task_type="sentence_polish",
                grammar_error_type="too_plain",
                severity="medium",
                span="Reading is good",
                correction="Reading is beneficial",
                confidence=0.9,
            )

    def test_style_suggestion_is_not_a_grammar_error(self) -> None:
        event = StyleSuggestionEvent(
            user_id=1,
            conversation_id="c-1",
            message_id="m-1",
            occurred_at=datetime(2026, 4, 25, tzinfo=timezone.utc),
            study_stage="toefl",
            source_agent="polish",
            task_type="sentence_polish",
            style_issue_type="academic_tone",
            span="a lot of",
            suggestion="a significant number of",
            reason="TOEFL 写作中更正式",
            confidence=0.84,
        )

        self.assertEqual(event.event_type, "style_suggestion_detected")
        self.assertEqual(event.style_issue_type, "academic_tone")

    def test_sample_checked_event_records_denominator(self) -> None:
        event = GrammarSampleCheckedEvent(
            user_id=1,
            conversation_id="c-1",
            message_id="m-1",
            occurred_at=datetime(2026, 4, 25, tzinfo=timezone.utc),
            source_agent="polish",
            task_type="sentence_polish",
            content_origin="user_submission",
            sentence_hash="sha256:sample",
            has_grammar_issue=True,
            confidence=0.95,
        )

        self.assertEqual(event.event_type, "grammar_sample_checked")
        self.assertEqual(event.sentence_hash, "sha256:sample")
        self.assertTrue(event.has_grammar_issue)

    def test_event_id_is_deterministic_for_retries(self) -> None:
        context = GrammarEventContext(user_id=123, message_id="msg-1")

        first = build_stable_event_id(
            context=context,
            event_type="grammar_error_detected",
            logical_key="sha256:abc|subject_verb_agreement",
        )
        second = build_stable_event_id(
            context=context,
            event_type="grammar_error_detected",
            logical_key="sha256:abc|subject_verb_agreement",
        )

        self.assertEqual(first, second)
        self.assertTrue(first.startswith("evt_grammar_"))


class GrammarProfileAggregationTest(unittest.TestCase):
    def test_profile_separates_questions_errors_and_style_suggestions(self) -> None:
        now = datetime(2026, 4, 25, tzinfo=timezone.utc)
        profile = build_grammar_profile(
            user_id=1,
            grammar_questions=[
                GrammarQuestionEvent(
                    user_id=1,
                    conversation_id="c-1",
                    message_id="m-1",
                    occurred_at=now,
                    grammar_question_type="relative_clause",
                    topic_label="定语从句",
                    source_text="which 和 that 有什么区别？",
                    confidence=0.92,
                )
            ],
            grammar_errors=[
                GrammarErrorEvent(
                    user_id=1,
                    conversation_id="c-2",
                    message_id="m-2",
                    occurred_at=now,
                    source_agent="polish",
                    task_type="sentence_polish",
                    grammar_error_type="subject_verb_agreement",
                    severity="medium",
                    span="He go",
                    correction="He goes",
                    sentence_hash="sha256:1",
                    confidence=0.95,
                )
            ],
            style_suggestions=[
                StyleSuggestionEvent(
                    user_id=1,
                    conversation_id="c-3",
                    message_id="m-3",
                    occurred_at=now,
                    source_agent="polish",
                    task_type="sentence_polish",
                    style_issue_type="academic_tone",
                    span="a lot of",
                    suggestion="a significant number of",
                    reason="TOEFL 写作中更正式",
                    confidence=0.86,
                )
            ],
            now=now,
        )

        self.assertEqual(profile.user_id, 1)
        self.assertEqual(profile.profile_scope, "30d")
        self.assertEqual(profile.grammar_interest_profile.top_topics[0].type, "relative_clause")
        self.assertEqual(profile.grammar_error_profile.top_errors[0].type, "subject_verb_agreement")
        self.assertEqual(profile.style_profile.top_style_suggestions[0].type, "academic_tone")

    def test_profile_filters_low_confidence_and_dedupes_same_sentence_error(self) -> None:
        now = datetime(2026, 4, 25, tzinfo=timezone.utc)
        duplicate_error = GrammarErrorEvent(
            user_id=1,
            conversation_id="c-2",
            message_id="m-2",
            occurred_at=now,
            source_agent="polish",
            task_type="sentence_polish",
            grammar_error_type="article",
            severity="high",
            span="go to the school",
            correction="go to school",
            sentence_hash="sha256:article-case",
            confidence=0.9,
        )
        low_confidence_error = GrammarErrorEvent(
            user_id=1,
            conversation_id="c-3",
            message_id="m-3",
            occurred_at=now,
            source_agent="polish",
            task_type="sentence_polish",
            grammar_error_type="preposition",
            severity="high",
            span="discuss about",
            correction="discuss",
            sentence_hash="sha256:prep-case",
            confidence=0.69,
        )

        profile = build_grammar_profile(
            user_id=1,
            grammar_questions=[],
            grammar_errors=[duplicate_error, duplicate_error, low_confidence_error],
            style_suggestions=[],
            now=now,
        )

        self.assertEqual(len(profile.grammar_error_profile.top_errors), 1)
        article = profile.grammar_error_profile.top_errors[0]
        self.assertEqual(article.type, "article")
        self.assertEqual(article.count, 1)
        self.assertEqual(article.weighted_score, 3.0)

    def test_profile_marks_repeated_errors_after_multiple_distinct_occurrences(self) -> None:
        now = datetime(2026, 4, 25, tzinfo=timezone.utc)
        errors = [
            GrammarErrorEvent(
                user_id=1,
                conversation_id=f"c-{index}",
                message_id=f"m-{index}",
                occurred_at=now,
                source_agent="polish",
                task_type="sentence_polish",
                grammar_error_type="preposition",
                severity="medium",
                span="depend of",
                correction="depend on",
                sentence_hash=f"sha256:{index}",
                confidence=0.9,
            )
            for index in range(3)
        ]

        profile = build_grammar_profile(
            user_id=1,
            grammar_questions=[],
            grammar_errors=errors,
            style_suggestions=[],
            now=now,
        )

        self.assertIn("preposition", profile.grammar_error_profile.repeated_errors)

    def test_profile_uses_sample_checked_events_for_clean_sentence_rate(self) -> None:
        now = datetime(2026, 4, 25, tzinfo=timezone.utc)
        samples = [
            GrammarSampleCheckedEvent(
                user_id=1,
                conversation_id=f"c-{index}",
                message_id=f"m-{index}",
                occurred_at=now,
                source_agent="polish",
                task_type="sentence_polish",
                content_origin="user_submission",
                sentence_hash=f"sha256:{index}",
                has_grammar_issue=index < 2,
                confidence=0.95,
            )
            for index in range(4)
        ]

        profile = build_grammar_profile(
            user_id=1,
            grammar_questions=[],
            grammar_errors=[],
            style_suggestions=[],
            grammar_samples=samples,
            now=now,
        )

        self.assertEqual(profile.grammar_error_profile.checked_sentence_count, 4)
        self.assertEqual(profile.grammar_error_profile.error_sentence_count, 2)
        self.assertEqual(profile.grammar_error_profile.clean_sentence_rate, 0.5)

    def test_profile_filters_non_eligible_and_non_user_origin_events(self) -> None:
        now = datetime(2026, 4, 25, tzinfo=timezone.utc)
        assistant_draft_error = GrammarErrorEvent(
            user_id=1,
            conversation_id="c-1",
            message_id="m-1",
            occurred_at=now,
            source_agent="polish",
            task_type="sentence_polish",
            content_origin="assistant_draft",
            grammar_error_type="article",
            severity="high",
            span="go to the school",
            correction="go to school",
            sentence_hash="sha256:assistant",
            confidence=0.95,
        )
        ineligible_error = assistant_draft_error.model_copy(
            update={
                "content_origin": "user_submission",
                "profile_eligible": False,
                "sentence_hash": "sha256:ineligible",
            }
        )

        profile = build_grammar_profile(
            user_id=1,
            grammar_questions=[],
            grammar_errors=[assistant_draft_error, ineligible_error],
            style_suggestions=[],
            now=now,
        )

        self.assertEqual(profile.grammar_error_profile.top_errors, [])

    def test_builds_7d_30d_and_all_profiles(self) -> None:
        now = datetime(2026, 4, 25, tzinfo=timezone.utc)
        old_question = GrammarQuestionEvent(
            user_id=1,
            conversation_id="c-old",
            message_id="m-old",
            occurred_at=datetime(2026, 3, 1, tzinfo=timezone.utc),
            grammar_question_type="tense",
            topic_label="时态",
            source_text="过去完成时怎么用？",
            confidence=0.9,
        )
        recent_question = GrammarQuestionEvent(
            user_id=1,
            conversation_id="c-new",
            message_id="m-new",
            occurred_at=now,
            grammar_question_type="relative_clause",
            topic_label="定语从句",
            source_text="which 和 that 有什么区别？",
            confidence=0.9,
        )

        profiles = build_grammar_profiles(
            user_id=1,
            grammar_questions=[old_question, recent_question],
            grammar_errors=[],
            style_suggestions=[],
            now=now,
        )

        self.assertEqual(set(profiles), {"7d", "30d", "all"})
        self.assertEqual([item.type for item in profiles["7d"].grammar_interest_profile.top_topics], ["relative_clause"])
        self.assertEqual(
            {item.type for item in profiles["all"].grammar_interest_profile.top_topics},
            {"relative_clause", "tense"},
        )


if __name__ == "__main__":
    unittest.main()
