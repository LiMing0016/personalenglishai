from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import urlparse, urlunparse

from dotenv import load_dotenv


def default_env_paths() -> list[Path]:
    repo_root = Path(__file__).resolve().parents[2]
    return [
        repo_root / ".env",
        repo_root / "backend" / ".env",
    ]


def load_orchestrator_env(env_path: str | Path | None = None) -> None:
    if env_path is not None:
        load_dotenv(dotenv_path=env_path, override=False)
        normalize_openai_base_url()
        return

    for path in default_env_paths():
        load_dotenv(dotenv_path=path, override=False)

    normalize_openai_base_url()


def normalize_openai_base_url() -> None:
    base_url = os.getenv("OPENAI_BASE_URL", "").strip()
    if not base_url:
        return

    parsed = urlparse(base_url)
    normalized_path = parsed.path.rstrip("/")
    if normalized_path.endswith("/v1"):
        return

    os.environ["OPENAI_BASE_URL"] = urlunparse(parsed._replace(path=f"{normalized_path}/v1"))
