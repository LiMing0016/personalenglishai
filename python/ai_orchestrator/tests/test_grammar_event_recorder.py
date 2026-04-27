from __future__ import annotations

import json
import unittest
from datetime import datetime, timezone
from unittest.mock import Mock
from urllib.error import HTTPError

from python.ai_orchestrator.schemas.grammar_profile import GrammarEventContext
from python.ai_orchestrator.schemas.grammar_profile import GrammarSampleCheckedEvent
from python.ai_orchestrator.services.grammar_event_recorder import BackendGrammarEventRecorder


class BackendGrammarEventRecorderTest(unittest.TestCase):
    def test_posts_batch_payload_to_backend(self) -> None:
        opener = Mock(return_value=(202, b'{"code":"0","data":{"acceptedCount":1}}'))
        recorder = BackendGrammarEventRecorder(
            backend_base_url="http://backend.test",
            opener=opener,
        )
        event = GrammarSampleCheckedEvent(
            user_id=123,
            conversation_id="conv-1",
            message_id="msg-1",
            occurred_at=datetime(2026, 4, 25, 10, 5, tzinfo=timezone.utc),
            source_agent="polish",
            task_type="sentence_polish",
            content_origin="user_submission",
            sentence_hash="sha256:abc",
            has_grammar_issue=True,
            confidence=0.95,
        )

        recorder.record_batch(
            context=GrammarEventContext(
                user_id=123,
                conversation_id="conv-1",
                message_id="msg-1",
                study_stage="toefl",
                assistant_mode="writing",
            ),
            events=[event],
            authorization="Bearer token",
        )

        request = opener.call_args.args[0]
        self.assertEqual(request.full_url, "http://backend.test/api/learning-events/grammar/batch")
        self.assertEqual(request.headers["Authorization"], "Bearer token")
        body = json.loads(request.data.decode("utf-8"))
        self.assertEqual(body["userId"], 123)
        self.assertEqual(body["conversationId"], "conv-1")
        self.assertEqual(body["events"][0]["eventType"], "grammar_sample_checked")
        self.assertEqual(body["events"][0]["payload"]["sentenceHash"], "sha256:abc")

    def test_backend_failure_does_not_raise(self) -> None:
        opener = Mock(side_effect=HTTPError("url", 500, "boom", hdrs=None, fp=None))
        recorder = BackendGrammarEventRecorder(
            backend_base_url="http://backend.test",
            opener=opener,
        )

        recorder.record_batch(
            context=GrammarEventContext(user_id=123, conversation_id="conv-1", message_id="msg-1"),
            events=[],
            authorization=None,
        )

        opener.assert_not_called()


if __name__ == "__main__":
    unittest.main()
