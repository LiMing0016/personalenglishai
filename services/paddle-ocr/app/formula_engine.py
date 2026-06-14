from typing import Any

from app.schemas import FormulaBlock


class FormulaRecognitionEngine:
    provider = "PaddleOCR"

    def __init__(self, enabled: bool = False, model: Any | None = None):
        self.enabled = enabled
        self.sdk_loaded = model is not None
        self.version: str | None = None
        self.unavailable_reason: str | None = None
        self._model = model
        if enabled:
            self._load_sdk()

    def _load_sdk(self) -> None:
        if self._model is not None:
            return
        try:
            import paddleocr

            formula_class = getattr(paddleocr, "FormulaRecognition", None)
            if formula_class is None:
                raise RuntimeError("FormulaRecognition is not available in this PaddleOCR SDK")

            self._model = formula_class()
            self.sdk_loaded = True
            self.version = getattr(paddleocr, "__version__", None)
        except Exception as exc:
            self.sdk_loaded = False
            self.unavailable_reason = str(exc)

    def recognize_formulas(self, image_path: str) -> tuple[list[FormulaBlock], list[str]]:
        if not self.enabled:
            return [], []
        if not self.sdk_loaded:
            return [], ["FORMULA_ENGINE_UNAVAILABLE"]

        try:
            raw_result = self._model.predict(input=image_path)
            return normalize_formula_result(raw_result), []
        except Exception as exc:
            return [], [f"FORMULA_RECOGNITION_FAILED:{exc}"]


def normalize_formula_result(raw_result: Any) -> list[FormulaBlock]:
    if raw_result is None:
        return []
    if isinstance(raw_result, dict):
        return _normalize_formula_items([raw_result])
    if isinstance(raw_result, list):
        return _normalize_formula_items(raw_result)
    try:
        return _normalize_formula_items(list(raw_result))
    except TypeError:
        return []


def _normalize_formula_items(items: list[Any]) -> list[FormulaBlock]:
    formulas: list[FormulaBlock] = []
    for item in items:
        if isinstance(item, dict):
            latex = item.get("latex") or item.get("rec_formula") or item.get("formula") or item.get("text")
            if not latex:
                continue
            formulas.append(
                FormulaBlock(
                    latex=str(latex),
                    bbox=_normalize_bbox(item.get("bbox") or item.get("box") or item.get("dt_polys") or []),
                    confidence=_safe_float(item.get("confidence") or item.get("score") or item.get("rec_score")),
                    imageRef=item.get("imageRef") or item.get("image_ref"),
                )
            )
    return formulas


def _normalize_bbox(value: Any) -> list[list[float]]:
    if not isinstance(value, (list, tuple)):
        return []
    bbox: list[list[float]] = []
    for point in value:
        if isinstance(point, (list, tuple)) and len(point) >= 2:
            bbox.append([_safe_float(point[0]), _safe_float(point[1])])
    return bbox


def _safe_float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0
