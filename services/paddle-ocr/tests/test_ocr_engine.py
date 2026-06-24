import unittest
from unittest.mock import patch

from app.ocr_engine import (
    build_paddle_ocr_kwargs,
    merge_blocks_text,
    normalize_paddle_ocr_result,
    predict_ocr,
    sort_blocks_reading_order,
)


class ArrayLike:
    def __init__(self, values):
        self.values = values

    def tolist(self):
        return self.values


class RecordingPredictor:
    def __init__(self):
        self.kwargs = None

    def predict(self, image_path, **kwargs):
        self.kwargs = kwargs
        return {
            "dt_polys": [
                [[0, 0], [20, 0], [20, 10], [0, 10]],
            ],
            "rec_texts": ["hello"],
            "rec_scores": [0.9],
        }


class FakePaddleOcrV3:
    def __init__(
        self,
        lang=None,
        use_doc_orientation_classify=None,
        use_doc_unwarping=None,
        use_textline_orientation=None,
        cpu_threads=None,
    ):
        pass


class OcrEngineTest(unittest.TestCase):
    def test_build_kwargs_passes_configured_cpu_threads(self):
        with patch.dict("os.environ", {"PADDLE_OCR_CPU_THREADS": "4"}):
            kwargs = build_paddle_ocr_kwargs(FakePaddleOcrV3, "ch")

        self.assertEqual(kwargs["cpu_threads"], 4)

    def test_normalizes_common_paddle_result_shape(self):
        raw_result = [
            [
                [[[20, 30], [80, 30], [80, 50], [20, 50]], ("world", 0.91)],
                [[[10, 10], [70, 10], [70, 25], [10, 25]], ("hello", 0.98)],
            ]
        ]

        blocks = normalize_paddle_ocr_result(raw_result)

        self.assertEqual([block.text for block in blocks], ["hello", "world"])
        self.assertEqual(blocks[0].order, 1)
        self.assertAlmostEqual(blocks[0].confidence, 0.98)

    def test_sorts_blocks_top_to_bottom_then_left_to_right(self):
        blocks = normalize_paddle_ocr_result(
            [
                [
                    [[[80, 10], [120, 10], [120, 30], [80, 30]], ("right", 0.9)],
                    [[[10, 10], [60, 10], [60, 30], [10, 30]], ("left", 0.9)],
                    [[[10, 60], [60, 60], [60, 80], [10, 80]], ("next", 0.9)],
                ]
            ]
        )

        sorted_blocks = sort_blocks_reading_order(blocks)

        self.assertEqual([block.text for block in sorted_blocks], ["left", "right", "next"])

    def test_normalizes_paddle_3_result_shape_with_array_boxes(self):
        raw_result = [
            {
                "dt_polys": [
                    ArrayLike([[40, 60], [160, 60], [160, 90], [40, 90]]),
                    ArrayLike([[40, 110], [220, 110], [220, 140], [40, 140]]),
                ],
                "rec_texts": ["Hello OCR", "Personal English AI"],
                "rec_scores": [0.99, 0.97],
            }
        ]

        blocks = normalize_paddle_ocr_result(raw_result)

        self.assertEqual([block.text for block in blocks], ["Hello OCR", "Personal English AI"])
        self.assertEqual(blocks[0].bbox[0], [40.0, 60.0])
        self.assertAlmostEqual(blocks[1].confidence, 0.97)

    def test_merges_same_line_and_preserves_paragraph_breaks(self):
        blocks = normalize_paddle_ocr_result(
            [
                [
                    [[[10, 10], [60, 10], [60, 30], [10, 30]], ("Hello", 0.9)],
                    [[[70, 11], [130, 11], [130, 30], [70, 30]], ("world", 0.9)],
                    [[[10, 80], [90, 80], [90, 100], [10, 100]], ("Next line", 0.9)],
                ]
            ]
        )

        text = merge_blocks_text(blocks)

        self.assertEqual(text, "Hello world\nNext line")

    def test_predict_ocr_does_not_request_uninitialized_doc_unwarping(self):
        predictor = RecordingPredictor()

        predict_ocr(predictor, "fake-page.png", enable_orientation=True, enable_unwarping=True)

        self.assertIn("use_textline_orientation", predictor.kwargs)
        self.assertNotIn("use_doc_unwarping", predictor.kwargs)


if __name__ == "__main__":
    unittest.main()
