from __future__ import annotations

import base64
import importlib
import inspect
import io
import os
import tempfile
import threading
from pathlib import Path
from typing import Any

from app.document_pipeline import merge_elements_text, normalize_document_pipeline_result
from app.ocr_engine import apply_common_paddle_kwargs
from app.schemas import ElementBlock, OcrAsset, OcrPage


class PaddleVlDocumentEngine:
    provider = "PaddleOCR-VL"
    disabled_message = "PaddleOCR-VL is disabled"
    not_loaded_message = "PaddleOCR-VL has not been loaded yet"

    def __init__(self, enabled: bool | None = None, load_on_init: bool = False):
        self.enabled = _env_bool("PADDLE_OCR_VL_ENABLED", False) if enabled is None else bool(enabled)
        self.sdk_loaded = False
        self.version: str | None = None
        self.unavailable_reason: str | None = self.disabled_message if not self.enabled else self.not_loaded_message
        self._pipeline: Any | None = None
        self._load_lock = threading.Lock()
        if self.enabled and load_on_init:
            self._load_sdk()

    def _load_sdk(self) -> None:
        if not self.enabled:
            self.sdk_loaded = False
            self.unavailable_reason = self.disabled_message
            self._pipeline = None
            return
        try:
            paddleocr_module = importlib.import_module("paddleocr")
            pipeline_class = getattr(paddleocr_module, "PaddleOCRVL")
            self.version = getattr(paddleocr_module, "__version__", None)
            self._pipeline = pipeline_class(**build_paddle_vl_kwargs(pipeline_class))
            self.sdk_loaded = True
            self.unavailable_reason = None
        except Exception as exc:
            self.unavailable_reason = str(exc)
            self.sdk_loaded = False
            self._pipeline = None

    def recognize_pdf(
        self,
        document_bytes: bytes,
        language: str | None = None,
        page_start: int | None = None,
        page_end: int | None = None,
        max_pages: int = 20,
        dpi: int = 220,
        enable_layout: bool = True,
        enable_table: bool = True,
        enable_formula: bool = False,
        enable_orientation: bool = True,
        enable_unwarping: bool = True,
    ) -> dict[str, Any]:
        if not document_bytes:
            raise ValueError("PDF_EMPTY")
        if not self.sdk_loaded or self._pipeline is None:
            with self._load_lock:
                if not self.sdk_loaded or self._pipeline is None:
                    self._load_sdk()
        if not self.sdk_loaded or self._pipeline is None:
            raise RuntimeError(self.unavailable_reason or "PaddleOCR-VL is not loaded")

        temp_dir, pdf_path, warnings = _prepare_pdf_input(document_bytes, page_start, page_end, max_pages)
        try:
            raw_result = predict_vl(
                self._pipeline,
                pdf_path,
                enable_layout=enable_layout,
                enable_table=enable_table,
                enable_formula=enable_formula,
                enable_orientation=enable_orientation,
                enable_unwarping=enable_unwarping,
            )
            normalized = normalize_vl_result(raw_result)
            _apply_page_number_offset(normalized, page_start)
            normalized["warnings"] = list(dict.fromkeys([*warnings, *normalized.get("warnings", [])]))
            normalized["metadata"] = {
                "engine": self.provider,
                "version": self.version,
                "language": language,
                "dpi": dpi,
                **normalized.get("metadata", {}),
            }
            return normalized
        finally:
            temp_dir.cleanup()


def build_paddle_vl_kwargs(pipeline_class: Any) -> dict[str, Any]:
    parameters = inspect.signature(pipeline_class).parameters
    kwargs: dict[str, Any] = {}
    _put_if_supported(kwargs, parameters, "pipeline_version", os.getenv("PADDLE_OCR_VL_PIPELINE_VERSION", "v1.6"))
    _put_if_supported(kwargs, parameters, "vl_rec_model_dir", os.getenv("PADDLE_OCR_VL_MODEL_DIR"))
    _put_if_supported(kwargs, parameters, "vl_rec_backend", os.getenv("PADDLE_OCR_VL_BACKEND"))
    _put_if_supported(kwargs, parameters, "vl_rec_server_url", os.getenv("PADDLE_OCR_VL_SERVER_URL"))
    _put_if_supported(kwargs, parameters, "vl_rec_api_model_name", os.getenv("PADDLE_OCR_VL_API_MODEL_NAME"))
    _put_if_supported(kwargs, parameters, "vl_rec_api_key", os.getenv("PADDLE_OCR_VL_API_KEY"))
    _put_if_supported(kwargs, parameters, "format_block_content", True)
    _put_if_supported(kwargs, parameters, "merge_layout_blocks", True)
    _put_if_supported(kwargs, parameters, "use_chart_recognition", _env_bool("PADDLE_OCR_VL_USE_CHART_RECOGNITION", False))
    _put_if_supported(kwargs, parameters, "use_seal_recognition", _env_bool("PADDLE_OCR_VL_USE_SEAL_RECOGNITION", False))
    apply_common_paddle_kwargs(kwargs, parameters)
    return kwargs


def predict_vl(
    pipeline: Any,
    pdf_path: str,
    enable_layout: bool = True,
    enable_table: bool = True,
    enable_formula: bool = False,
    enable_orientation: bool = True,
    enable_unwarping: bool = True,
) -> Any:
    predict = getattr(pipeline, "predict")
    kwargs = {
        "use_doc_orientation_classify": bool(enable_orientation),
        "use_doc_unwarping": bool(enable_unwarping),
        "use_layout_detection": bool(enable_layout),
        "use_chart_recognition": bool(enable_table),
        "format_block_content": True,
        "merge_layout_blocks": True,
    }
    if enable_formula:
        kwargs["vlm_extra_args"] = {"enable_formula": True}
    try:
        return predict(input=pdf_path, **kwargs)
    except TypeError:
        try:
            return predict(pdf_path, **kwargs)
        except TypeError:
            return predict(pdf_path)


def normalize_vl_result(raw_result: Any) -> dict[str, Any]:
    if isinstance(raw_result, dict) and "pages" in raw_result:
        return {
            "pages": _coerce_pages(raw_result.get("pages")),
            "assets": _coerce_assets(raw_result.get("assets")),
            "warnings": _coerce_string_list(raw_result.get("warnings")),
            "metadata": raw_result.get("metadata") if isinstance(raw_result.get("metadata"), dict) else {},
            "raw": raw_result.get("raw", {}),
        }

    pages: list[OcrPage] = []
    assets: list[OcrAsset] = []
    warnings: list[str] = []
    raw_items: list[dict[str, Any]] = []
    for fallback_page_number, item in enumerate(_iter_result_dicts(raw_result), start=1):
        raw_items.append(_json_safe(item))
        page = _page_from_vl_item(item, fallback_page_number)
        if page is not None:
            pages.append(page)
            page_number = page.pageNumber
        else:
            page_number = _page_number(item, fallback_page_number)
        assets.extend(_assets_from_vl_item(item, page_number, len(assets) + 1))

    if not pages and raw_result:
        warnings.append("PADDLE_VL_EMPTY_RESULT")

    return {
        "pages": pages,
        "assets": assets,
        "warnings": warnings,
        "metadata": {"engine": "PaddleOCR-VL"},
        "raw": {"items": raw_items},
    }


def _page_from_vl_item(item: dict[str, Any], fallback_page_number: int) -> OcrPage | None:
    normalized = normalize_document_pipeline_result([item])
    elements = _mark_vl_elements(normalized.get("elements", []))
    markdown = _extract_markdown_text(item)
    text = markdown or merge_elements_text(elements) or _extract_overall_text(item)
    if not text and not elements:
        return None
    page_number = _page_number(item, fallback_page_number)
    return OcrPage(
        pageNumber=page_number,
        text=text,
        rawText=_extract_overall_text(item),
        cleanedText=text,
        elements=elements,
        width=_safe_optional_int(item.get("width")),
        height=_safe_optional_int(item.get("height")),
        layoutStatus="SUCCEEDED" if elements else "EMPTY",
        tableStatus="SUCCEEDED" if _has_element_type(elements, "table") else "EMPTY",
        formulaStatus="SUCCEEDED" if _has_element_type(elements, "formula") else "NOT_REQUESTED",
        warnings=_coerce_string_list(normalized.get("warnings")),
    )


def _prepare_pdf_input(
    document_bytes: bytes,
    page_start: int | None,
    page_end: int | None,
    max_pages: int,
) -> tuple[tempfile.TemporaryDirectory[str], str, list[str]]:
    temp_dir = tempfile.TemporaryDirectory(prefix="peai-paddle-vl-")
    output_path = Path(temp_dir.name) / "document.pdf"
    warnings: list[str] = []
    try:
        import fitz

        doc = fitz.open(stream=document_bytes, filetype="pdf")
        try:
            if doc.page_count == 0:
                raise ValueError("PDF_EMPTY")
            start = max(1, page_start or 1)
            end = min(page_end or doc.page_count, doc.page_count)
            if start > end or start > doc.page_count:
                raise ValueError("PAGE_RANGE_OUT_OF_BOUNDS")
            selected_pages = list(range(start, end + 1))
            if len(selected_pages) > max_pages:
                selected_pages = selected_pages[:max_pages]
                warnings.append("MAX_PAGES_TRUNCATED")
            if len(selected_pages) == doc.page_count and start == 1:
                output_path.write_bytes(document_bytes)
            else:
                subset = fitz.open()
                try:
                    for page_number in selected_pages:
                        subset.insert_pdf(doc, from_page=page_number - 1, to_page=page_number - 1)
                    subset.save(output_path.as_posix())
                finally:
                    subset.close()
        finally:
            doc.close()
    except Exception:
        temp_dir.cleanup()
        raise
    return temp_dir, output_path.as_posix(), warnings


def _coerce_pages(value: Any) -> list[OcrPage]:
    pages: list[OcrPage] = []
    for item in value or []:
        if isinstance(item, OcrPage):
            pages.append(item)
        elif isinstance(item, dict):
            pages.append(OcrPage(**item))
    return pages


def _apply_page_number_offset(result: dict[str, Any], page_start: int | None) -> None:
    offset = max(0, (page_start or 1) - 1)
    if offset <= 0:
        return
    page_number_map: dict[int, int] = {}
    for page in result.get("pages") or []:
        if isinstance(page, OcrPage):
            original_page = page.pageNumber
            page.pageNumber = original_page + offset
            page_number_map[original_page] = page.pageNumber
        elif isinstance(page, dict):
            original_page = _safe_optional_int(page.get("pageNumber")) or 1
            page["pageNumber"] = original_page + offset
            page_number_map[original_page] = page["pageNumber"]
    for asset in result.get("assets") or []:
        if isinstance(asset, OcrAsset):
            original_page = asset.pageNumber
            asset.pageNumber = page_number_map.get(original_page, original_page + offset)
            asset.id = _offset_asset_id(asset.id, original_page, asset.pageNumber)
        elif isinstance(asset, dict):
            original_page = _safe_optional_int(asset.get("pageNumber")) or 1
            next_page = page_number_map.get(original_page, original_page + offset)
            asset["pageNumber"] = next_page
            asset["id"] = _offset_asset_id(str(asset.get("id") or ""), original_page, next_page)


def _offset_asset_id(asset_id: str, original_page: int, next_page: int) -> str:
    if not asset_id:
        return f"p{next_page}-vl-a1"
    prefix = f"p{original_page}-"
    if asset_id.startswith(prefix):
        return f"p{next_page}-{asset_id[len(prefix):]}"
    return asset_id


def _coerce_assets(value: Any) -> list[OcrAsset]:
    assets: list[OcrAsset] = []
    for item in value or []:
        if isinstance(item, OcrAsset):
            assets.append(item)
        elif isinstance(item, dict):
            assets.append(OcrAsset(**item))
    return assets


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
    if hasattr(result, "__dict__"):
        return dict(getattr(result, "__dict__"))
    return {}


def _mark_vl_elements(elements: list[ElementBlock]) -> list[ElementBlock]:
    for element in elements:
        element.source = "paddle_vl"
    return elements


def _assets_from_vl_item(item: dict[str, Any], page_number: int, start_order: int) -> list[OcrAsset]:
    assets: list[OcrAsset] = []
    seen_bboxes: set[str] = set()
    for raw_block in item.get("parsing_res_list") or []:
        data = raw_block if isinstance(raw_block, dict) else _result_object_to_dict(raw_block)
        raw_type = str(data.get("block_label") or data.get("label") or data.get("type") or "").strip()
        if not _is_image_raw_type(raw_type):
            continue
        bbox = _normalize_asset_bbox(data.get("block_bbox") or data.get("bbox") or data.get("box"))
        bbox_key = str(bbox)
        if bbox_key in seen_bboxes:
            continue
        encoded = _encode_asset_image(data.get("image"))
        if encoded is None:
            continue
        seen_bboxes.add(bbox_key)
        assets.append(_make_image_asset(page_number, start_order + len(assets), raw_type, bbox, encoded, data))

    for raw_image in item.get("imgs_in_doc") or []:
        data = raw_image if isinstance(raw_image, dict) else _result_object_to_dict(raw_image)
        raw_type = str(data.get("label") or data.get("type") or "image").strip()
        bbox = _normalize_asset_bbox(data.get("coordinate") or data.get("bbox") or data.get("box"))
        bbox_key = str(bbox)
        if bbox_key in seen_bboxes:
            continue
        encoded = _encode_asset_image(data.get("img") or data.get("image"))
        if encoded is None:
            continue
        seen_bboxes.add(bbox_key)
        assets.append(_make_image_asset(page_number, start_order + len(assets), raw_type, bbox, encoded, data))
    return assets


def _make_image_asset(
    page_number: int,
    order: int,
    raw_type: str,
    bbox: list[list[float]],
    encoded: dict[str, Any],
    data: dict[str, Any],
) -> OcrAsset:
    metadata: dict[str, Any] = {
        "rawType": raw_type,
    }
    if data.get("path"):
        metadata["path"] = str(data.get("path"))
    return OcrAsset(
        id=f"p{page_number}-vl-a{order}",
        assetType="image",
        pageNumber=page_number,
        bbox=bbox,
        mimeType=encoded["mimeType"],
        dataBase64=encoded["dataBase64"],
        width=encoded["width"],
        height=encoded["height"],
        order=order,
        source="paddle_vl",
        rawType=raw_type or "image",
        confidence=_safe_confidence(data.get("confidence") or data.get("score")),
        metadata=metadata,
    )


def _encode_asset_image(image: Any) -> dict[str, Any] | None:
    if image is None or not hasattr(image, "save"):
        return None
    try:
        prepared = image.copy()
        if prepared.mode not in {"RGB", "L"}:
            prepared = prepared.convert("RGB")
        width, height = prepared.size
        max_edge = _env_int("PADDLE_OCR_VL_ASSET_MAX_EDGE", 1400)
        if max(width, height) > max_edge > 0:
            prepared.thumbnail((max_edge, max_edge))
            width, height = prepared.size
        buffer = io.BytesIO()
        if prepared.mode == "L":
            prepared = prepared.convert("RGB")
        prepared.save(buffer, format="JPEG", quality=_env_int("PADDLE_OCR_VL_ASSET_JPEG_QUALITY", 82), optimize=True)
        return {
            "mimeType": "image/jpeg",
            "dataBase64": base64.b64encode(buffer.getvalue()).decode("ascii"),
            "width": width,
            "height": height,
        }
    except Exception:
        return None


def _is_image_raw_type(raw_type: str) -> bool:
    normalized = raw_type.strip().lower().replace(" ", "_")
    return normalized in {
        "image",
        "figure",
        "picture",
        "diagram",
        "chart",
        "header_image",
        "footer_image",
    }


def _extract_markdown_text(item: dict[str, Any]) -> str:
    markdown = item.get("markdown") or item.get("markdown_text") or item.get("markdownText")
    if isinstance(markdown, str):
        return markdown.strip()
    if isinstance(markdown, dict):
        return str(markdown.get("text") or markdown.get("markdown") or "").strip()
    return ""


def _extract_overall_text(item: dict[str, Any]) -> str:
    candidates = [
        item.get("text"),
        item.get("content"),
        item.get("overall_ocr_res"),
        item.get("ocr_res"),
    ]
    for candidate in candidates:
        text = _coerce_text(candidate)
        if text:
            return text
    return ""


def _coerce_text(value: Any) -> str:
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, dict):
        texts = value.get("rec_texts") or value.get("texts") or []
        if isinstance(texts, list):
            return "\n".join(str(item).strip() for item in texts if str(item).strip()).strip()
    return ""


def _page_number(item: dict[str, Any], fallback: int) -> int:
    for key in ["pageNumber", "page_number", "page_num"]:
        value = _safe_optional_int(item.get(key))
        if value is not None and value > 0:
            return value
    page_index = _safe_optional_int(item.get("page_index"))
    if page_index is not None and page_index >= 0:
        return page_index + 1
    return fallback


def _has_element_type(elements: list[ElementBlock], element_type: str) -> bool:
    return any(element.type == element_type and element.text.strip() for element in elements)


def _coerce_string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if str(item).strip()]


def _safe_optional_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _safe_confidence(value: Any) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return 0.0
    if parsed < 0:
        return 0.0
    if parsed > 1:
        return 1.0
    return parsed


def _normalize_asset_bbox(value: Any) -> list[list[float]]:
    value = _to_plain(value)
    if value is None:
        return []
    if _looks_like_rect(value):
        left, top, right, bottom = [_safe_float(item) for item in value[:4]]
        return [[left, top], [right, top], [right, bottom], [left, bottom]]
    if _looks_like_points(value):
        return [[_safe_float(point[0]), _safe_float(point[1])] for point in value]
    return []


def _looks_like_rect(value: Any) -> bool:
    return isinstance(value, (list, tuple)) and len(value) == 4 and all(_is_number_like(item) for item in value)


def _looks_like_points(value: Any) -> bool:
    if not isinstance(value, (list, tuple)) or not value:
        return False
    first = _to_plain(value[0])
    return isinstance(first, (list, tuple)) and len(first) >= 2 and _is_number_like(first[0]) and _is_number_like(first[1])


def _is_number_like(value: Any) -> bool:
    try:
        float(value)
        return True
    except (TypeError, ValueError):
        return False


def _safe_float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _to_plain(value: Any) -> Any:
    if hasattr(value, "tolist"):
        return value.tolist()
    return value


def _json_safe(value: Any) -> Any:
    if hasattr(value, "tolist"):
        value = value.tolist()
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)


def _put_if_supported(kwargs: dict[str, Any], parameters: dict[str, Any], name: str, value: Any) -> None:
    if value is None or value == "":
        return
    if name in parameters or any(parameter.kind == inspect.Parameter.VAR_KEYWORD for parameter in parameters.values()):
        kwargs[name] = value


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    try:
        return int(value)
    except ValueError:
        return default
