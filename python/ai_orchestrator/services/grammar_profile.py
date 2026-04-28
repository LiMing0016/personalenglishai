from __future__ import annotations

import hashlib
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from typing import Iterable

from python.ai_orchestrator.schemas.grammar_profile import (
    GrammarEventContext,
    GrammarErrorEvent,
    GrammarErrorProfile,
    GrammarErrorProfileItem,
    GrammarInterestProfile,
    GrammarQuestionEvent,
    GrammarSampleCheckedEvent,
    GrammarTopicProfileItem,
    ProfileScope,
    StyleProfile,
    StyleSuggestionEvent,
    StyleSuggestionProfileItem,
    UserGrammarProfile,
)

CONFIDENCE_THRESHOLD = 0.7
REPEATED_ERROR_THRESHOLD = 3
PROFILE_SCOPES: tuple[ProfileScope, ...] = ("7d", "30d", "all")

_SEVERITY_WEIGHT = {"low": 1.0, "medium": 2.0, "high": 3.0}
_PROFILE_ORIGINS = {"user_input", "user_submission"}
_SCOPE_DAYS = {"7d": 7, "30d": 30}


def build_stable_event_id(*, context: GrammarEventContext, event_type: str, logical_key: str) -> str:
    raw = f"{context.user_id}|{context.message_id}|{event_type}|{logical_key}"
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    return f"evt_grammar_{digest}"


def _in_scope(occurred_at: datetime, *, now: datetime, profile_scope: ProfileScope) -> bool:
    if profile_scope == "all":
        return True
    return occurred_at >= now - timedelta(days=_SCOPE_DAYS[profile_scope])


def _confidence_weight(confidence: float) -> float:
    if confidence >= 0.9:
        return 1.0
    if confidence >= 0.8:
        return 0.9
    return 0.8


def _eligible_events(events: Iterable, *, user_id: int, now: datetime, profile_scope: ProfileScope) -> list:
    return [
        event
        for event in events
        if event.user_id == user_id
        and event.profile_eligible
        and event.content_origin in _PROFILE_ORIGINS
        and event.confidence >= CONFIDENCE_THRESHOLD
        and _in_scope(event.occurred_at, now=now, profile_scope=profile_scope)
    ]


def _build_interest_profile(events: list[GrammarQuestionEvent]) -> GrammarInterestProfile:
    counts: Counter[str] = Counter()
    labels: dict[str, str] = {}
    last_seen: dict[str, datetime] = {}

    for event in events:
        topic = event.grammar_question_type
        counts[topic] += 1
        labels[topic] = event.topic_label
        last_seen[topic] = max(last_seen.get(topic, event.occurred_at), event.occurred_at)

    items = [
        GrammarTopicProfileItem(
            type=topic,
            label=labels[topic],
            count=count,
            last_seen=last_seen[topic],
        )
        for topic, count in counts.items()
    ]
    items.sort(key=lambda item: (-item.count, item.type))
    return GrammarInterestProfile(top_topics=items)


def _dedupe_errors(events: list[GrammarErrorEvent]) -> list[GrammarErrorEvent]:
    seen: set[tuple[int, str, str, str]] = set()
    deduped: list[GrammarErrorEvent] = []

    for event in events:
        sentence_key = event.sentence_hash or event.span
        key = (event.user_id, event.message_id, sentence_key, event.grammar_error_type)
        if key in seen:
            continue
        seen.add(key)
        deduped.append(event)

    return deduped


def _build_error_profile(
    events: list[GrammarErrorEvent],
    samples: list[GrammarSampleCheckedEvent],
) -> GrammarErrorProfile:
    deduped = _dedupe_errors(events)
    counts: Counter[str] = Counter()
    weighted_scores: defaultdict[str, float] = defaultdict(float)
    last_seen: dict[str, datetime] = {}

    for event in deduped:
        error_type = event.grammar_error_type
        counts[error_type] += 1
        weighted_scores[error_type] += _SEVERITY_WEIGHT[event.severity] * _confidence_weight(event.confidence)
        last_seen[error_type] = max(last_seen.get(error_type, event.occurred_at), event.occurred_at)

    items = [
        GrammarErrorProfileItem(
            type=error_type,
            count=count,
            weighted_score=round(weighted_scores[error_type], 2),
            last_seen=last_seen[error_type],
        )
        for error_type, count in counts.items()
    ]
    items.sort(key=lambda item: (-item.weighted_score, item.type))
    repeated_errors = sorted(error_type for error_type, count in counts.items() if count >= REPEATED_ERROR_THRESHOLD)
    deduped_samples = _dedupe_samples(samples)
    checked_sentence_count = len(deduped_samples)
    error_sentence_count = sum(1 for sample in deduped_samples if sample.has_grammar_issue)
    clean_sentence_rate = None
    if checked_sentence_count > 0:
        clean_sentence_rate = round((checked_sentence_count - error_sentence_count) / checked_sentence_count, 4)
    return GrammarErrorProfile(
        top_errors=items,
        repeated_errors=repeated_errors,
        checked_sentence_count=checked_sentence_count,
        error_sentence_count=error_sentence_count,
        clean_sentence_rate=clean_sentence_rate,
    )


def _dedupe_samples(events: list[GrammarSampleCheckedEvent]) -> list[GrammarSampleCheckedEvent]:
    seen: set[tuple[int, str, str]] = set()
    deduped: list[GrammarSampleCheckedEvent] = []

    for event in events:
        key = (event.user_id, event.message_id, event.sentence_hash)
        if key in seen:
            continue
        seen.add(key)
        deduped.append(event)

    return deduped


def _build_style_profile(events: list[StyleSuggestionEvent]) -> StyleProfile:
    counts: Counter[str] = Counter()
    last_seen: dict[str, datetime] = {}

    for event in events:
        issue_type = event.style_issue_type
        counts[issue_type] += 1
        last_seen[issue_type] = max(last_seen.get(issue_type, event.occurred_at), event.occurred_at)

    items = [
        StyleSuggestionProfileItem(
            type=issue_type,
            count=count,
            last_seen=last_seen[issue_type],
        )
        for issue_type, count in counts.items()
    ]
    items.sort(key=lambda item: (-item.count, item.type))
    return StyleProfile(top_style_suggestions=items)


def _source_event_stats(*event_groups: list) -> tuple[int, datetime | None]:
    events = [event for group in event_groups for event in group]
    if not events:
        return 0, None
    return len(events), max(event.occurred_at for event in events)


def build_grammar_profile(
    *,
    user_id: int,
    grammar_questions: Iterable[GrammarQuestionEvent],
    grammar_errors: Iterable[GrammarErrorEvent],
    style_suggestions: Iterable[StyleSuggestionEvent],
    grammar_samples: Iterable[GrammarSampleCheckedEvent] = (),
    now: datetime,
    profile_scope: ProfileScope = "30d",
) -> UserGrammarProfile:
    eligible_questions = _eligible_events(grammar_questions, user_id=user_id, now=now, profile_scope=profile_scope)
    eligible_errors = _eligible_events(grammar_errors, user_id=user_id, now=now, profile_scope=profile_scope)
    eligible_styles = _eligible_events(style_suggestions, user_id=user_id, now=now, profile_scope=profile_scope)
    eligible_samples = _eligible_events(grammar_samples, user_id=user_id, now=now, profile_scope=profile_scope)
    source_event_count, source_max_occurred_at = _source_event_stats(
        eligible_questions,
        eligible_errors,
        eligible_styles,
        eligible_samples,
    )
    return UserGrammarProfile(
        user_id=user_id,
        profile_scope=profile_scope,
        source_event_count=source_event_count,
        source_max_occurred_at=source_max_occurred_at,
        grammar_interest_profile=_build_interest_profile(eligible_questions),
        grammar_error_profile=_build_error_profile(eligible_errors, eligible_samples),
        style_profile=_build_style_profile(eligible_styles),
    )


def build_grammar_profiles(
    *,
    user_id: int,
    grammar_questions: Iterable[GrammarQuestionEvent],
    grammar_errors: Iterable[GrammarErrorEvent],
    style_suggestions: Iterable[StyleSuggestionEvent],
    grammar_samples: Iterable[GrammarSampleCheckedEvent] = (),
    now: datetime,
) -> dict[ProfileScope, UserGrammarProfile]:
    question_events = list(grammar_questions)
    error_events = list(grammar_errors)
    style_events = list(style_suggestions)
    sample_events = list(grammar_samples)
    return {
        scope: build_grammar_profile(
            user_id=user_id,
            grammar_questions=question_events,
            grammar_errors=error_events,
            style_suggestions=style_events,
            grammar_samples=sample_events,
            now=now,
            profile_scope=scope,
        )
        for scope in PROFILE_SCOPES
    }
