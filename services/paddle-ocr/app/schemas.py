from typing import Any, Literal

from pydantic import BaseModel, Field


OcrStatus = Literal["SUCCEEDED", "PARTIAL", "FAILED"]


class OcrPdfRequest(BaseModel):
    documentBase64: str = Field(min_length=1)
    language: str = "ch,eng"
    pageStart: int | None = Field(default=None, ge=1)
    pageEnd: int | None = Field(default=None, ge=1)
    maxPages: int = Field(default=20, ge=1, le=500)
    enableTextOcr: bool = True
    enableFormula: bool = False
    dpi: int = Field(default=220, ge=72, le=400)


class OcrImageRequest(BaseModel):
    imageBase64: str = Field(min_length=1)
    language: str = "ch,eng"
    enableTextOcr: bool = True
    enableFormula: bool = False


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


class OcrPage(BaseModel):
    pageNumber: int = Field(ge=1)
    text: str = ""
    blocks: list[TextBlock] = Field(default_factory=list)
    formulas: list[FormulaBlock] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    width: int | None = None
    height: int | None = None
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
