from __future__ import annotations

import base64
import imghdr
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass
class RenderedPage:
    pageNumber: int
    path: str
    width: int
    height: int
    warnings: list[str]


class RenderedPages:
    def __init__(self, pages: list[RenderedPage], temp_dir: tempfile.TemporaryDirectory[str] | None = None):
        self.pages = pages
        self._temp_dir = temp_dir

    def __iter__(self):
        return iter(self.pages)

    def __len__(self):
        return len(self.pages)

    def cleanup(self) -> None:
        if self._temp_dir is not None:
            self._temp_dir.cleanup()


class PdfRenderer:
    def render_pdf(
        self,
        document_bytes: bytes,
        page_start: int | None = None,
        page_end: int | None = None,
        max_pages: int = 20,
        dpi: int = 220,
    ) -> RenderedPages:
        if not document_bytes:
            raise ValueError("PDF_EMPTY")
        try:
            import fitz  # PyMuPDF
        except Exception as exc:
            raise RuntimeError(f"PDF_RENDERER_UNAVAILABLE: {exc}") from exc

        temp_dir = tempfile.TemporaryDirectory(prefix="peai-paddle-ocr-")
        pages: list[RenderedPage] = []
        try:
            doc = fitz.open(stream=document_bytes, filetype="pdf")
            if doc.page_count == 0:
                raise ValueError("PDF_EMPTY")
            start = page_start or 1
            end = page_end or doc.page_count
            if start > end or start > doc.page_count:
                raise ValueError("PAGE_RANGE_OUT_OF_BOUNDS")
            end = min(end, doc.page_count)
            selected_pages = list(range(start, end + 1))
            warnings: list[str] = []
            if len(selected_pages) > max_pages:
                selected_pages = selected_pages[:max_pages]
                warnings.append("MAX_PAGES_TRUNCATED")

            zoom = dpi / 72.0
            matrix = fitz.Matrix(zoom, zoom)
            for page_number in selected_pages:
                page = doc.load_page(page_number - 1)
                pix = page.get_pixmap(matrix=matrix, alpha=False)
                path = Path(temp_dir.name) / f"page-{page_number}.png"
                pix.save(path.as_posix())
                page_warnings = warnings if page_number == selected_pages[-1] else []
                pages.append(
                    RenderedPage(
                        pageNumber=page_number,
                        path=path.as_posix(),
                        width=pix.width,
                        height=pix.height,
                        warnings=list(page_warnings),
                    )
                )
            doc.close()
            return RenderedPages(pages, temp_dir)
        except Exception:
            temp_dir.cleanup()
            raise

    def validate_image(self, image_bytes: bytes) -> RenderedPage:
        if not image_bytes:
            raise ValueError("IMAGE_EMPTY")
        image_type = imghdr.what(None, image_bytes)
        if image_type not in {"png", "jpeg"}:
            raise ValueError("IMAGE_UNSUPPORTED_FORMAT")
        try:
            from PIL import Image
        except Exception as exc:
            raise RuntimeError(f"IMAGE_RENDERER_UNAVAILABLE: {exc}") from exc

        temp_dir = tempfile.TemporaryDirectory(prefix="peai-paddle-ocr-image-")
        path = Path(temp_dir.name) / f"image.{image_type}"
        path.write_bytes(image_bytes)
        with Image.open(path) as image:
            width, height = image.size
        page = RenderedPage(pageNumber=1, path=path.as_posix(), width=width, height=height, warnings=[])
        page._temp_dir = temp_dir  # type: ignore[attr-defined]
        return page


def decode_base64(value: str) -> bytes:
    try:
        return base64.b64decode(value, validate=True)
    except Exception as exc:
        raise ValueError("INVALID_BASE64") from exc


def cleanup_rendered(value) -> None:
    cleanup = getattr(value, "cleanup", None)
    if callable(cleanup):
        cleanup()
        return
    temp_dir = getattr(value, "_temp_dir", None)
    if temp_dir is not None:
        temp_dir.cleanup()
