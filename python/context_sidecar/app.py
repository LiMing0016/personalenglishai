import json
import os
import time
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from redis import Redis
from redis.exceptions import RedisError
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


REDIS_URL = os.getenv("CONTEXT_SIDECAR_REDIS_URL", "redis://127.0.0.1:6379/0")
REDIS_TTL_SECONDS = _env_int("CONTEXT_SIDECAR_REDIS_TTL_SECONDS", 86400)
REDIS_MAX_MESSAGES = _env_int("CONTEXT_SIDECAR_REDIS_MAX_MESSAGES", 50)
DEFAULT_RECENT_TURNS = _env_int("CONTEXT_SIDECAR_DEFAULT_RECENT_TURNS", 8)

LOW_SIGNAL_WORDS = {
    "ok",
    "okay",
    "thanks",
    "thank you",
    "\u597d\u7684",
    "\u8c22\u8c22",
    "\u55ef",
    "\u6536\u5230",
}

redis_client = Redis.from_url(REDIS_URL, decode_responses=True)
app = FastAPI(title="Context Sidecar", version="0.1.0")


class MessageDto(BaseModel):
    role: str = Field(default="User")
    content: str


class ProcessRequest(BaseModel):
    recentTurns: Optional[int] = None
    conversationId: Optional[str] = None
    traceId: Optional[str] = None
    messages: List[MessageDto] = Field(default_factory=list)


class ProcessResponse(BaseModel):
    messages: List[MessageDto]
    processorName: str = "langchain-python-redis"
    summaryUsed: bool = False


class AppendRequest(BaseModel):
    conversationId: str
    traceId: Optional[str] = None
    messages: List[MessageDto] = Field(default_factory=list)


def _redis_key(conversation_id: str) -> str:
    return f"ai:chat:conv:{conversation_id}"


def _to_langchain_message(message: MessageDto) -> Optional[BaseMessage]:
    content = (message.content or "").strip()
    if not content:
        return None
    role = (message.role or "User").strip().lower()
    if "assistant" in role or role == "ai":
        return AIMessage(content=content)
    return HumanMessage(content=content)


def _to_dto(message: BaseMessage) -> Optional[MessageDto]:
    content = str(message.content).strip()
    if not content:
        return None
    role = "Assistant" if isinstance(message, AIMessage) else "User"
    return MessageDto(role=role, content=content)


def _is_low_signal(text: str) -> bool:
    t = (text or "").strip().lower()
    if not t:
        return True
    if len(t) <= 2:
        return True
    return t in LOW_SIGNAL_WORDS


def _normalize_and_filter(messages: List[MessageDto], recent_turns: int) -> List[MessageDto]:
    lc_messages: List[BaseMessage] = []
    for dto in messages:
        msg = _to_langchain_message(dto)
        if msg is not None:
            lc_messages.append(msg)

    out: List[MessageDto] = []
    prev: Optional[MessageDto] = None
    for msg in lc_messages:
        dto = _to_dto(msg)
        if dto is None or _is_low_signal(dto.content):
            continue
        if prev and prev.role == dto.role and prev.content == dto.content:
            continue
        out.append(dto)
        prev = dto

    if recent_turns <= 0:
        recent_turns = DEFAULT_RECENT_TURNS
    return out[-recent_turns:]


def _load_redis_messages(conversation_id: Optional[str]) -> List[MessageDto]:
    if not conversation_id:
        return []
    try:
        values = redis_client.lrange(_redis_key(conversation_id), 0, -1)
    except RedisError as e:
        raise HTTPException(status_code=503, detail=f"redis read failed: {e}") from e

    out: List[MessageDto] = []
    for value in values:
        try:
            obj = json.loads(value)
            content = str(obj.get("content", "")).strip()
            if not content:
                continue
            role = str(obj.get("role", "User")).strip() or "User"
            out.append(MessageDto(role=role, content=content))
        except Exception:
            continue
    return out


def _append_redis_messages(conversation_id: str, messages: List[MessageDto]) -> int:
    if not conversation_id or not messages:
        return 0
    key = _redis_key(conversation_id)
    payloads = [
        json.dumps(
            {"role": m.role, "content": m.content, "ts": int(time.time() * 1000)},
            ensure_ascii=False,
        )
        for m in messages
        if (m.content or "").strip()
    ]
    if not payloads:
        return 0

    try:
        pipe = redis_client.pipeline()
        pipe.rpush(key, *payloads)
        pipe.ltrim(key, -REDIS_MAX_MESSAGES, -1)
        pipe.expire(key, REDIS_TTL_SECONDS)
        pipe.execute()
    except RedisError as e:
        raise HTTPException(status_code=503, detail=f"redis write failed: {e}") from e
    return len(payloads)


@app.get("/health")
def health():
    try:
        redis_client.ping()
        redis_ok = True
    except Exception:
        redis_ok = False
    return {"ok": True, "redisOk": redis_ok, "processor": "langchain-python-redis"}


@app.post("/context/conversation/process", response_model=ProcessResponse)
def process_conversation(req: ProcessRequest):
    redis_messages = _load_redis_messages(req.conversationId)
    combined = [*redis_messages, *req.messages]
    recent_turns = req.recentTurns or DEFAULT_RECENT_TURNS
    processed = _normalize_and_filter(combined, recent_turns)
    return ProcessResponse(messages=processed, summaryUsed=False)


@app.post("/context/conversation/append")
def append_conversation(req: AppendRequest):
    count = _append_redis_messages(req.conversationId, req.messages)
    return {"ok": True, "appended": count, "processor": "langchain-python-redis"}
