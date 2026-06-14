from app.schemas import DocumentStatus, OcrPage


LOW_CONFIDENCE_THRESHOLD = 0.6
SPARSE_TEXT_BLOCK_THRESHOLD = 2


def assess_page_quality(page: OcrPage) -> OcrPage:
    warnings = list(dict.fromkeys(page.warnings))
    if not page.blocks and not page.text.strip():
        warnings.append("EMPTY_PAGE")
        page.confidence = 0.0
        page.warnings = list(dict.fromkeys(warnings))
        return page

    if page.blocks:
        confidence = sum(block.confidence for block in page.blocks) / len(page.blocks)
    else:
        confidence = page.confidence or 0.5

    if confidence < LOW_CONFIDENCE_THRESHOLD:
        warnings.append("LOW_CONFIDENCE")
    if len(page.blocks) < SPARSE_TEXT_BLOCK_THRESHOLD and page.text.strip():
        warnings.append("SPARSE_TEXT")

    page.confidence = round(max(0.0, min(1.0, confidence)), 4)
    page.warnings = list(dict.fromkeys(warnings))
    return page


def aggregate_document_status(pages: list[OcrPage]) -> DocumentStatus:
    warnings: list[str] = []
    recognized = 0
    for page in pages:
        warnings.extend(page.warnings)
        if page.text.strip() or page.blocks or page.formulas:
            recognized += 1

    page_count = len(pages)
    if page_count == 0 or recognized == 0:
        status = "FAILED"
    elif recognized < page_count:
        status = "PARTIAL"
    else:
        status = "SUCCEEDED"

    return DocumentStatus(
        status=status,
        pageCount=page_count,
        recognizedPageCount=recognized,
        warnings=list(dict.fromkeys(warnings)),
    )
