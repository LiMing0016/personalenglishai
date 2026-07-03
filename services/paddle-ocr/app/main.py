from __future__ import annotations

import time
from typing import Any

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.document_pipeline import DocumentPipelineEngine, elements_to_text_blocks, merge_elements_text
from app.formula_engine import FormulaRecognitionEngine
from app.ocr_engine import TextOcrEngine, merge_blocks_text, sort_blocks_reading_order
from app.pdf_renderer import PdfRenderer, cleanup_rendered, decode_base64
from app.quality import aggregate_document_status, assess_page_quality
from app.schemas import ElementBlock, HealthResponse, OcrAsset, OcrImageRequest, OcrPage, OcrPdfRequest, OcrResponse
from app.vl_engine import PaddleVlDocumentEngine


def create_app(
    text_engine: TextOcrEngine | None = None,
    renderer: PdfRenderer | None = None,
    formula_engine: FormulaRecognitionEngine | None = None,
    document_engine: DocumentPipelineEngine | None = None,
    vl_engine: PaddleVlDocumentEngine | None = None,
) -> FastAPI:
    app = FastAPI(title="Personal English AI PaddleOCR Service", version="0.1.0")
    app.state.text_engine = text_engine or TextOcrEngine()
    app.state.renderer = renderer or PdfRenderer()
    app.state.formula_engine = formula_engine or FormulaRecognitionEngine()
    app.state.document_engine = document_engine or DocumentPipelineEngine()
    app.state.vl_engine = vl_engine or PaddleVlDocumentEngine()

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        engine = app.state.text_engine
        formula = app.state.formula_engine
        document_engine = app.state.document_engine
        vl_engine = app.state.vl_engine
        text_loaded = bool(getattr(engine, "sdk_loaded", False))
        document_loaded = bool(getattr(document_engine, "sdk_loaded", False))
        document_message = getattr(document_engine, "unavailable_reason", None)
        document_pending = not document_loaded and document_message == DocumentPipelineEngine.not_loaded_message
        vl_loaded = bool(getattr(vl_engine, "sdk_loaded", False))
        return HealthResponse(
            status="UP" if text_loaded and (document_loaded or document_pending) else "DEGRADED",
            provider=getattr(engine, "provider", "PaddleOCR"),
            sdkLoaded=text_loaded,
            version=getattr(engine, "version", None),
            documentEngineLoaded=document_loaded,
            documentEngineProvider=getattr(document_engine, "provider", None),
            documentEngineVersion=getattr(document_engine, "version", None),
            documentEngineMessage=document_message,
            vlEngineLoaded=vl_loaded,
            vlEngineProvider=getattr(vl_engine, "provider", None),
            vlEngineVersion=getattr(vl_engine, "version", None),
            vlEngineMessage=getattr(vl_engine, "unavailable_reason", None),
            formulaEnabled=bool(getattr(formula, "enabled", False)),
            message=getattr(engine, "unavailable_reason", None),
        )

    @app.post("/ocr/pdf", response_model=OcrResponse)
    def ocr_pdf(request: OcrPdfRequest):
        started = time.perf_counter()
        rendered = None
        try:
            document_bytes = decode_base64(request.documentBase64)
            rendered = app.state.renderer.render_pdf(
                document_bytes,
                page_start=request.pageStart,
                page_end=request.pageEnd,
                max_pages=request.maxPages,
                dpi=request.dpi,
            )
            pages = [
                _recognize_page(
                    app,
                    page,
                    language=request.language,
                    enable_text_ocr=request.enableTextOcr,
                    enable_layout=request.feature_enabled("enableLayout"),
                    enable_table=request.feature_enabled("enableTable"),
                    enable_formula=request.feature_enabled("enableFormula"),
                    enable_orientation=request.feature_enabled("enableOrientation"),
                    enable_unwarping=request.feature_enabled("enableUnwarping"),
                )
                for page in _page_iter(rendered)
            ]
            return _build_response(
                pages,
                started,
                metadata={
                    "parseMode": request.parseMode,
                    "maxPages": request.maxPages,
                    "dpi": request.dpi,
                    "enableLayout": request.feature_enabled("enableLayout"),
                    "enableTable": request.feature_enabled("enableTable"),
                    "enableFormula": request.feature_enabled("enableFormula"),
                    "enableOrientation": request.feature_enabled("enableOrientation"),
                    "enableUnwarping": request.feature_enabled("enableUnwarping"),
                },
            )
        except Exception as exc:
            return _error_response(str(exc), started)
        finally:
            if rendered is not None:
                cleanup_rendered(rendered)

    @app.post("/vl/pdf", response_model=OcrResponse)
    def vl_pdf(request: OcrPdfRequest):
        started = time.perf_counter()
        try:
            document_bytes = decode_base64(request.documentBase64)
            result = app.state.vl_engine.recognize_pdf(
                document_bytes,
                language=request.language,
                page_start=request.pageStart,
                page_end=request.pageEnd,
                max_pages=request.maxPages,
                dpi=request.dpi,
                enable_layout=request.feature_enabled("enableLayout"),
                enable_table=request.feature_enabled("enableTable"),
                enable_formula=request.feature_enabled("enableFormula"),
                enable_orientation=request.feature_enabled("enableOrientation"),
                enable_unwarping=request.feature_enabled("enableUnwarping"),
            )
            pages = _coerce_vl_pages(result)
            metadata = {
                "parseMode": request.parseMode,
                "maxPages": request.maxPages,
                "dpi": request.dpi,
                "enableLayout": request.feature_enabled("enableLayout"),
                "enableTable": request.feature_enabled("enableTable"),
                "enableFormula": request.feature_enabled("enableFormula"),
                "enableOrientation": request.feature_enabled("enableOrientation"),
                "enableUnwarping": request.feature_enabled("enableUnwarping"),
            }
            metadata.update(_result_value(result, "metadata", {}) or {})
            return _build_response(
                pages,
                started,
                provider=getattr(app.state.vl_engine, "provider", "PaddleOCR-VL"),
                assets=_coerce_assets(_result_value(result, "assets", [])),
                warnings=_coerce_string_list(_result_value(result, "warnings", [])),
                metadata=metadata,
            )
        except Exception as exc:
            return _error_response(str(exc), started, provider="PaddleOCR-VL")

    @app.post("/ocr/image", response_model=OcrResponse)
    def ocr_image(request: OcrImageRequest):
        started = time.perf_counter()
        rendered_page = None
        try:
            image_bytes = decode_base64(request.imageBase64)
            rendered_page = app.state.renderer.validate_image(image_bytes)
            page = _recognize_page(
                app,
                rendered_page,
                language=request.language,
                enable_text_ocr=request.enableTextOcr,
                enable_layout=request.feature_enabled("enableLayout"),
                enable_table=request.feature_enabled("enableTable"),
                enable_formula=request.feature_enabled("enableFormula"),
                enable_orientation=request.feature_enabled("enableOrientation"),
                enable_unwarping=request.feature_enabled("enableUnwarping"),
            )
            return _build_response(
                [page],
                started,
                metadata={
                    "parseMode": request.parseMode,
                    "enableLayout": request.feature_enabled("enableLayout"),
                    "enableTable": request.feature_enabled("enableTable"),
                    "enableFormula": request.feature_enabled("enableFormula"),
                    "enableOrientation": request.feature_enabled("enableOrientation"),
                    "enableUnwarping": request.feature_enabled("enableUnwarping"),
                },
            )
        except Exception as exc:
            return _error_response(str(exc), started)
        finally:
            if rendered_page is not None:
                cleanup_rendered(rendered_page)

    return app


def _recognize_page(
    app: FastAPI,
    page: Any,
    language: str,
    enable_text_ocr: bool,
    enable_layout: bool,
    enable_table: bool,
    enable_formula: bool,
    enable_orientation: bool,
    enable_unwarping: bool,
) -> OcrPage:
    warnings = list(_page_value(page, "warnings", []))
    blocks = []
    formulas = []
    document_elements: list[ElementBlock] = []
    document_pipeline_requested = enable_layout or enable_table or enable_formula

    layout_status = "NOT_REQUESTED"
    table_status = "NOT_REQUESTED"
    formula_status = "NOT_REQUESTED"

    if document_pipeline_requested:
        document_engine = app.state.document_engine
        try:
            result = document_engine.recognize_image(
                _page_value(page, "path"),
                language=language,
                enable_layout=enable_layout,
                enable_table=enable_table,
                enable_formula=enable_formula,
                enable_orientation=enable_orientation,
                enable_unwarping=enable_unwarping,
            )
            document_elements = _coerce_document_elements(result)
            warnings.extend(_coerce_document_warnings(result))
            if enable_layout:
                layout_status = "SUCCEEDED" if document_elements else "EMPTY"
            if enable_table:
                table_status = "SUCCEEDED" if _has_element_type(document_elements, "table") else "EMPTY"
            if enable_formula:
                formula_status = "SUCCEEDED" if _has_element_type(document_elements, "formula") else "EMPTY"
        except Exception as exc:
            if enable_layout:
                layout_status = "FALLBACK_TEXT"
                warnings.append(f"LAYOUT_ENGINE_FAILED:{exc}")
            if enable_table:
                table_status = "UNAVAILABLE"
                warnings.append(f"TABLE_ENGINE_FAILED:{exc}")
            if enable_formula:
                formula_status = "UNAVAILABLE"
                warnings.append(f"FORMULA_ENGINE_FAILED:{exc}")
    if enable_text_ocr:
        try:
            if not getattr(app.state.text_engine, "sdk_loaded", False):
                warnings.append("TEXT_OCR_ENGINE_UNAVAILABLE")
            else:
                blocks = app.state.text_engine.recognize_image(
                    _page_value(page, "path"),
                    language,
                    enable_orientation=enable_orientation,
                    enable_unwarping=enable_unwarping,
                )
        except Exception as exc:
            warnings.append(f"TEXT_OCR_FAILED:{exc}")

    if enable_formula and not _has_element_type(document_elements, "formula"):
        if not getattr(app.state.formula_engine, "enabled", False):
            if formula_status == "NOT_REQUESTED":
                formula_status = "UNAVAILABLE"
            warnings.append("FORMULA_ENGINE_UNAVAILABLE")
        else:
            page_formulas, formula_warnings = app.state.formula_engine.recognize_formulas(_page_value(page, "path"))
            formulas.extend(page_formulas)
            warnings.extend(formula_warnings)
            formula_status = "SUCCEEDED" if page_formulas else ("UNAVAILABLE" if formula_warnings else "EMPTY")

    blocks = sort_blocks_reading_order(blocks)
    if not blocks and document_elements:
        blocks = elements_to_text_blocks(document_elements)
    raw_text = merge_blocks_text(blocks)
    elements = document_elements or _blocks_to_elements(blocks)
    if formulas and not _has_element_type(elements, "formula"):
        elements.extend(_formulas_to_elements(formulas, start_order=len(elements) + 1))
    text = _merge_page_text(merge_elements_text(elements), raw_text)
    if formulas and not _has_element_type(document_elements, "formula"):
        text = _append_formula_placeholders(text or raw_text, formulas)

    return assess_page_quality(
        OcrPage(
            pageNumber=int(_page_value(page, "pageNumber", 1)),
            text=text,
            rawText=raw_text,
            cleanedText=text,
            blocks=blocks,
            formulas=formulas,
            elements=elements,
            width=_page_value(page, "width"),
            height=_page_value(page, "height"),
            layoutStatus=layout_status,
            tableStatus=table_status,
            formulaStatus=formula_status,
            warnings=list(dict.fromkeys(warnings)),
        )
    )


def _build_response(
    pages: list[OcrPage],
    started: float,
    metadata: dict[str, Any] | None = None,
    provider: str = "PaddleOCR",
    assets: list[OcrAsset] | None = None,
    warnings: list[str] | None = None,
) -> OcrResponse:
    status = aggregate_document_status(pages)
    merged_warnings = list(dict.fromkeys([*(warnings or []), *status.warnings]))
    return OcrResponse(
        status=status.status,
        provider=provider,
        pages=pages,
        assets=assets or [],
        warnings=merged_warnings,
        elapsedMs=_elapsed_ms(started),
        pageCount=status.pageCount,
        recognizedPageCount=status.recognizedPageCount,
        message=None if status.status != "FAILED" else f"{provider} 未识别到有效内容",
        metadata=metadata or {},
    )


def _error_response(message: str, started: float, provider: str = "PaddleOCR") -> JSONResponse:
    body = OcrResponse(
        status="FAILED",
        provider=provider,
        pages=[],
        warnings=["OCR_REQUEST_FAILED"],
        elapsedMs=_elapsed_ms(started),
        pageCount=0,
        recognizedPageCount=0,
        message=message,
    )
    return JSONResponse(status_code=200, content=body.model_dump())


def _page_iter(rendered) -> list[Any]:
    if hasattr(rendered, "pages"):
        return list(rendered.pages)
    return list(rendered)


def _merge_page_text(structured_text: str, raw_text: str) -> str:
    structured_text = (structured_text or "").strip()
    raw_text = (raw_text or "").strip()
    if not structured_text:
        return raw_text
    if not raw_text:
        return structured_text
    if raw_text in structured_text:
        return structured_text
    if structured_text in raw_text:
        return raw_text
    return f"{structured_text}\n\n{raw_text}"


def _page_value(page: Any, name: str, default=None):
    if isinstance(page, dict):
        return page.get(name, default)
    return getattr(page, name, default)


def _append_formula_placeholders(text: str, formulas) -> str:
    placeholders = [f"[FORMULA: {formula.latex or formula.imageRef or 'unrecognized'}]" for formula in formulas]
    if not text:
        return "\n".join(placeholders)
    return text + "\n" + "\n".join(placeholders)


def _blocks_to_elements(blocks) -> list[ElementBlock]:
    elements: list[ElementBlock] = []
    for index, block in enumerate(sort_blocks_reading_order(blocks), start=1):
        if not block.text.strip():
            continue
        elements.append(
            ElementBlock(
                type="paragraph",
                text=block.text.strip(),
                bbox=block.bbox,
                confidence=block.confidence,
                order=index,
                source="paddle_ocr",
                rawType="text",
            )
        )
    return elements


def _formulas_to_elements(formulas, start_order: int) -> list[ElementBlock]:
    elements: list[ElementBlock] = []
    for offset, formula in enumerate(formulas):
        text = formula.latex or formula.imageRef or ""
        if not text:
            continue
        elements.append(
            ElementBlock(
                type="formula",
                text=text,
                bbox=formula.bbox,
                confidence=formula.confidence,
                order=start_order + offset,
                source="paddle_ocr_formula",
                rawType="formula",
                warnings=formula.warnings,
            )
        )
    return elements


def _coerce_document_elements(result: Any) -> list[ElementBlock]:
    raw_elements = _result_value(result, "elements", [])
    elements: list[ElementBlock] = []
    for index, item in enumerate(raw_elements or [], start=1):
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


def _coerce_document_warnings(result: Any) -> list[str]:
    warnings = _result_value(result, "warnings", [])
    return _coerce_string_list(warnings)


def _coerce_string_list(warnings: Any) -> list[str]:
    if not isinstance(warnings, list):
        return []
    return [str(item) for item in warnings if str(item).strip()]


def _coerce_vl_pages(result: Any) -> list[OcrPage]:
    raw_pages = _result_value(result, "pages", [])
    pages: list[OcrPage] = []
    for item in raw_pages or []:
        if isinstance(item, OcrPage):
            pages.append(item)
        elif isinstance(item, dict):
            pages.append(OcrPage(**item))
    return pages


def _coerce_assets(result: Any) -> list[OcrAsset]:
    assets: list[OcrAsset] = []
    for item in result or []:
        if isinstance(item, OcrAsset):
            assets.append(item)
        elif isinstance(item, dict):
            assets.append(OcrAsset(**item))
    return assets


def _result_value(result: Any, name: str, default=None):
    if isinstance(result, dict):
        return result.get(name, default)
    return getattr(result, name, default)


def _has_element_type(elements: list[ElementBlock], element_type: str) -> bool:
    return any(element.type == element_type and element.text.strip() for element in elements)


def _elapsed_ms(started: float) -> int:
    return max(0, int((time.perf_counter() - started) * 1000))


app = create_app()
