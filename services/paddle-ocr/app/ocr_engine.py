from __future__ import annotations

import importlib
from typing import Any

from app.schemas import TextBlock


class TextOcrEngine:
    provider = "PaddleOCR"

    def __init__(self, default_language: str = "ch"):
        self.default_language = normalize_language(default_language)
        self.sdk_loaded = False
        self.version: str | None = None
        self.unavailable_reason: str | None = None
        self._ocr: Any | None = None
        self._load_sdk()

    def _load_sdk(self) -> None:
        try:
            paddleocr_module = importlib.import_module("paddleocr")
            paddleocr_class = getattr(paddleocr_module, "PaddleOCR")
            self.version = getattr(paddleocr_module, "__version__", None)
            self._ocr = paddleocr_class(use_angle_cls=True, lang=self.default_language)
            self.sdk_loaded = True
        except Exception as exc:  # PaddleOCR is optional at app boot time.
            self.unavailable_reason = str(exc)
            self.sdk_loaded = False
            self._ocr = None

    def recognize_image(self, image_path: str, language: str | None = None) -> list[TextBlock]:
        if not self.sdk_loaded or self._ocr is None:
            raise RuntimeError(self.unavailable_reason or "PaddleOCR SDK is not loaded")
        raw_result = self._ocr.ocr(image_path, cls=True)
        return normalize_paddle_ocr_result(raw_result)


def normalize_language(language: str | None) -> str:
    if not language:
        return "ch"
    normalized = language.lower().replace(" ", "")
    if normalized in {"ch,eng", "zh,en", "zh-cn,en", "chinese,english"}:
        return "ch"
    return normalized.split(",")[0] or "ch"


def normalize_paddle_ocr_result(raw_result: Any) -> list[TextBlock]:
    rows = _flatten_ocr_rows(raw_result)
    blocks: list[TextBlock] = []
    for row in rows:
        parsed = _parse_row(row)
        if parsed is not None:
            blocks.append(parsed)
    return _assign_order(sort_blocks_reading_order(blocks))


def sort_blocks_reading_order(blocks: list[TextBlock]) -> list[TextBlock]:
    return sorted(blocks, key=lambda block: (_bbox_top(block.bbox), _bbox_left(block.bbox)))


def merge_blocks_text(blocks: list[TextBlock]) -> str:
    ordered = sort_blocks_reading_order(blocks)
    if not ordered:
        return ""

    lines: list[list[TextBlock]] = []
    current_line: list[TextBlock] = []
    current_top: float | None = None
    current_height = 0.0
    for block in ordered:
        top = _bbox_top(block.bbox)
        height = max(1.0, _bbox_bottom(block.bbox) - top)
        threshold = max(8.0, current_height * 0.7)
        if current_top is None or abs(top - current_top) <= threshold:
            current_line.append(block)
            current_top = top if current_top is None else min(current_top, top)
            current_height = max(current_height, height)
        else:
            lines.append(sort_blocks_reading_order(current_line))
            current_line = [block]
            current_top = top
            current_height = height
    if current_line:
        lines.append(sort_blocks_reading_order(current_line))

    return "\n".join(" ".join(block.text.strip() for block in line if block.text.strip()) for line in lines).strip()


def _flatten_ocr_rows(raw_result: Any) -> list[Any]:
    if raw_result is None:
        return []
    if isinstance(raw_result, dict):
        if "rec_texts" in raw_result:
            boxes = raw_result.get("dt_polys") or raw_result.get("rec_polys") or []
            texts = raw_result.get("rec_texts") or []
            scores = raw_result.get("rec_scores") or []
            return list(zip(boxes, zip(texts, scores)))
        return []
    if not isinstance(raw_result, list):
        return []
    if raw_result and all(_looks_like_ocr_row(item) for item in raw_result):
        return raw_result
    rows: list[Any] = []
    for item in raw_result:
        rows.extend(_flatten_ocr_rows(item))
    return rows


def _looks_like_ocr_row(item: Any) -> bool:
    if not isinstance(item, (list, tuple)) or len(item) < 2:
        return False
    bbox = item[0]
    payload = item[1]
    return _looks_like_bbox(bbox) and _looks_like_payload(payload)


def _parse_row(row: Any) -> TextBlock | None:
    if not isinstance(row, (list, tuple)) or len(row) < 2:
        return None
    bbox = _normalize_bbox(row[0])
    text = ""
    confidence = 0.0
    payload = row[1]
    if isinstance(payload, dict):
        text = str(payload.get("text") or payload.get("rec_text") or "")
        confidence = _safe_float(payload.get("confidence") or payload.get("score"))
    elif isinstance(payload, (list, tuple)) and len(payload) >= 2:
        text = str(payload[0] or "")
        confidence = _safe_float(payload[1])
    if not text.strip():
        return None
    return TextBlock(text=text.strip(), bbox=bbox, confidence=confidence, order=0)


def _normalize_bbox(value: Any) -> list[list[float]]:
    if not isinstance(value, (list, tuple)):
        return []
    bbox: list[list[float]] = []
    for point in value:
        if isinstance(point, (list, tuple)) and len(point) >= 2:
            bbox.append([_safe_float(point[0]), _safe_float(point[1])])
    return bbox


def _looks_like_bbox(value: Any) -> bool:
    if not isinstance(value, (list, tuple)) or not value:
        return False
    first = value[0]
    return isinstance(first, (list, tuple)) and len(first) >= 2 and _is_number_like(first[0]) and _is_number_like(first[1])


def _looks_like_payload(value: Any) -> bool:
    if isinstance(value, dict):
        return True
    return isinstance(value, (list, tuple)) and len(value) >= 2 and isinstance(value[0], str)


def _assign_order(blocks: list[TextBlock]) -> list[TextBlock]:
    for index, block in enumerate(blocks, start=1):
        block.order = index
    return blocks


def _bbox_top(bbox: list[list[float]]) -> float:
    return min((point[1] for point in bbox), default=0.0)


def _bbox_bottom(bbox: list[list[float]]) -> float:
    return max((point[1] for point in bbox), default=0.0)


def _bbox_left(bbox: list[list[float]]) -> float:
    return min((point[0] for point in bbox), default=0.0)


def _safe_float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _is_number_like(value: Any) -> bool:
    try:
        float(value)
        return True
    except (TypeError, ValueError):
        return False
