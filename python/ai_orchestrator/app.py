from __future__ import annotations

from typing import Annotated

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

try:
    from .assistant_service import AssistantAgentService, AssistantConfigError
    from .env_loader import load_orchestrator_env
    from .schemas.chat import ChatResponse
    from .schemas.prompt_sheet import GenerateExamPromptRequest
    from .schemas.prompt_sheet import GenerateExamPromptResponse
    from .schemas.prompt_sheet import PromptSheetChatRequest
    from .schemas.prompt_sheet import PromptSheetChatResponse
    from .services.prompt_sheet_workflow import PromptSheetWorkflowConfigError
    from .services.prompt_sheet_workflow import PromptSheetWorkflowService
except ImportError:  # pragma: no cover - script mode fallback
    from assistant_service import AssistantAgentService, AssistantConfigError
    from env_loader import load_orchestrator_env
    from schemas.chat import ChatResponse
    from schemas.prompt_sheet import GenerateExamPromptRequest
    from schemas.prompt_sheet import GenerateExamPromptResponse
    from schemas.prompt_sheet import PromptSheetChatRequest
    from schemas.prompt_sheet import PromptSheetChatResponse
    from services.prompt_sheet_workflow import PromptSheetWorkflowConfigError
    from services.prompt_sheet_workflow import PromptSheetWorkflowService


load_orchestrator_env()

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
service = AssistantAgentService.from_env()
prompt_sheet_service = PromptSheetWorkflowService.from_env()


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "ok": True,
        "configured": service.is_configured(),
        "promptSheetConfigured": prompt_sheet_service.is_configured(),
        "model": service.model,
    }


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
    except AssistantConfigError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - runtime safety
        raise HTTPException(status_code=500, detail=f"assistant orchestrator failed: {exc}") from exc

    return ChatResponse(
        reply=result.reply,
        conversationId=conversation_id,
        agentName=result.agent_name,
    )


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
