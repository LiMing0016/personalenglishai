from __future__ import annotations

import hmac
import json
import logging
from pathlib import PurePath
from typing import Annotated, Any

from fastapi import Body, Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import ValidationError

try:
    from .assistant_runtime import AssistantRuntime
    from .assistant_service import AssistantConfigError
    from .env_loader import load_orchestrator_env
    from .observability import configure_observability
    from .schemas.assistant_request import AssistantRequest
    from .schemas.chat import AssistantRunResponse
    from .schemas.chat import ChatResponse
    from .schemas.routing import RoutingDecision
    from .schemas.prompt_sheet import GenerateExamPromptRequest
    from .schemas.prompt_sheet import GenerateExamPromptResponse
    from .schemas.prompt_sheet import PromptSheetChatRequest
    from .schemas.prompt_sheet import PromptSheetChatResponse
    from .schemas.learning_assets import LearningAssetOrganizeRequest
    from .schemas.learning_assets import LearningAssetOrganizeResponse
    from .schemas.vocabulary_card import VocabularyCardGenerationRequest
    from .schemas.vocabulary_card import VocabularyCardGenerationResponse
    from .schemas.vocabulary_image_recognition import MAX_IMAGE_BYTES
    from .schemas.vocabulary_image_recognition import VocabularyImageRecognitionRequest
    from .schemas.vocabulary_image_recognition import VocabularyImageRecognitionResponse
    from .schemas.vocabulary_import_analysis import VocabularyImportAnalysisRequest
    from .schemas.vocabulary_import_analysis import VocabularyImportAnalysisResponse
    from .services.prompt_sheet_workflow import PromptSheetWorkflowConfigError
    from .services.prompt_sheet_workflow import PromptSheetWorkflowService
    from .services.learning_asset_copilot import LearningAssetCopilotConfigError
    from .services.learning_asset_copilot import LearningAssetCopilotService
    from .services.assistant_request_validator import AssistantRequestValidationError
    from .raw_assistant_service import RawAssistantConfigError
    from .services.vocabulary_card_generation import VocabularyCardGenerationError
    from .services.vocabulary_card_generation import VocabularyCardGenerationService
    from .services.vocabulary_image_recognition import VocabularyImageRecognitionError
    from .services.vocabulary_image_recognition import VocabularyImageRecognitionService
    from .services.vocabulary_import_analysis import VocabularyImportAnalysisError
    from .services.vocabulary_import_analysis import VocabularyImportAnalysisService
except ImportError:  # pragma: no cover - script mode fallback
    from assistant_runtime import AssistantRuntime
    from assistant_service import AssistantConfigError
    from env_loader import load_orchestrator_env
    from observability import configure_observability
    from schemas.assistant_request import AssistantRequest
    from schemas.chat import AssistantRunResponse
    from schemas.chat import ChatResponse
    from schemas.routing import RoutingDecision
    from schemas.prompt_sheet import GenerateExamPromptRequest
    from schemas.prompt_sheet import GenerateExamPromptResponse
    from schemas.prompt_sheet import PromptSheetChatRequest
    from schemas.prompt_sheet import PromptSheetChatResponse
    from schemas.learning_assets import LearningAssetOrganizeRequest
    from schemas.learning_assets import LearningAssetOrganizeResponse
    from schemas.vocabulary_card import VocabularyCardGenerationRequest
    from schemas.vocabulary_card import VocabularyCardGenerationResponse
    from schemas.vocabulary_image_recognition import MAX_IMAGE_BYTES
    from schemas.vocabulary_image_recognition import VocabularyImageRecognitionRequest
    from schemas.vocabulary_image_recognition import VocabularyImageRecognitionResponse
    from schemas.vocabulary_import_analysis import VocabularyImportAnalysisRequest
    from schemas.vocabulary_import_analysis import VocabularyImportAnalysisResponse
    from services.prompt_sheet_workflow import PromptSheetWorkflowConfigError
    from services.prompt_sheet_workflow import PromptSheetWorkflowService
    from services.learning_asset_copilot import LearningAssetCopilotConfigError
    from services.learning_asset_copilot import LearningAssetCopilotService
    from services.assistant_request_validator import AssistantRequestValidationError
    from raw_assistant_service import RawAssistantConfigError
    from services.vocabulary_card_generation import VocabularyCardGenerationError
    from services.vocabulary_card_generation import VocabularyCardGenerationService
    from services.vocabulary_image_recognition import VocabularyImageRecognitionError
    from services.vocabulary_image_recognition import VocabularyImageRecognitionService
    from services.vocabulary_import_analysis import VocabularyImportAnalysisError
    from services.vocabulary_import_analysis import VocabularyImportAnalysisService


load_orchestrator_env()
observability_status = configure_observability()

app = FastAPI(title="PEAI Assistant Orchestrator", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://127.0.0.1:3000",
    ],
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)
service = AssistantRuntime.from_env()
prompt_sheet_service = PromptSheetWorkflowService.from_env()
learning_asset_copilot_service = LearningAssetCopilotService.from_env()
vocabulary_card_generation_service = VocabularyCardGenerationService.from_env()
vocabulary_image_recognition_service = VocabularyImageRecognitionService.from_env()
vocabulary_import_analysis_service = VocabularyImportAnalysisService.from_env()

log = logging.getLogger("uvicorn.error")


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "ok": True,
        "configured": service.is_configured(),
        "promptSheetConfigured": prompt_sheet_service.is_configured(),
        "learningAssetCopilotConfigured": learning_asset_copilot_service.is_configured(),
        "vocabularyCardGenerationConfigured": (
            vocabulary_card_generation_service.is_configured()
            and bool(getattr(vocabulary_card_generation_service, "internal_token", ""))
        ),
        "vocabularyImageRecognitionConfigured": (
            vocabulary_image_recognition_service.is_configured()
            and bool(getattr(vocabulary_image_recognition_service, "internal_token", ""))
        ),
        "vocabularyImportAnalysisConfigured": (
            vocabulary_import_analysis_service.is_configured()
            and bool(getattr(vocabulary_import_analysis_service, "internal_token", ""))
        ),
        "model": service.model,
        "langfuseTracing": observability_status.configured,
    }


def _vocabulary_generation_http_error(status_code: int, code: str, message: str) -> HTTPException:
    return HTTPException(status_code=status_code, detail={"code": code, "message": message})


def _require_vocabulary_generation_internal_token(
    authorization: Annotated[str | None, Header()] = None,
) -> None:
    expected_token = getattr(vocabulary_card_generation_service, "internal_token", "")
    if not expected_token:
        raise _vocabulary_generation_http_error(
            503,
            "VOCABULARY_GENERATION_NOT_CONFIGURED",
            "Vocabulary generation is unavailable.",
        )

    scheme, _, provided_token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not provided_token:
        raise _vocabulary_generation_http_error(
            401,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )
    if not hmac.compare_digest(provided_token, expected_token):
        raise _vocabulary_generation_http_error(
            403,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )


def _vocabulary_generation_error_to_http_exception(
    error: VocabularyCardGenerationError,
) -> HTTPException:
    if error.code in {
        "INVALID_GENERATION_REQUEST",
        "UNSUPPORTED_CONTRACT_VERSION",
        "UNSUPPORTED_CONTENT_FORMAT_VERSION",
        "UNSUPPORTED_PROMPT_STRATEGY",
    }:
        return _vocabulary_generation_http_error(
            400,
            error.code,
            "The vocabulary generation request is invalid.",
        )
    if error.code == "INTERNAL_AUTH_MISSING":
        return _vocabulary_generation_http_error(
            401,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )
    if error.code in {"INTERNAL_AUTH_FAILED", "INTERNAL_AUTH_INVALID"}:
        return _vocabulary_generation_http_error(
            403,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )
    if error.code in {"VOCABULARY_GENERATION_NOT_CONFIGURED", "MODEL_UPSTREAM_UNAVAILABLE"}:
        return _vocabulary_generation_http_error(
            503,
            error.code,
            "Vocabulary generation is unavailable.",
        )
    if error.code == "MODEL_TIMEOUT":
        return _vocabulary_generation_http_error(
            504,
            "MODEL_TIMEOUT",
            "Vocabulary generation timed out.",
        )
    return _vocabulary_generation_http_error(
        500,
        "GENERATION_INTERNAL_ERROR",
        "Vocabulary generation failed.",
    )


def _image_recognition_http_error(status_code: int, code: str, message: str) -> HTTPException:
    return HTTPException(status_code=status_code, detail={"code": code, "message": message})


def _require_vocabulary_image_recognition_internal_token(
    authorization: Annotated[str | None, Header()] = None,
) -> None:
    expected_token = getattr(vocabulary_image_recognition_service, "internal_token", "")
    if not expected_token:
        raise _image_recognition_http_error(
            503,
            "IMAGE_RECOGNITION_NOT_CONFIGURED",
            "Vocabulary image recognition is unavailable.",
        )

    scheme, _, provided_token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not provided_token:
        raise _image_recognition_http_error(
            401,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )
    if not hmac.compare_digest(provided_token, expected_token):
        raise _image_recognition_http_error(
            403,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )


def _image_recognition_error_to_http_exception(
    error: VocabularyImageRecognitionError,
) -> HTTPException:
    if error.code in {
        "INVALID_IMAGE_REQUEST",
        "UNSUPPORTED_IMAGE_TYPE",
        "IMAGE_TOO_LARGE",
    }:
        return _image_recognition_http_error(
            400,
            error.code,
            "The vocabulary image recognition request is invalid.",
        )
    if error.code == "MODEL_OUTPUT_INVALID":
        return _image_recognition_http_error(
            502,
            error.code,
            "Vocabulary image recognition returned an invalid result.",
        )
    if error.code in {
        "IMAGE_RECOGNITION_NOT_CONFIGURED",
        "MODEL_UPSTREAM_UNAVAILABLE",
    }:
        return _image_recognition_http_error(
            503,
            error.code,
            "Vocabulary image recognition is unavailable.",
        )
    if error.code == "MODEL_TIMEOUT":
        return _image_recognition_http_error(
            504,
            error.code,
            "Vocabulary image recognition timed out.",
        )
    return _image_recognition_http_error(
        500,
        "IMAGE_RECOGNITION_INTERNAL_ERROR",
        "Vocabulary image recognition failed.",
    )


def _validate_image_upload(file_name: str, content_type: str, content: bytes) -> None:
    if not content:
        raise VocabularyImageRecognitionError("INVALID_IMAGE_REQUEST", False)
    if len(content) > MAX_IMAGE_BYTES:
        raise VocabularyImageRecognitionError("IMAGE_TOO_LARGE", False)

    extensions = {
        "image/jpeg": frozenset({".jpg", ".jpeg"}),
        "image/png": frozenset({".png"}),
        "image/webp": frozenset({".webp"}),
    }.get(content_type)
    if extensions is None or PurePath(file_name).suffix.casefold() not in extensions:
        raise VocabularyImageRecognitionError("UNSUPPORTED_IMAGE_TYPE", False)


def _require_vocabulary_import_analysis_internal_token(
    authorization: Annotated[str | None, Header()] = None,
) -> None:
    expected_token = getattr(vocabulary_import_analysis_service, "internal_token", "")
    if not expected_token:
        raise _image_recognition_http_error(
            503,
            "IMPORT_ANALYSIS_NOT_CONFIGURED",
            "Vocabulary import analysis is unavailable.",
        )
    scheme, _, provided_token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not provided_token:
        raise _image_recognition_http_error(
            401,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )
    if not hmac.compare_digest(provided_token, expected_token):
        raise _image_recognition_http_error(
            403,
            "INTERNAL_AUTH_FAILED",
            "Internal authentication failed.",
        )


def _import_analysis_error_to_http_exception(
    error: VocabularyImportAnalysisError,
) -> HTTPException:
    if error.code in {
        "INVALID_IMPORT_REQUEST",
        "UNSUPPORTED_IMAGE_TYPE",
        "IMAGE_TOO_LARGE",
    }:
        return _image_recognition_http_error(
            400,
            error.code,
            "The vocabulary import analysis request is invalid.",
        )
    if error.code == "MODEL_OUTPUT_INVALID":
        return _image_recognition_http_error(
            502,
            error.code,
            "Vocabulary import analysis returned an invalid result.",
        )
    if error.code in {
        "IMPORT_ANALYSIS_NOT_CONFIGURED",
        "MODEL_UPSTREAM_UNAVAILABLE",
    }:
        return _image_recognition_http_error(
            503,
            error.code,
            "Vocabulary import analysis is unavailable.",
        )
    if error.code == "MODEL_TIMEOUT":
        return _image_recognition_http_error(
            504,
            error.code,
            "Vocabulary import analysis timed out.",
        )
    return _image_recognition_http_error(
        500,
        "IMPORT_ANALYSIS_INTERNAL_ERROR",
        "Vocabulary import analysis failed.",
    )


def _validate_import_image_upload(
    file_name: str,
    content_type: str,
    content: bytes,
) -> None:
    try:
        _validate_image_upload(file_name, content_type, content)
    except VocabularyImageRecognitionError as exc:
        code = "INVALID_IMPORT_REQUEST" if exc.code == "INVALID_IMAGE_REQUEST" else exc.code
        raise VocabularyImportAnalysisError(code, exc.retryable) from None


@app.post(
    "/internal/v1/vocabulary/import-analyses",
    response_model=VocabularyImportAnalysisResponse,
    dependencies=[Depends(_require_vocabulary_import_analysis_internal_token)],
)
async def analyze_vocabulary_import(
    contract_version: Annotated[int, Form(alias="contractVersion")],
    trace_id: Annotated[str, Form(alias="traceId")],
    input_fingerprint: Annotated[str, Form(alias="inputFingerprint")],
    language: Annotated[str, Form()],
    text: Annotated[str, Form()] = "",
    file: Annotated[UploadFile | None, File()] = None,
) -> VocabularyImportAnalysisResponse:
    content = await file.read(MAX_IMAGE_BYTES + 1) if file is not None else None
    file_name = (file.filename or "image") if file is not None else None
    content_type = (file.content_type or "application/octet-stream") if file is not None else None
    try:
        if content is not None and file_name is not None and content_type is not None:
            _validate_import_image_upload(file_name, content_type, content)
        request = VocabularyImportAnalysisRequest(
            contractVersion=contract_version,
            traceId=trace_id,
            inputFingerprint=input_fingerprint,
            language=language,
            text=text,
            fileName=file_name,
            contentType=content_type,
            content=content,
        )
        result = await vocabulary_import_analysis_service.analyze(request)
        return VocabularyImportAnalysisResponse.model_validate(result)
    except ValidationError:
        raise _image_recognition_http_error(
            422,
            "INVALID_IMPORT_REQUEST",
            "The vocabulary import analysis request is invalid.",
        ) from None
    except VocabularyImportAnalysisError as exc:
        raise _import_analysis_error_to_http_exception(exc) from None
    except Exception:  # pragma: no cover - runtime safety
        log.warning(
            "Vocabulary import analysis endpoint failed",
            extra={
                "trace_id": trace_id,
                "text_length": len(text),
                "image_bytes": len(content or b""),
                "error_code": "IMPORT_ANALYSIS_INTERNAL_ERROR",
            },
        )
        raise _image_recognition_http_error(
            500,
            "IMPORT_ANALYSIS_INTERNAL_ERROR",
            "Vocabulary import analysis failed.",
        ) from None


@app.post(
    "/internal/v1/vocabulary/image-recognitions",
    response_model=VocabularyImageRecognitionResponse,
    dependencies=[Depends(_require_vocabulary_image_recognition_internal_token)],
)
async def recognize_vocabulary_image(
    contract_version: Annotated[int, Form(alias="contractVersion")],
    trace_id: Annotated[str, Form(alias="traceId")],
    language: Annotated[str, Form()],
    file: Annotated[UploadFile, File()],
) -> VocabularyImageRecognitionResponse:
    content = await file.read(MAX_IMAGE_BYTES + 1)
    file_name = file.filename or "image"
    content_type = file.content_type or "application/octet-stream"
    http_error: HTTPException | None = None
    try:
        _validate_image_upload(file_name, content_type, content)
        request = VocabularyImageRecognitionRequest(
            contractVersion=contract_version,
            traceId=trace_id,
            language=language,
            fileName=file_name,
            contentType=content_type,
            content=content,
        )
        result = await vocabulary_image_recognition_service.recognize(request)
        return VocabularyImageRecognitionResponse.model_validate(result)
    except ValidationError:
        http_error = _image_recognition_http_error(
            422,
            "INVALID_IMAGE_REQUEST",
            "The vocabulary image recognition request is invalid.",
        )
    except VocabularyImageRecognitionError as exc:
        http_error = _image_recognition_error_to_http_exception(exc)
    except Exception:  # pragma: no cover - runtime safety
        log.warning(
            "Vocabulary image recognition endpoint failed",
            extra={
                "trace_id": trace_id,
                "image_bytes": len(content),
                "error_code": "IMAGE_RECOGNITION_INTERNAL_ERROR",
            },
        )
        http_error = _image_recognition_http_error(
            500,
            "IMAGE_RECOGNITION_INTERNAL_ERROR",
            "Vocabulary image recognition failed.",
        )

    if http_error is None:  # pragma: no cover - try/except invariant
        http_error = _image_recognition_http_error(
            500,
            "IMAGE_RECOGNITION_INTERNAL_ERROR",
            "Vocabulary image recognition failed.",
        )
    raise http_error


@app.post(
    "/internal/v1/vocabulary/card-generations",
    response_model=VocabularyCardGenerationResponse,
    dependencies=[Depends(_require_vocabulary_generation_internal_token)],
)
async def generate_vocabulary_card(
    request: VocabularyCardGenerationRequest,
) -> VocabularyCardGenerationResponse:
    try:
        result = await vocabulary_card_generation_service.generate(request)
        return VocabularyCardGenerationResponse.model_validate(result, extra="ignore")
    except VocabularyCardGenerationError as exc:
        raise _vocabulary_generation_error_to_http_exception(exc) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise _vocabulary_generation_http_error(
            500,
            "GENERATION_INTERNAL_ERROR",
            "Vocabulary generation failed.",
        ) from exc


@app.post("/chat", response_model=ChatResponse)
async def chat(
    message: Annotated[str, Form()] = "",
    conversation_id: Annotated[str, Form()] = "",
    study_stage: Annotated[str, Form()] = "",
    assistant_mode: Annotated[str, Form()] = "",
    authorization: Annotated[str | None, Header()] = None,
    files: Annotated[list[UploadFile], File()] = [],
) -> ChatResponse:
    if not message.strip() and not files:
        raise HTTPException(status_code=400, detail="message 或 files 至少要提供一个。")
    if not conversation_id.strip():
        raise HTTPException(status_code=400, detail="conversation_id 不能为空。")

    attachments = []
    for upload in files:
        attachments.append(
            {
                "filename": upload.filename or "attachment",
                "content_type": upload.content_type or "application/octet-stream",
                "content": await upload.read(),
            }
        )

    try:
        result = await service.chat(
            message=message,
            conversation_id=conversation_id,
            attachments=attachments,
            study_stage=study_stage,
            assistant_mode=assistant_mode,
            authorization=authorization,
        )
    except (AssistantConfigError, RawAssistantConfigError) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise HTTPException(status_code=500, detail=f"assistant orchestrator failed: {exc}") from exc

    return ChatResponse(
        reply=result.reply,
        conversationId=conversation_id,
        agentName=result.agent_name,
    )


@app.post("/assistant/run", response_model=AssistantRunResponse)
async def assistant_run(
    request: AssistantRequest,
    authorization: Annotated[str | None, Header()] = None,
) -> AssistantRunResponse:
    try:
        result = await service.run_assistant_request(request, authorization=authorization)
    except AssistantRequestValidationError as exc:
        raise HTTPException(status_code=400, detail={"code": exc.code, "message": exc.message}) from exc
    except (AssistantConfigError, RawAssistantConfigError) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise HTTPException(status_code=500, detail=f"assistant run failed: {exc}") from exc

    if result.run is None:
        raise HTTPException(status_code=500, detail="assistant run metadata missing")

    return AssistantRunResponse(
        reply=result.reply,
        conversationId=request.app_conversation_id or request.client_message_id,
        agentName=result.agent_name,
        run=result.run,
        parts=result.parts,
    )


@app.post("/assistant/route/debug", response_model=RoutingDecision)
async def assistant_route_debug(
    request: AssistantRequest,
    authorization: Annotated[str | None, Header()] = None,
) -> RoutingDecision:
    try:
        return await service.route_assistant_request(request, authorization=authorization)
    except AssistantRequestValidationError as exc:
        raise HTTPException(status_code=400, detail={"code": exc.code, "message": exc.message}) from exc
    except AssistantConfigError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise HTTPException(status_code=500, detail=f"assistant route debug failed: {exc}") from exc


@app.post("/assistant/run/stream")
async def assistant_run_stream(
    request: AssistantRequest,
    authorization: Annotated[str | None, Header()] = None,
) -> StreamingResponse:
    async def event_stream():
        try:
            async for event in service.stream_assistant_request(request, authorization=authorization):
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        except AssistantRequestValidationError as exc:
            payload = {"type": "run.failed", "error": {"code": exc.code, "message": exc.message}}
            yield f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"
        except (AssistantConfigError, RawAssistantConfigError) as exc:
            payload = {"type": "run.failed", "error": {"code": "OPENAI_RUN_FAILED", "message": str(exc)}}
            yield f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"
        except Exception as exc:  # pragma: no cover - runtime safety
            payload = {"type": "run.failed", "error": {"code": "OPENAI_RUN_FAILED", "message": f"assistant run failed: {exc}"}}
            yield f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/prompt-sheet/chat", response_model=PromptSheetChatResponse)
async def prompt_sheet_chat(request: PromptSheetChatRequest) -> PromptSheetChatResponse:
    if not request.message.strip():
        raise HTTPException(status_code=400, detail="message 不能为空。")

    try:
        return await prompt_sheet_service.chat(request)
    except PromptSheetWorkflowConfigError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise HTTPException(status_code=500, detail=f"prompt sheet chat failed: {exc}") from exc


@app.post("/prompt-sheet/generate", response_model=GenerateExamPromptResponse)
async def prompt_sheet_generate(request: GenerateExamPromptRequest) -> GenerateExamPromptResponse:
    if not request.original_input.strip() or not request.topic.strip():
        raise HTTPException(status_code=400, detail="originalInput 和 topic 不能为空。")

    try:
        return await prompt_sheet_service.generate(request)
    except PromptSheetWorkflowConfigError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise HTTPException(status_code=500, detail=f"prompt sheet generation failed: {exc}") from exc


@app.post("/learning-assets/organize", response_model=LearningAssetOrganizeResponse)
async def learning_asset_organize(payload: Annotated[dict[str, Any], Body()]) -> LearningAssetOrganizeResponse:
    try:
        request = LearningAssetOrganizeRequest.model_validate(payload)
    except ValidationError as exc:
        raise HTTPException(status_code=400, detail=exc.errors()) from exc

    try:
        return await learning_asset_copilot_service.organize(request)
    except LearningAssetCopilotConfigError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise HTTPException(status_code=500, detail=f"learning asset copilot failed: {exc}") from exc
