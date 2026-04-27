from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import Callable, Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from ..schemas.grammar_profile import GrammarEventBase
from ..schemas.grammar_profile import GrammarEventContext
from .grammar_profile import build_stable_event_id

log = logging.getLogger("uvicorn.error")


def _camelize(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


def _json_default(value):
    if isinstance(value, datetime):
        return value.isoformat().replace("+00:00", "Z")
    raise TypeError(f"Object of type {type(value).__name__} is not JSON serializable")


_TOP_LEVEL_FIELDS = {
    "event_type",
    "occurred_at",
    "study_stage",
    "assistant_mode",
    "source_agent",
    "task_type",
    "content_origin",
    "profile_eligible",
    "confidence",
    "schema_version",
    "skill_version",
    "taxonomy_version",
    "prompt_version",
    "model_version",
    "grammar_question_type",
    "grammar_error_type",
    "style_issue_type",
    "severity",
    "sentence_hash",
}

_PAYLOAD_EXCLUDED_FIELDS = {
    "user_id",
    "conversation_id",
    "message_id",
    "event_type",
    "occurred_at",
    "study_stage",
    "assistant_mode",
    "source_agent",
    "task_type",
    "content_origin",
    "profile_eligible",
    "confidence",
    "schema_version",
    "skill_version",
    "taxonomy_version",
    "prompt_version",
    "model_version",
}


def _logical_key(event: GrammarEventBase) -> str:
    data = event.model_dump(exclude_none=True)
    event_type = data.get("event_type")
    if event_type == "grammar_sample_checked":
        return str(data["sentence_hash"])
    if event_type == "grammar_question_asked":
        return str(data["grammar_question_type"])
    if event_type == "grammar_error_detected":
        return f"{data.get('sentence_hash', '')}|{data['grammar_error_type']}"
    if event_type == "style_suggestion_detected":
        return f"{data.get('sentence_hash', '')}|{data['style_issue_type']}|{data['span']}"
    return str(event_type)


def _event_payload(*, context: GrammarEventContext, event: GrammarEventBase) -> dict[str, object]:
    data = event.model_dump(exclude_none=True)
    item = {
        "eventId": build_stable_event_id(
            context=context,
            event_type=str(data["event_type"]),
            logical_key=_logical_key(event),
        )
    }
    for key in _TOP_LEVEL_FIELDS:
        if key in data:
            item[_camelize(key)] = data[key]
    item["payload"] = {
        _camelize(key): value
        for key, value in data.items()
        if key not in _PAYLOAD_EXCLUDED_FIELDS
    }
    return item


class BackendGrammarEventRecorder:
    def __init__(
        self,
        *,
        backend_base_url: str,
        opener: Callable[[Request], object] | None = None,
    ) -> None:
        self._backend_base_url = backend_base_url.rstrip("/")
        self._opener = opener or urlopen

    def record_batch(
        self,
        *,
        context: GrammarEventContext,
        events: Iterable[GrammarEventBase],
        authorization: str | None,
    ) -> None:
        event_list = list(events)
        if not event_list:
            return

        body = {
            "userId": context.user_id,
            "conversationId": context.conversation_id,
            "messageId": context.message_id,
            "events": [_event_payload(context=context, event=event) for event in event_list],
        }
        raw_body = json.dumps(body, ensure_ascii=False, default=_json_default).encode("utf-8")
        request = Request(
            f"{self._backend_base_url}/api/learning-events/grammar/batch",
            data=raw_body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                **({"Authorization": authorization} if authorization else {}),
            },
        )

        try:
            self._opener(request)
        except (HTTPError, URLError, OSError) as exc:
            log.warning(
                "[GRAMMAR_EVENT_RECORD_FAILED] user_id=%s conversation_id=%s events=%s error=%s",
                context.user_id,
                context.conversation_id or "",
                len(event_list),
                exc,
            )
