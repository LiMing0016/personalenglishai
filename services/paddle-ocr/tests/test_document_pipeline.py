import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.document_pipeline import DocumentPipelineEngine, build_ppstructure_kwargs


class LazyDocumentPipelineEngine(DocumentPipelineEngine):
    def __init__(self):
        self.load_count = 0
        super().__init__()

    def _load_sdk(self) -> None:
        self.load_count += 1
        self.sdk_loaded = True
        self.version = "test"
        self.unavailable_reason = None
        self._pipeline = FakePipeline()


class FakePipeline:
    def predict(self, image_path, **kwargs):
        return {
            "parsing_res_list": [
                {
                    "block_label": "text",
                    "block_content": "hello structure",
                    "block_order": 1,
                    "block_bbox": [0, 0, 20, 10],
                    "confidence": 0.9,
                }
            ]
        }


class FakePPStructureV3:
    def __init__(
        self,
        lang=None,
        use_doc_orientation_classify=None,
        use_doc_unwarping=None,
        use_textline_orientation=None,
        use_table_recognition=None,
        use_formula_recognition=None,
        use_region_detection=None,
        format_block_content=None,
        cpu_threads=None,
    ):
        pass

    def predict(self, image_path, **kwargs):
        return {}


class DocumentPipelineEngineTest(unittest.TestCase):
    def test_ppstructure_kwargs_initialize_high_quality_models_without_formula_by_default(self):
        kwargs = build_ppstructure_kwargs(FakePPStructureV3, "ch")

        self.assertEqual(kwargs["lang"], "ch")
        self.assertTrue(kwargs["use_doc_orientation_classify"])
        self.assertTrue(kwargs["use_doc_unwarping"])
        self.assertTrue(kwargs["use_textline_orientation"])
        self.assertTrue(kwargs["use_table_recognition"])
        self.assertFalse(kwargs["use_formula_recognition"])
        self.assertTrue(kwargs["use_region_detection"])
        self.assertTrue(kwargs["format_block_content"])

    def test_ppstructure_kwargs_pass_configured_cpu_threads(self):
        with patch.dict("os.environ", {"PADDLE_OCR_CPU_THREADS": "4"}):
            kwargs = build_ppstructure_kwargs(FakePPStructureV3, "ch")

        self.assertEqual(kwargs["cpu_threads"], 4)

    def test_document_pipeline_loads_lazily_on_first_recognition(self):
        engine = LazyDocumentPipelineEngine()

        self.assertEqual(engine.load_count, 0)
        self.assertFalse(engine.sdk_loaded)
        self.assertEqual(engine.unavailable_reason, "PPStructureV3 has not been loaded yet")

        result = engine.recognize_image("fake-page.png")

        self.assertEqual(engine.load_count, 1)
        self.assertTrue(engine.sdk_loaded)
        self.assertEqual(result["elements"][0].text, "hello structure")

    def test_successful_sdk_load_clears_pending_message(self):
        fake_module = SimpleNamespace(PPStructureV3=FakePPStructureV3, __version__="test")

        with patch("app.document_pipeline.importlib.import_module", return_value=fake_module):
            engine = DocumentPipelineEngine(load_on_init=True)

        self.assertTrue(engine.sdk_loaded)
        self.assertIsNone(engine.unavailable_reason)
        self.assertEqual(engine.version, "test")


if __name__ == "__main__":
    unittest.main()
