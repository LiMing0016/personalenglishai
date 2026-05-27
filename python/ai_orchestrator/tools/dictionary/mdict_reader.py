from __future__ import annotations

from collections.abc import Callable, Iterable, Iterator
from pathlib import Path
from typing import Any

from python.ai_orchestrator.schemas.dictionary_cleaning import DictionaryRawEntry

MdxRecord = tuple[str | bytes, str | bytes]
MdxReaderFactory = Callable[[Path], Iterable[MdxRecord]]


def iter_mdx_raw_entries(
    path: str | Path,
    *,
    limit: int | None = None,
    reader_factory: MdxReaderFactory | None = None,
) -> Iterator[DictionaryRawEntry]:
    mdx_path = Path(path)
    reader = reader_factory or default_readmdict_reader
    count = 0
    for key, value in reader(mdx_path):
        headword = decode_mdict_value(key).strip()
        html = decode_mdict_value(value).strip()
        if not headword or not html:
            continue
        yield DictionaryRawEntry(headword=headword, html=html, source_entry_id=headword)
        count += 1
        if limit is not None and count >= limit:
            break


def default_readmdict_reader(path: Path) -> Iterable[MdxRecord]:
    try:
        from readmdict import MDX  # type: ignore
    except ImportError as exc:
        raise RuntimeError(
            "MDX parsing requires the optional readmdict package. "
            "Install readmdict in the Python worker environment before running dictionary import."
        ) from exc
    return MDX(str(path)).items()  # type: ignore[no-any-return]


def iter_mdd_raw_resources(
    path: str | Path,
    *,
    limit: int | None = None,
    reader_factory: MdxReaderFactory | None = None,
) -> Iterator[dict[str, object]]:
    mdd_path = Path(path)
    reader = reader_factory or default_readmdd_reader
    count = 0
    for key, value in reader(mdd_path):
        resource_key = decode_mdict_value(key).strip()
        payload = value if isinstance(value, bytes) else str(value).encode("utf-8")
        if not resource_key:
            continue
        file_name = resource_key.replace("\\", "/").split("/")[-1] or resource_key
        yield {
            "resource_key": resource_key,
            "resource_type": resource_type_for_key(resource_key),
            "file_name": file_name,
            "storage_path": resource_key,
            "size_bytes": len(payload),
        }
        count += 1
        if limit is not None and count >= limit:
            break


def default_readmdd_reader(path: Path) -> Iterable[MdxRecord]:
    try:
        from readmdict import MDD  # type: ignore
    except ImportError as exc:
        raise RuntimeError(
            "MDD parsing requires the optional readmdict package. "
            "Install readmdict in the Python worker environment before running dictionary import."
        ) from exc
    return MDD(str(path)).items()  # type: ignore[no-any-return]


def resource_type_for_key(resource_key: str) -> str:
    lower = resource_key.lower()
    if lower.endswith((".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg")):
        return "image"
    if lower.endswith((".mp3", ".wav", ".ogg", ".m4a")):
        return "audio"
    if lower.endswith(".css"):
        return "css"
    return "other"


def decode_mdict_value(value: Any) -> str:
    if isinstance(value, bytes):
        for encoding in ("utf-8", "utf-16", "gb18030"):
            try:
                return value.decode(encoding)
            except UnicodeDecodeError:
                continue
        return value.decode("utf-8", errors="ignore")
    return str(value)
