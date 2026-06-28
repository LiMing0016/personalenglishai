import base64
import unittest

from fastapi.testclient import TestClient

from app.main import create_app
from app.schemas import ElementBlock, OcrPage, OcrResponse, TextBlock


class FakeEngine:
    sdk_loaded = True
    provider = "PaddleOCR"
    version = "test"
    unavailable_reason = None

    def recognize_image(self, image_path, language, **kwargs):
        return [
            TextBlock(
                text="hello",
                bbox=[[0, 0], [20, 0], [20, 10], [0, 10]],
                confidence=0.99,
                order=1,
            )
        ]


class FakeRenderer:
    def __init__(self):
        self.last_render_args = None

    def render_pdf(self, document_bytes, page_start=None, page_end=None, max_pages=20, dpi=220):
        self.last_render_args = {
            "page_start": page_start,
            "page_end": page_end,
            "max_pages": max_pages,
            "dpi": dpi,
        }
        return [
            {
                "pageNumber": 1,
                "path": "fake-page.png",
                "width": 100,
                "height": 200,
                "warnings": [],
            }
        ]

    def validate_image(self, image_bytes):
        return {"path": "fake-image.png", "width": 80, "height": 60, "warnings": []}


class FakeDocumentEngine:
    sdk_loaded = True
    provider = "PaddleOCR-PPStructureV3"
    version = "test-structure"
    unavailable_reason = None

    def __init__(self):
        self.last_request = None

    def recognize_image(self, image_path, **kwargs):
        self.last_request = {"image_path": image_path, **kwargs}
        return {
            "elements": [
                ElementBlock(
                    type="heading",
                    text="Computer Networking",
                    bbox=[[10, 10], [200, 10], [200, 40], [10, 40]],
                    confidence=0.96,
                    order=1,
                    source="paddle_ppstructure",
                    rawType="title",
                ),
                ElementBlock(
                    type="table",
                    text="| Layer | Purpose |",
                    bbox=[[10, 50], [220, 50], [220, 120], [10, 120]],
                    confidence=0.91,
                    order=2,
                    source="paddle_ppstructure",
                    rawType="table",
                    metadata={"html": "<table><tr><td>Layer</td><td>Purpose</td></tr></table>"},
                ),
                ElementBlock(
                    type="formula",
                    text="E = mc^2",
                    bbox=[[10, 130], [180, 130], [180, 160], [10, 160]],
                    confidence=0.88,
                    order=3,
                    source="paddle_ppstructure",
                    rawType="formula",
                ),
            ],
            "warnings": ["STRUCTURE_TEST_WARNING"],
            "raw": {"pipeline": "fake"},
        }


class FakeLazyDocumentEngine(FakeDocumentEngine):
    sdk_loaded = False
    unavailable_reason = "PPStructureV3 has not been loaded yet"

    def recognize_image(self, image_path, **kwargs):
        self.sdk_loaded = True
        self.unavailable_reason = None
        return super().recognize_image(image_path, **kwargs)


class UnavailableDocumentEngine:
    sdk_loaded = False
    provider = "PaddleOCR-PPStructureV3"
    version = None
    unavailable_reason = "missing PPStructureV3 dependency"

    def recognize_image(self, image_path, **kwargs):
        raise RuntimeError(self.unavailable_reason)


class ApiContractTest(unittest.TestCase):
    def setUp(self):
        self.renderer = FakeRenderer()
        self.client = TestClient(create_app(text_engine=FakeEngine(), renderer=self.renderer))

    def test_health_reports_provider_and_sdk_status(self):
        client = TestClient(create_app(
            text_engine=FakeEngine(),
            renderer=self.renderer,
            document_engine=FakeDocumentEngine(),
        ))

        response = client.get("/health")

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["provider"], "PaddleOCR")
        self.assertTrue(body["sdkLoaded"])
        self.assertTrue(body["documentEngineLoaded"])
        self.assertEqual(body["documentEngineProvider"], "PaddleOCR-PPStructureV3")
        self.assertEqual(body["documentEngineVersion"], "test-structure")
        self.assertIsNone(body["documentEngineMessage"])

    def test_health_allows_lazy_document_engine_before_first_high_quality_request(self):
        response = self.client.get("/health")

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "UP")
        self.assertTrue(body["sdkLoaded"])
        self.assertFalse(body["documentEngineLoaded"])
        self.assertEqual(body["documentEngineMessage"], "PPStructureV3 has not been loaded yet")

    def test_pdf_ocr_returns_stable_response_shape(self):
        pdf_base64 = base64.b64encode(b"%PDF-1.4 fake").decode("ascii")

        response = self.client.post("/ocr/pdf", json={"documentBase64": pdf_base64, "language": "ch,eng"})

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "SUCCEEDED")
        self.assertEqual(body["provider"], "PaddleOCR")
        self.assertEqual(body["pageCount"], 1)
        self.assertEqual(body["recognizedPageCount"], 1)
        self.assertEqual(body["pages"][0]["pageNumber"], 1)
        self.assertEqual(body["pages"][0]["blocks"][0]["text"], "hello")
        self.assertEqual(body["pages"][0]["elements"][0]["type"], "paragraph")
        self.assertEqual(body["pages"][0]["elements"][0]["text"], "hello")
        self.assertEqual(body["pages"][0]["elements"][0]["source"], "paddle_ocr")
        self.assertEqual(body["pages"][0]["elements"][0]["rawType"], "text")

    def test_pdf_ocr_accepts_high_quality_options_and_reports_degraded_capabilities(self):
        client = TestClient(create_app(
            text_engine=FakeEngine(),
            renderer=self.renderer,
            document_engine=UnavailableDocumentEngine(),
        ))
        pdf_base64 = base64.b64encode(b"%PDF-1.4 fake").decode("ascii")

        response = client.post(
            "/ocr/pdf",
            json={
                "documentBase64": pdf_base64,
                "language": "ch,eng",
                "parseMode": "high_quality",
                "maxPages": 3,
                "dpi": 300,
                "enableLayout": True,
                "enableTable": True,
                "enableFormula": False,
                "enableOrientation": True,
                "enableUnwarping": True,
            },
        )

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(self.renderer.last_render_args["max_pages"], 3)
        self.assertEqual(self.renderer.last_render_args["dpi"], 300)
        self.assertEqual(body["metadata"]["parseMode"], "high_quality")
        self.assertTrue(any(item.startswith("LAYOUT_ENGINE_FAILED:") for item in body["pages"][0]["warnings"]))
        self.assertTrue(any(item.startswith("TABLE_ENGINE_FAILED:") for item in body["pages"][0]["warnings"]))

    def test_pdf_high_quality_triggers_lazy_document_pipeline(self):
        document_engine = FakeLazyDocumentEngine()
        client = TestClient(create_app(text_engine=FakeEngine(), renderer=self.renderer, document_engine=document_engine))
        pdf_base64 = base64.b64encode(b"%PDF-1.4 fake").decode("ascii")

        response = client.post(
            "/ocr/pdf",
            json={
                "documentBase64": pdf_base64,
                "parseMode": "high_quality",
                "enableTextOcr": False,
                "enableLayout": True,
                "enableTable": True,
                "enableFormula": True,
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertTrue(document_engine.sdk_loaded)
        body = response.json()
        self.assertEqual(body["pages"][0]["layoutStatus"], "SUCCEEDED")
        self.assertEqual(body["pages"][0]["elements"][0]["source"], "paddle_ppstructure")

    def test_pdf_high_quality_uses_document_pipeline_elements_when_available(self):
        document_engine = FakeDocumentEngine()
        client = TestClient(create_app(text_engine=FakeEngine(), renderer=self.renderer, document_engine=document_engine))
        pdf_base64 = base64.b64encode(b"%PDF-1.4 fake").decode("ascii")

        response = client.post(
            "/ocr/pdf",
            json={
                "documentBase64": pdf_base64,
                "parseMode": "high_quality",
                "enableTextOcr": False,
                "enableLayout": True,
                "enableTable": True,
                "enableFormula": True,
                "enableOrientation": True,
                "enableUnwarping": True,
            },
        )

        self.assertEqual(response.status_code, 200)
        body = response.json()
        page = body["pages"][0]
        self.assertEqual(document_engine.last_request["image_path"], "fake-page.png")
        self.assertTrue(document_engine.last_request["enable_layout"])
        self.assertTrue(document_engine.last_request["enable_table"])
        self.assertTrue(document_engine.last_request["enable_formula"])
        self.assertTrue(document_engine.last_request["enable_orientation"])
        self.assertTrue(document_engine.last_request["enable_unwarping"])
        self.assertEqual(page["layoutStatus"], "SUCCEEDED")
        self.assertEqual(page["tableStatus"], "SUCCEEDED")
        self.assertEqual(page["formulaStatus"], "SUCCEEDED")
        self.assertEqual([item["type"] for item in page["elements"]], ["heading", "table", "formula"])
        self.assertEqual(page["elements"][1]["metadata"]["html"], "<table><tr><td>Layer</td><td>Purpose</td></tr></table>")
        self.assertIn("Computer Networking", page["text"])
        self.assertIn("E = mc^2", page["text"])
        self.assertIn("STRUCTURE_TEST_WARNING", page["warnings"])

    def test_image_ocr_returns_same_page_contract(self):
        image_base64 = base64.b64encode(b"fake-image").decode("ascii")

        response = self.client.post("/ocr/image", json={"imageBase64": image_base64, "language": "ch"})

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "SUCCEEDED")
        self.assertEqual(body["pages"][0]["text"], "hello")


if __name__ == "__main__":
    unittest.main()
