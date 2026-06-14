import base64
import unittest

from fastapi.testclient import TestClient

from app.main import create_app
from app.schemas import OcrPage, OcrResponse, TextBlock


class FakeEngine:
    sdk_loaded = True
    provider = "PaddleOCR"
    version = "test"
    unavailable_reason = None

    def recognize_image(self, image_path, language):
        return [
            TextBlock(
                text="hello",
                bbox=[[0, 0], [20, 0], [20, 10], [0, 10]],
                confidence=0.99,
                order=1,
            )
        ]


class FakeRenderer:
    def render_pdf(self, document_bytes, page_start=None, page_end=None, max_pages=20, dpi=220):
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


class ApiContractTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(create_app(text_engine=FakeEngine(), renderer=FakeRenderer()))

    def test_health_reports_provider_and_sdk_status(self):
        response = self.client.get("/health")

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["provider"], "PaddleOCR")
        self.assertTrue(body["sdkLoaded"])

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

    def test_image_ocr_returns_same_page_contract(self):
        image_base64 = base64.b64encode(b"fake-image").decode("ascii")

        response = self.client.post("/ocr/image", json={"imageBase64": image_base64, "language": "ch"})

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "SUCCEEDED")
        self.assertEqual(body["pages"][0]["text"], "hello")


if __name__ == "__main__":
    unittest.main()
