from __future__ import annotations

import mimetypes
import os
import unittest
from pathlib import Path

from python.ai_orchestrator.schemas.vocabulary_image_recognition import (
    MAX_CANDIDATES,
    PROMPT_VERSION,
    VocabularyImageRecognitionRequest,
)
from python.ai_orchestrator.services.vocabulary_image_recognition import (
    VocabularyImageRecognitionService,
)


def configured_real_smoke() -> tuple[VocabularyImageRecognitionService, Path]:
    image_value = os.getenv("VOCABULARY_IMAGE_RECOGNITION_SMOKE_IMAGE", "").strip()
    required_values = (
        os.getenv("RUN_VOCABULARY_IMAGE_RECOGNITION_REAL_SMOKE") == "1",
        bool(os.getenv("OPENAI_API_KEY", "").strip()),
        bool(os.getenv("VOCABULARY_IMAGE_RECOGNITION_MODEL", "").strip()),
        bool(image_value),
    )
    if not all(required_values):
        raise unittest.SkipTest("real image recognition smoke is not explicitly configured")

    image_path = Path(image_value)
    content_type, _ = mimetypes.guess_type(image_path.name)
    if not image_path.is_file() or content_type not in {
        "image/jpeg",
        "image/png",
        "image/webp",
    }:
        raise unittest.SkipTest("real image recognition smoke image is unavailable or unsupported")

    service = VocabularyImageRecognitionService.from_env()
    if not service.is_configured():
        raise unittest.SkipTest("real image recognition smoke service is not configured")
    return service, image_path


class VocabularyImageRecognitionRealSmokeTest(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.service, cls.image_path = configured_real_smoke()

    async def test_real_image_recognition_preserves_public_contract(self) -> None:
        content_type, _ = mimetypes.guess_type(self.image_path.name)
        request = VocabularyImageRecognitionRequest(
            contractVersion=1,
            traceId="vocab-image-real-smoke",
            language="en",
            fileName=self.image_path.name,
            contentType=content_type,
            content=self.image_path.read_bytes(),
        )

        response = await self.service.recognize(request)

        self.assertEqual(response.contract_version, 1)
        self.assertEqual(response.trace_id, request.trace_id)
        self.assertEqual(response.generation.trace_id, request.trace_id)
        self.assertLessEqual(len(response.items), MAX_CANDIDATES)
        self.assertEqual(response.generation.prompt_version, PROMPT_VERSION)
        self.assertGreaterEqual(response.generation.model_call_count, 1)
        self.assertLessEqual(response.generation.model_call_count, 2)


if __name__ == "__main__":
    unittest.main()
