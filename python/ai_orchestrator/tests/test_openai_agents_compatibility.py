import unittest

from agents.usage import Usage


class OpenAIAgentsCompatibilityTest(unittest.TestCase):
    def test_default_usage_supports_current_openai_token_details_schema(self) -> None:
        usage = Usage()

        self.assertEqual(usage.input_tokens_details.cached_tokens, 0)
        self.assertEqual(usage.input_tokens_details.cache_write_tokens, 0)


if __name__ == "__main__":
    unittest.main()
