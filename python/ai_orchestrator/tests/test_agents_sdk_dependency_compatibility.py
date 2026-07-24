import unittest


class AgentsSdkDependencyCompatibilityTest(unittest.TestCase):
    def test_run_context_wrapper_can_initialize_with_pinned_openai_sdk(self) -> None:
        from agents.run_context import RunContextWrapper

        wrapper = RunContextWrapper(context=None)

        self.assertIsNone(wrapper.context)


if __name__ == "__main__":
    unittest.main()
