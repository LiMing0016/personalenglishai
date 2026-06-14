import unittest

from app.ocr_engine import merge_blocks_text, normalize_paddle_ocr_result, sort_blocks_reading_order


class OcrEngineTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
