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
        self.assertIn("APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLED: ${APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLED:-false}", compose)
        self.assertIn("APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_BASE_URL: ${APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_BASE_URL:-http://paddle-ocr:8090}", compose)

    def test_local_compose_passes_optional_vl_settings_to_paddle_service(self):
        compose = (ROOT / "docker-compose.local.yml").read_text()

        self.assertIn("PADDLE_OCR_VL_ENABLED: ${PADDLE_OCR_VL_ENABLED:-false}", compose)
        self.assertIn("PADDLE_OCR_VL_PIPELINE_VERSION: ${PADDLE_OCR_VL_PIPELINE_VERSION:-v1.6}", compose)

    def test_local_compose_passes_optional_paddle_device_to_paddle_service(self):
        compose = (ROOT / "docker-compose.local.yml").read_text()

        self.assertIn("PADDLE_OCR_DEVICE: ${PADDLE_OCR_DEVICE:-}", compose)

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

    def test_ocr_runtime_profile_examples_keep_apple_and_a4000_paths_distinct(self):
        apple_profile = (ROOT / ".env.ocr.apple-mac.example").read_text()
        a4000_profile = (ROOT / ".env.ocr.nvidia-a4000.example").read_text()

        self.assertIn("PEAI_OCR_RUNTIME_PROFILE=apple-local-mac", apple_profile)
        self.assertIn("APP_OCR_PADDLE_BASE_URL=http://host.docker.internal:8091", apple_profile)
        self.assertIn("PADDLE_OCR_DEVICE=", apple_profile)

        self.assertIn("PEAI_OCR_RUNTIME_PROFILE=nvidia-a4000-remote", a4000_profile)
        self.assertIn("APP_OCR_PADDLE_BASE_URL=http://<A4000_LAN_IP>:8090", a4000_profile)
        self.assertIn("PADDLE_OCR_DEVICE=gpu:0", a4000_profile)


if __name__ == "__main__":
    unittest.main()
