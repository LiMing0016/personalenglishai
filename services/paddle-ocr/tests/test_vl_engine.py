import unittest
from unittest.mock import patch

from app.schemas import OcrAsset, OcrPage
from app.vl_engine import _apply_page_number_offset, build_paddle_vl_kwargs


class FakePaddleVl:
    def __init__(self, pipeline_version=None, device=None):
        pass


class PaddleVlDocumentEngineTest(unittest.TestCase):
    def test_vl_kwargs_pass_configured_device(self):
        with patch.dict("os.environ", {"PADDLE_OCR_DEVICE": "gpu:0"}):
            kwargs = build_paddle_vl_kwargs(FakePaddleVl)

        self.assertEqual(kwargs["device"], "gpu:0")

    def test_apply_page_number_offset_preserves_original_pdf_page_numbers(self):
        result = {
            "pages": [
                OcrPage(pageNumber=1, text="page eleven"),
                OcrPage(pageNumber=2, text="page twelve"),
            ],
            "assets": [
                OcrAsset(
                    id="p1-vl-a1",
                    assetType="image",
                    pageNumber=1,
                    dataBase64="ZmFrZQ==",
                    width=10,
                    height=10,
                    order=1,
                )
            ],
        }

        _apply_page_number_offset(result, 11)

        self.assertEqual([page.pageNumber for page in result["pages"]], [11, 12])
        self.assertEqual(result["assets"][0].pageNumber, 11)
        self.assertEqual(result["assets"][0].id, "p11-vl-a1")


if __name__ == "__main__":
    unittest.main()
