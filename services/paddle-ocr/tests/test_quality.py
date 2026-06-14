import unittest

from app.quality import aggregate_document_status, assess_page_quality
from app.schemas import OcrPage, TextBlock


class QualityTest(unittest.TestCase):
    def test_empty_page_is_marked_with_warning(self):
        page = OcrPage(pageNumber=1, text="", blocks=[], formulas=[])

        result = assess_page_quality(page)

        self.assertEqual(result.confidence, 0.0)
        self.assertIn("EMPTY_PAGE", result.warnings)

    def test_low_confidence_page_keeps_blocks_and_warns(self):
        page = OcrPage(
            pageNumber=1,
            text="weak text",
            blocks=[
                TextBlock(text="weak", bbox=[[0, 0], [10, 0], [10, 10], [0, 10]], confidence=0.42, order=1),
                TextBlock(text="text", bbox=[[0, 12], [10, 12], [10, 20], [0, 20]], confidence=0.52, order=2),
            ],
            formulas=[],
        )

        result = assess_page_quality(page)

        self.assertLess(result.confidence, 0.6)
        self.assertIn("LOW_CONFIDENCE", result.warnings)

    def test_document_status_supports_partial_success(self):
        pages = [
            OcrPage(pageNumber=1, text="ok", blocks=[], formulas=[], confidence=0.9),
            OcrPage(pageNumber=2, text="", blocks=[], formulas=[], confidence=0.0, warnings=["EMPTY_PAGE"]),
        ]

        status = aggregate_document_status(pages)

        self.assertEqual(status.status, "PARTIAL")
        self.assertEqual(status.pageCount, 2)
        self.assertEqual(status.recognizedPageCount, 1)
        self.assertIn("EMPTY_PAGE", status.warnings)


if __name__ == "__main__":
    unittest.main()
