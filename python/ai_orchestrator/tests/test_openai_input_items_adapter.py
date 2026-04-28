import base64
import unittest

from python.ai_orchestrator.adapters.openai_input_items import build_input_items


class OpenAIInputItemsAdapterTest(unittest.TestCase):
    def test_maps_text_images_and_files_into_responses_input_items(self) -> None:
        items = build_input_items(
            "帮我评价这张图和这个 PDF",
            [
                {
                    "filename": "draft.png",
                    "content_type": "image/png",
                    "content": b"fake-image",
                },
                {
                    "filename": "rubric.pdf",
                    "content_type": "application/pdf",
                    "content": b"fake-pdf",
                },
            ],
        )

        self.assertEqual(items[0]["role"], "user")
        content = items[0]["content"]
        self.assertEqual(content[0], {"type": "input_text", "text": "帮我评价这张图和这个 PDF"})
        self.assertEqual(content[1]["type"], "input_image")
        self.assertTrue(content[1]["image_url"].startswith("data:image/png;base64,"))
        self.assertEqual(
            content[2],
            {
                "type": "input_file",
                "filename": "rubric.pdf",
                "file_data": base64.b64encode(b"fake-pdf").decode("ascii"),
            },
        )


if __name__ == "__main__":
    unittest.main()
