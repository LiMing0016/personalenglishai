import unittest

from app.formula_engine import FormulaRecognitionEngine


class FakeFormulaModel:
    def predict(self, input):
        return [
            {
                "latex": "E = mc^2",
                "bbox": [[1, 2], [10, 2], [10, 8], [1, 8]],
                "confidence": 0.87,
            }
        ]


class FailingFormulaModel:
    def predict(self, input):
        raise RuntimeError("model failed")


class FormulaEngineTest(unittest.TestCase):
    def test_disabled_formula_engine_returns_no_warning(self):
        engine = FormulaRecognitionEngine(enabled=False)

        formulas, warnings = engine.recognize_formulas("page.png")

        self.assertEqual(formulas, [])
        self.assertEqual(warnings, [])

    def test_normalizes_formula_model_output(self):
        engine = FormulaRecognitionEngine(enabled=True, model=FakeFormulaModel())

        formulas, warnings = engine.recognize_formulas("page.png")

        self.assertEqual(warnings, [])
        self.assertEqual(formulas[0].latex, "E = mc^2")
        self.assertAlmostEqual(formulas[0].confidence, 0.87)

    def test_formula_failure_returns_warning_without_exception(self):
        engine = FormulaRecognitionEngine(enabled=True, model=FailingFormulaModel())

        formulas, warnings = engine.recognize_formulas("page.png")

        self.assertEqual(formulas, [])
        self.assertIn("FORMULA_RECOGNITION_FAILED:model failed", warnings)


if __name__ == "__main__":
    unittest.main()
