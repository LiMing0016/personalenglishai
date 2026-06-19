from typing import Any, Literal

from pydantic import BaseModel, Field


OcrStatus = Literal["SUCCEEDED", "PARTIAL", "FAILED"]
ParseMode = Literal["standard", "high_quality"]


class OcrPdfRequest(BaseModel):
    documentBase64: str = Field(min_length=1)
    language: str = "ch,eng"
    parseMode: ParseMode = "standard"
    pageStart: int | None = Field(default=None, ge=1)
    pageEnd: int | None = Field(default=None, ge=1)
    maxPages: int = Field(default=20, ge=1, le=500)
    enableTextOcr: bool = True
    enableLayout: bool | None = None
    enableTable: bool | None = None
    enableFormula: bool | None = None
    enableOrientation: bool | None = None
    enableUnwarping: bool | None = None
    dpi: int = Field(default=220, ge=72, le=400)

    def feature_enabled(self, name: str) -> bool:
        value = getattr(self, name)
        if value is not None:
            return bool(value)
        if self.parseMode == "high_quality":
            return name in {"enableLayout", "enableTable", "enableFormula", "enableOrientation", "enableUnwarping"}
        return name == "enableOrientation"


class OcrImageRequest(BaseModel):
    imageBase64: str = Field(min_length=1)
    language: str = "ch,eng"
    parseMode: ParseMode = "standard"
    enableTextOcr: bool = True
    enableLayout: bool | None = None
    enableTable: bool | None = None
    enableFormula: bool | None = None
    enableOrientation: bool | None = None
    enableUnwarping: bool | None = None

    def feature_enabled(self, name: str) -> bool:
        value = getattr(self, name)
        if value is not None:
            return bool(value)
        if self.parseMode == "high_quality":
            return name in {"enableLayout", "enableTable", "enableFormula", "enableOrientation", "enableUnwarping"}
        return name == "enableOrientation"


class TextBlock(BaseModel):
    text: str
    bbox: list[list[float]]
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    order: int = Field(default=0, ge=0)


class FormulaBlock(BaseModel):
    latex: str
    bbox: list[list[float]] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    imageRef: str | None = None
    warnings: list[str] = Field(default_factory=list)


class ElementBlock(BaseModel):
    type: str = "paragraph"
    text: str = ""
    bbox: list[list[float]] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    order: int = Field(default=0, ge=0)
    source: str = "paddle_ocr"
    rawType: str | None = None
    warnings: list[str] = Field(default_factory=list)
    metadata: dict[str, Any] = Field(default_factory=dict)


class OcrPage(BaseModel):
    pageNumber: int = Field(ge=1)
    text: str = ""
    rawText: str = ""
    cleanedText: str = ""
    blocks: list[TextBlock] = Field(default_factory=list)
    formulas: list[FormulaBlock] = Field(default_factory=list)
    elements: list[ElementBlock] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    qualityScore: float = Field(default=0.0, ge=0.0, le=1.0)
    width: int | None = None
    height: int | None = None
    layoutStatus: str = "NOT_REQUESTED"
    tableStatus: str = "NOT_REQUESTED"
    formulaStatus: str = "NOT_REQUESTED"
    warnings: list[str] = Field(default_factory=list)


class DocumentStatus(BaseModel):
    status: OcrStatus
    pageCount: int
    recognizedPageCount: int
    warnings: list[str] = Field(default_factory=list)


class OcrResponse(BaseModel):
    status: OcrStatus
    provider: str = "PaddleOCR"
    pages: list[OcrPage] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    elapsedMs: int = 0
    pageCount: int = 0
    recognizedPageCount: int = 0
    message: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    status: Literal["UP", "DEGRADED"]
    provider: str = "PaddleOCR"
    sdkLoaded: bool
    version: str | None = None
    formulaEnabled: bool = False
    message: str | None = None
