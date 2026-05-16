import unittest
from unittest.mock import Mock, patch

from python.ai_orchestrator import observability


class ObservabilityTest(unittest.TestCase):
    def setUp(self) -> None:
        observability._langfuse_configured = False

    def tearDown(self) -> None:
        observability._langfuse_configured = False

    def test_langfuse_tracing_is_disabled_by_default(self) -> None:
        with (
            patch.dict("os.environ", {}, clear=True),
            patch("python.ai_orchestrator.observability._load_langfuse_dependencies") as load_dependencies,
        ):
            status = observability.configure_langfuse_tracing()

        self.assertFalse(status.enabled)
        self.assertFalse(status.configured)
        load_dependencies.assert_not_called()

    def test_langfuse_tracing_requires_complete_credentials(self) -> None:
        with (
            patch.dict("os.environ", {"LANGFUSE_ENABLED": "true", "LANGFUSE_PUBLIC_KEY": "pk"}, clear=True),
            patch("python.ai_orchestrator.observability._load_langfuse_dependencies") as load_dependencies,
        ):
            status = observability.configure_langfuse_tracing()

        self.assertTrue(status.enabled)
        self.assertFalse(status.configured)
        self.assertIn("LANGFUSE_SECRET_KEY", status.reason)
        self.assertIn("LANGFUSE_BASE_URL", status.reason)
        load_dependencies.assert_not_called()

    def test_langfuse_tracing_instruments_openai_agents_when_configured(self) -> None:
        instrumentor = Mock()
        instrumentor_cls = Mock(return_value=instrumentor)
        get_client = Mock()

        with (
            patch.dict(
                "os.environ",
                {
                    "LANGFUSE_ENABLED": "true",
                    "LANGFUSE_PUBLIC_KEY": "pk",
                    "LANGFUSE_SECRET_KEY": "sk",
                    "LANGFUSE_HOST": "https://cloud.langfuse.com",
                },
                clear=True,
            ),
            patch(
                "python.ai_orchestrator.observability._load_langfuse_dependencies",
                return_value=(instrumentor_cls, get_client),
            ),
        ):
            status = observability.configure_langfuse_tracing()
            self.assertEqual(observability.os.getenv("LANGFUSE_BASE_URL"), "https://cloud.langfuse.com")

        self.assertTrue(status.enabled)
        self.assertTrue(status.configured)
        instrumentor_cls.assert_called_once_with()
        instrumentor.instrument.assert_called_once_with()

    def test_langfuse_tracing_is_idempotent(self) -> None:
        instrumentor = Mock()
        instrumentor_cls = Mock(return_value=instrumentor)
        get_client = Mock()

        with (
            patch.dict(
                "os.environ",
                {
                    "LANGFUSE_ENABLED": "1",
                    "LANGFUSE_PUBLIC_KEY": "pk",
                    "LANGFUSE_SECRET_KEY": "sk",
                    "LANGFUSE_BASE_URL": "https://cloud.langfuse.com",
                },
                clear=True,
            ),
            patch(
                "python.ai_orchestrator.observability._load_langfuse_dependencies",
                return_value=(instrumentor_cls, get_client),
            ),
        ):
            first = observability.configure_langfuse_tracing()
            second = observability.configure_langfuse_tracing()

        self.assertTrue(first.configured)
        self.assertTrue(second.configured)
        self.assertEqual(second.reason, "already_configured")
        instrumentor.instrument.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
