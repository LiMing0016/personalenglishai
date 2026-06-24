from __future__ import annotations

import importlib
import inspect
import threading
from typing import Any

from app.ocr_engine import apply_common_paddle_kwargs, normalize_language, sort_blocks_reading_order
from app.schemas import ElementBlock, TextBlock


class DocumentPipelineEngine:
    provider = "PaddleOCR-PPStructureV3"
    not_loaded_message = "PPStructureV3 has not been loaded yet"

    def __init__(self, default_language: str = "ch", load_on_init: bool = False):
        self.default_language = normalize_language(default_language)
        self.sdk_loaded = False
        self.version: str | None = None
        self.unavailable_reason: str | None = self.not_loaded_message
        self._pipeline: Any | None = None
        self._load_lock = threading.Lock()
        if load_on_init:
            self._load_sdk()

    def _load_sdk(self) -> None:
        try:
            paddleocr_module = importlib.import_module("paddleocr")
            pipeline_class = getattr(paddleocr_module, "PPStructureV3")
            self.version = getattr(paddleocr_module, "__version__", None)
            self._pipeline = pipeline_class(**build_ppstructure_kwargs(pipeline_class, self.default_language))
            self.sdk_loaded = True
            self.unavailable_reason = None
        except Exception as exc:
            self.unavailable_reason = str(exc)
            self.sdk_loaded = False
            self._pipeline = None

    def recognize_image(
        self,
        image_path: str,
        language: str | None = None,
        enable_layout: bool = True,
        enable_table: bool = True,
        enable_formula: bool = True,
        enable_orientation: bool = True,
        enable_unwarping: bool = True,
    ) -> dict[str, Any]:
        if not self.sdk_loaded or self._pipeline is None:
            with self._load_lock:
                if not self.sdk_loaded or self._pipeline is None:
                    self._load_sdk()
        if not self.sdk_loaded or self._pipeline is None:
            raise RuntimeError(self.unavailable_reason or "PaddleOCR PPStructureV3 is not loaded")
        raw_result = predict_document_structure(
            self._pipeline,
            image_path,
            enable_layout=enable_layout,
            enable_table=enable_table,
            enable_formula=enable_formula,
            enable_orientation=enable_orientation,
            enable_unwarping=enable_unwarping,
        )
        return normalize_document_pipeline_result(raw_result)


def build_ppstructure_kwargs(pipeline_class: Any, language: str) -> dict[str, Any]:
    parameters = inspect.signature(pipeline_class).parameters
    kwargs: dict[str, Any] = {}
    if "lang" in parameters:
        kwargs["lang"] = language
    if "use_doc_orientation_classify" in parameters:
        kwargs["use_doc_orientation_classify"] = True
    if "use_doc_unwarping" in parameters:
        kwargs["use_doc_unwarping"] = True
    if "use_textline_orientation" in parameters:
        kwargs["use_textline_orientation"] = True
    if "use_table_recognition" in parameters:
        kwargs["use_table_recognition"] = True
    if "use_formula_recognition" in parameters:
        kwargs["use_formula_recognition"] = False
    if "use_region_detection" in parameters:
        kwargs["use_region_detection"] = True
    if "format_block_content" in parameters:
        kwargs["format_block_content"] = True
    apply_common_paddle_kwargs(kwargs, parameters)
    return kwargs


def predict_document_structure(
    pipeline: Any,
    image_path: str,
    enable_layout: bool = True,
    enable_table: bool = True,
    enable_formula: bool = True,
    enable_orientation: bool = True,
    enable_unwarping: bool = True,
) -> Any:
    predict = getattr(pipeline, "predict")
    kwargs = {
        "use_doc_orientation_classify": bool(enable_orientation),
        "use_doc_unwarping": bool(enable_unwarping),
        "use_textline_orientation": bool(enable_orientation),
        "use_table_recognition": bool(enable_table),
        "use_formula_recognition": bool(enable_formula),
        "use_region_detection": bool(enable_layout),
        "format_block_content": True,
    }
    try:
        return predict(input=image_path, **kwargs)
    except TypeError:
        try:
            return predict(image_path, **kwargs)
        except TypeError:
            return predict(image_path)


def normalize_document_pipeline_result(raw_result: Any) -> dict[str, Any]:
    if isinstance(raw_result, dict) and "elements" in raw_result:
        return {
            "elements": _coerce_elements(raw_result.get("elements")),
            "warnings": _coerce_string_list(raw_result.get("warnings")),
            "raw": raw_result.get("raw", {}),
        }

    elements: list[ElementBlock] = []
    warnings: list[str] = []
    raw_items: list[dict[str, Any]] = []
    seen_keys: set[tuple[str, str, str]] = set()

    for result in _iter_result_dicts(raw_result):
        raw_items.append(_json_safe(result))
        parsing_items = result.get("parsing_res_list") or []
        for item in parsing_items:
            element = _element_from_parsing_item(item, len(elements) + 1)
            _append_unique(elements, element, seen_keys)

        for item in result.get("table_res_list") or []:
            element = _element_from_table_item(item, len(elements) + 1)
            _append_unique(elements, element, seen_keys)

        for item in result.get("formula_res_list") or []:
            element = _element_from_formula_item(item, len(elements) + 1)
            _append_unique(elements, element, seen_keys)

    if not elements and raw_result:
        warnings.append("PPSTRUCTURE_EMPTY_RESULT")

    return {
        "elements": sorted(elements, key=lambda item: (item.order, _bbox_top(item.bbox), _bbox_left(item.bbox))),
        "warnings": warnings,
        "raw": {"items": raw_items},
    }


def elements_to_text_blocks(elements: list[ElementBlock]) -> list[TextBlock]:
    text_types = {"heading", "paragraph", "list", "quote", "code", "question", "option"}
    blocks = [
        TextBlock(text=element.text, bbox=element.bbox, confidence=element.confidence, order=element.order)
        for element in elements
        if element.type in text_types and element.text.strip()
    ]
    return sort_blocks_reading_order(blocks)


def merge_elements_text(elements: list[ElementBlock]) -> str:
    return "\n".join(item.text.strip() for item in sorted(elements, key=lambda item: item.order) if item.text.strip()).strip()


def _coerce_elements(value: Any) -> list[ElementBlock]:
    elements: list[ElementBlock] = []
    for index, item in enumerate(value or [], start=1):
        if isinstance(item, ElementBlock):
            element = item
        elif isinstance(item, dict):
            element = ElementBlock(**item)
        else:
            continue
        if element.order <= 0:
            element.order = index
        elements.append(element)
    return sorted(elements, key=lambda item: item.order)


def _iter_result_dicts(raw_result: Any) -> list[dict[str, Any]]:
    if raw_result is None:
        return []
    if isinstance(raw_result, dict):
        return [raw_result]
    if isinstance(raw_result, list):
        items: list[dict[str, Any]] = []
        for item in raw_result:
            items.extend(_iter_result_dicts(item))
        return items
    return [_result_object_to_dict(raw_result)]


def _result_object_to_dict(result: Any) -> dict[str, Any]:
    if isinstance(result, dict):
        return result
    data: dict[str, Any] = {}
    for name in [
        "input_path",
        "page_index",
        "page_count",
        "width",
        "height",
        "model_settings",
        "doc_preprocessor_res",
        "parsing_res_list",
        "overall_ocr_res",
        "formula_res_list",
        "table_res_list",
        "markdown",
    ]:
        if hasattr(result, name):
            data[name] = getattr(result, name)
    if data:
        return data
    if hasattr(result, "__dict__"):
        return dict(getattr(result, "__dict__"))
    return {}


def _element_from_parsing_item(item: Any, fallback_order: int) -> ElementBlock:
    data = item if isinstance(item, dict) else _result_object_to_dict(item)
    raw_type = str(data.get("block_label") or data.get("label") or data.get("type") or "text")
    text = str(data.get("block_content") or data.get("content") or data.get("text") or "").strip()
    order = _safe_int(data.get("block_order") or data.get("block_id"), fallback_order)
    metadata = {
        "blockId": data.get("block_id"),
    }
    return ElementBlock(
        type=_map_raw_type(raw_type),
        text=text,
        bbox=_normalize_bbox(data.get("block_bbox") or data.get("bbox") or data.get("box")),
        confidence=_safe_confidence(data.get("confidence") or data.get("score")),
        order=order,
        source="paddle_ppstructure",
        rawType=raw_type,
        metadata={key: value for key, value in metadata.items() if value is not None},
    )


def _element_from_table_item(item: Any, fallback_order: int) -> ElementBlock:
    data = item if isinstance(item, dict) else _result_object_to_dict(item)
    html = str(data.get("pred_html") or data.get("html") or "").strip()
    text = html or _join_table_ocr_text(data.get("table_ocr_pred"))
    return ElementBlock(
        type="table",
        text=text,
        bbox=_normalize_bbox(data.get("cell_box_list") or data.get("rec_polys") or data.get("bbox")),
        confidence=_safe_confidence(data.get("confidence") or data.get("score")),
        order=fallback_order,
        source="paddle_ppstructure",
        rawType="table",
        metadata={"html": html} if html else {},
    )


def _element_from_formula_item(item: Any, fallback_order: int) -> ElementBlock:
    data = item if isinstance(item, dict) else _result_object_to_dict(item)
    text = str(data.get("rec_formula") or data.get("formula") or data.get("latex") or "").strip()
    return ElementBlock(
        type="formula",
        text=text,
        bbox=_normalize_bbox(data.get("rec_polys") or data.get("bbox") or data.get("box")),
        confidence=_safe_confidence(data.get("confidence") or data.get("score")),
        order=fallback_order,
        source="paddle_ppstructure",
        rawType="formula",
        metadata={"formulaRegionId": data.get("formula_region_id")} if data.get("formula_region_id") is not None else {},
    )


def _append_unique(elements: list[ElementBlock], element: ElementBlock, seen_keys: set[tuple[str, str, str]]) -> None:
    if not element.text.strip():
        return
    key = (element.type, element.text.strip(), str(element.bbox))
    if key in seen_keys:
        return
    seen_keys.add(key)
    elements.append(element)


def _map_raw_type(raw_type: str) -> str:
    normalized = raw_type.strip().lower().replace(" ", "_")
    if normalized in {"doc_title", "document_title", "title", "header", "paragraph_title", "section_title"}:
        return "heading"
    if "table" in normalized:
        return "table"
    if "formula" in normalized or "equation" in normalized:
        return "formula"
    if normalized in {"image", "figure", "chart"}:
        return "image"
    if "list" in normalized:
        return "list"
    if normalized in {"quote"}:
        return "quote"
    if normalized in {"algorithm", "code"}:
        return "code"
    return "paragraph"


def _normalize_bbox(value: Any) -> list[list[float]]:
    value = _to_plain(value)
    if value is None:
        return []
    if _looks_like_rect(value):
        left, top, right, bottom = [_safe_float(item) for item in value[:4]]
        return [[left, top], [right, top], [right, bottom], [left, bottom]]
    if _looks_like_points(value):
        return [[_safe_float(point[0]), _safe_float(point[1])] for point in value]
    if isinstance(value, (list, tuple)):
        points: list[list[float]] = []
        for item in value:
            points.extend(_normalize_bbox(item))
        return _bbox_from_points(points)
    return []


def _bbox_from_points(points: list[list[float]]) -> list[list[float]]:
    if not points:
        return []
    left = min(point[0] for point in points)
    top = min(point[1] for point in points)
    right = max(point[0] for point in points)
    bottom = max(point[1] for point in points)
    return [[left, top], [right, top], [right, bottom], [left, bottom]]


def _looks_like_rect(value: Any) -> bool:
    return isinstance(value, (list, tuple)) and len(value) == 4 and all(_is_number_like(item) for item in value)


def _looks_like_points(value: Any) -> bool:
    if not isinstance(value, (list, tuple)) or not value:
        return False
    first = _to_plain(value[0])
    return isinstance(first, (list, tuple)) and len(first) >= 2 and _is_number_like(first[0]) and _is_number_like(first[1])


def _join_table_ocr_text(value: Any) -> str:
    if not isinstance(value, dict):
        return ""
    return " ".join(str(item).strip() for item in value.get("rec_texts") or [] if str(item).strip())


def _coerce_string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if str(item).strip()]


def _safe_int(value: Any, default: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return parsed if parsed > 0 else default


def _safe_float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _safe_confidence(value: Any) -> float:
    return max(0.0, min(1.0, _safe_float(value)))


def _is_number_like(value: Any) -> bool:
    try:
        float(value)
        return True
    except (TypeError, ValueError):
        return False


def _to_plain(value: Any) -> Any:
    if hasattr(value, "tolist"):
        return value.tolist()
    return value


def _json_safe(value: Any) -> Any:
    value = _to_plain(value)
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)


def _bbox_top(bbox: list[list[float]]) -> float:
    return min((point[1] for point in bbox), default=0.0)


def _bbox_left(bbox: list[list[float]]) -> float:
    return min((point[0] for point in bbox), default=0.0)
