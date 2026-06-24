from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]


class DeploymentContractTest(unittest.TestCase):
    def test_requirements_include_ppstructure_runtime_dependencies(self):
        requirements = (ROOT / "services" / "paddle-ocr" / "requirements.txt").read_text()

        self.assertIn("paddlex[ocr]", requirements)

    def test_local_compose_passes_high_quality_ocr_settings_to_backend(self):
        compose = (ROOT / "docker-compose.local.yml").read_text()

        expected_backend_env = [
            "APP_OCR_PADDLE_TIMEOUT_MS",
            "APP_OCR_PADDLE_PARSE_MODE",
            "APP_OCR_PADDLE_MAX_PAGES",
            "APP_OCR_PADDLE_DPI",
            "APP_OCR_PADDLE_ENABLE_LAYOUT",
            "APP_OCR_PADDLE_ENABLE_TABLE",
            "APP_OCR_PADDLE_ENABLE_FORMULA",
            "APP_OCR_PADDLE_ENABLE_ORIENTATION",
            "APP_OCR_PADDLE_ENABLE_UNWARPING",
        ]
        for name in expected_backend_env:
            self.assertIn(f"{name}:", compose)

        self.assertIn("APP_OCR_PROVIDER: ${APP_OCR_PROVIDER:-paddle}", compose)
        self.assertIn("APP_OCR_PADDLE_PARSE_MODE: ${APP_OCR_PADDLE_PARSE_MODE:-high_quality}", compose)
        self.assertIn("APP_OCR_PADDLE_ENABLE_LAYOUT: ${APP_OCR_PADDLE_ENABLE_LAYOUT:-true}", compose)
        self.assertIn("APP_OCR_PADDLE_ENABLE_TABLE: ${APP_OCR_PADDLE_ENABLE_TABLE:-true}", compose)

    def test_local_compose_persists_paddlex_model_cache(self):
        compose = (ROOT / "docker-compose.local.yml").read_text()

        self.assertIn("peai_local_paddlex_cache:/root/.paddlex", compose)
        self.assertIn("peai_local_paddlex_cache:", compose)

    def test_local_compose_uses_first_level_cpu_performance_profile_without_openmp_overrides(self):
        compose = (ROOT / "docker-compose.local.yml").read_text()

        self.assertIn("PADDLE_OCR_CPU_THREADS: ${PADDLE_OCR_CPU_THREADS:-4}", compose)
        self.assertIn("PADDLE_PDX_CPU_NUM_THREADS: ${PADDLE_OCR_CPU_THREADS:-4}", compose)
        self.assertNotIn("OMP_NUM_THREADS:", compose)
        self.assertNotIn("OPENBLAS_NUM_THREADS:", compose)
        self.assertNotIn("MKL_NUM_THREADS:", compose)


if __name__ == "__main__":
    unittest.main()
