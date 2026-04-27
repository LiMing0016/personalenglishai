import unittest
from unittest.mock import patch

from python.ai_orchestrator.env_loader import default_env_paths, load_orchestrator_env, normalize_openai_base_url


class EnvLoaderTest(unittest.TestCase):
    def test_loads_repo_and_backend_env_without_overriding_existing_values(self) -> None:
        with patch("python.ai_orchestrator.env_loader.load_dotenv") as load_dotenv:
            load_orchestrator_env()

        self.assertEqual(
            [call.kwargs for call in load_dotenv.call_args_list],
            [{"dotenv_path": path, "override": False} for path in default_env_paths()],
        )

    def test_normalizes_openai_base_url_for_python_sdk(self) -> None:
        with patch.dict("os.environ", {"OPENAI_BASE_URL": "https://api.openai.com"}, clear=True):
            normalize_openai_base_url()

            import os

            self.assertEqual(os.getenv("OPENAI_BASE_URL"), "https://api.openai.com/v1")

    def test_keeps_versioned_openai_base_url(self) -> None:
        with patch.dict("os.environ", {"OPENAI_BASE_URL": "https://api.openai.com/v1"}, clear=True):
            normalize_openai_base_url()

            import os

            self.assertEqual(os.getenv("OPENAI_BASE_URL"), "https://api.openai.com/v1")


if __name__ == "__main__":
    unittest.main()
