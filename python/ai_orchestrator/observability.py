from __future__ import annotations

from dataclasses import dataclass
import logging
import os
from typing import Callable, Type


log = logging.getLogger("uvicorn.error")

_TRUE_VALUES = {"1", "true", "yes", "on"}
_langfuse_configured = False


@dataclass(frozen=True, slots=True)
class LangfuseTracingStatus:
    enabled: bool
    configured: bool
    reason: str = ""


def configure_observability() -> LangfuseTracingStatus:
    return configure_langfuse_tracing()


def configure_langfuse_tracing() -> LangfuseTracingStatus:
    global _langfuse_configured

    if not _env_enabled("LANGFUSE_ENABLED"):
        return LangfuseTracingStatus(enabled=False, configured=False, reason="disabled")

    _normalize_langfuse_base_url()
    missing = _missing_langfuse_settings()
    if missing:
        reason = f"missing {', '.join(missing)}"
        log.warning("Langfuse tracing enabled but not configured: %s", reason)
        return LangfuseTracingStatus(enabled=True, configured=False, reason=reason)

    if _langfuse_configured:
        return LangfuseTracingStatus(enabled=True, configured=True, reason="already_configured")

    try:
        instrumentor_cls, _get_client = _load_langfuse_dependencies()
        instrumentor_cls().instrument()
    except Exception as exc:
        log.warning("Langfuse tracing setup failed: %s", exc, exc_info=True)
        return LangfuseTracingStatus(enabled=True, configured=False, reason=exc.__class__.__name__)

    _langfuse_configured = True
    log.info("Langfuse tracing enabled base_url=%s", os.getenv("LANGFUSE_BASE_URL", ""))
    return LangfuseTracingStatus(enabled=True, configured=True, reason="configured")


def flush_observability() -> None:
    if not _langfuse_configured:
        return

    try:
        _instrumentor_cls, get_client = _load_langfuse_dependencies()
        langfuse = get_client()
        flush = getattr(langfuse, "flush", None)
        if callable(flush):
            flush()
    except Exception:
        log.warning("Langfuse trace flush failed", exc_info=True)


def _env_enabled(name: str) -> bool:
    return os.getenv(name, "").strip().lower() in _TRUE_VALUES


def _normalize_langfuse_base_url() -> None:
    if os.getenv("LANGFUSE_BASE_URL", "").strip():
        return

    host = os.getenv("LANGFUSE_HOST", "").strip()
    if host:
        os.environ["LANGFUSE_BASE_URL"] = host


def _missing_langfuse_settings() -> list[str]:
    required = ("LANGFUSE_PUBLIC_KEY", "LANGFUSE_SECRET_KEY", "LANGFUSE_BASE_URL")
    return [name for name in required if not os.getenv(name, "").strip()]


def _load_langfuse_dependencies() -> tuple[Type[object], Callable[[], object]]:
    from langfuse import get_client
    from openinference.instrumentation.openai_agents import OpenAIAgentsInstrumentor

    return OpenAIAgentsInstrumentor, get_client
