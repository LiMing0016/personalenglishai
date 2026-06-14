from __future__ import annotations

import time
from typing import Any

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.formula_engine import FormulaRecognitionEngine
from app.ocr_engine import TextOcrEngine, merge_blocks_text, sort_blocks_reading_order
from app.pdf_renderer import PdfRenderer, cleanup_rendered, decode_base64
from app.quality import aggregate_document_status, assess_page_quality
from app.schemas import HealthResponse, OcrImageRequest, OcrPage, OcrPdfRequest, OcrResponse


def create_app(
    text_engine: TextOcrEngine | None = None,
    renderer: PdfRenderer | None = None,
    formula_engine: FormulaRecognitionEngine | None = None,
) -> FastAPI:
    app = FastAPI(title="Personal English AI PaddleOCR Service", version="0.1.0")
    app.state.text_engine = text_engine or TextOcrEngine()
    app.state.renderer = renderer or PdfRenderer()
    app.state.formula_engine = formula_engine or FormulaRecognitionEngine()

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        engine = app.state.text_engine
        formula = app.state.formula_engine
        return HealthResponse(
            status="UP" if engine.sdk_loaded else "DEGRADED",
            provider=getattr(engine, "provider", "PaddleOCR"),
            sdkLoaded=bool(getattr(engine, "sdk_loaded", False)),
            version=getattr(engine, "version", None),
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
                    enable_formula=request.enableFormula,
                )
                for page in _page_iter(rendered)
            ]
            return _build_response(pages, started)
        except Exception as exc:
            return _error_response(str(exc), started)
        finally:
            if rendered is not None:
                cleanup_rendered(rendered)

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
                enable_formula=request.enableFormula,
            )
            return _build_response([page], started)
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
    enable_formula: bool,
) -> OcrPage:
    warnings = list(_page_value(page, "warnings", []))
    blocks = []
    formulas = []
    if enable_text_ocr:
        try:
            if not getattr(app.state.text_engine, "sdk_loaded", False):
                warnings.append("TEXT_OCR_ENGINE_UNAVAILABLE")
            else:
                blocks = app.state.text_engine.recognize_image(_page_value(page, "path"), language)
        except Exception as exc:
            warnings.append(f"TEXT_OCR_FAILED:{exc}")
    if enable_formula:
        page_formulas, formula_warnings = app.state.formula_engine.recognize_formulas(_page_value(page, "path"))
        formulas.extend(page_formulas)
        warnings.extend(formula_warnings)

    blocks = sort_blocks_reading_order(blocks)
    text = merge_blocks_text(blocks)
    if formulas:
        text = _append_formula_placeholders(text, formulas)

    return assess_page_quality(
        OcrPage(
            pageNumber=int(_page_value(page, "pageNumber", 1)),
            text=text,
            blocks=blocks,
            formulas=formulas,
            width=_page_value(page, "width"),
            height=_page_value(page, "height"),
            warnings=list(dict.fromkeys(warnings)),
        )
    )


def _build_response(pages: list[OcrPage], started: float) -> OcrResponse:
    status = aggregate_document_status(pages)
    return OcrResponse(
        status=status.status,
        provider="PaddleOCR",
        pages=pages,
        warnings=status.warnings,
        elapsedMs=_elapsed_ms(started),
        pageCount=status.pageCount,
        recognizedPageCount=status.recognizedPageCount,
        message=None if status.status != "FAILED" else "PaddleOCR 未识别到有效内容",
    )


def _error_response(message: str, started: float) -> JSONResponse:
    body = OcrResponse(
        status="FAILED",
        provider="PaddleOCR",
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


def _page_value(page: Any, name: str, default=None):
    if isinstance(page, dict):
        return page.get(name, default)
    return getattr(page, name, default)


def _append_formula_placeholders(text: str, formulas) -> str:
    placeholders = [f"[FORMULA: {formula.latex or formula.imageRef or 'unrecognized'}]" for formula in formulas]
    if not text:
        return "\n".join(placeholders)
    return text + "\n" + "\n".join(placeholders)


def _elapsed_ms(started: float) -> int:
    return max(0, int((time.perf_counter() - started) * 1000))


app = create_app()
