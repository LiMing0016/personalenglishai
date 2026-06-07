from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from python.ai_orchestrator.schemas.dictionary_cleaning import (
    DictionaryCleanEntry,
    DictionaryCleaningResult,
    DictionaryCleaningSummary,
    DictionaryExample,
    DictionaryRawEntry,
    DictionarySense,
)
from python.ai_orchestrator.tools.dictionary.entry_parser import parse_dictionary_entry_html
from python.ai_orchestrator.tools.dictionary.mdict_reader import iter_mdd_raw_resources, iter_mdx_raw_entries
from python.ai_orchestrator.tools.dictionary.xlsx_examples_reader import read_dictionary_examples_xlsx


def run_dictionary_cleaning_workflow(
    *,
    source_code: str,
    display_name: str,
    raw_entries: list[DictionaryRawEntry] | None = None,
    mdx_path: str | Path | None = None,
    mdx_limit: int | None = None,
    mdd_path: str | Path | None = None,
    mdd_limit: int | None = None,
    examples_path: str | Path | None = None,
) -> DictionaryCleaningResult:
    warnings: list[str] = []
    entries: list[DictionaryCleanEntry] = []
    source_entries = list(raw_entries or [])
    if mdx_path:
        try:
            source_entries.extend(iter_mdx_raw_entries(mdx_path, limit=mdx_limit))
        except Exception as exc:
            warnings.append(f"failed to read mdx entries: {exc}")

    for raw_entry in source_entries:
        try:
            entries.append(
                parse_dictionary_entry_html(
                    raw_entry.headword,
                    raw_entry.html,
                    source_entry_id=raw_entry.source_entry_id,
                )
            )
        except Exception as exc:
            warnings.append(f"failed to parse entry {raw_entry.headword}: {exc}")

    external_examples: list[DictionaryExample] = []
    if examples_path:
        try:
            external_examples = read_dictionary_examples_xlsx(examples_path)
        except Exception as exc:
            warnings.append(f"failed to read examples xlsx: {exc}")

    merge_external_examples(entries, external_examples)

    resources: list[dict[str, object]] = []
    if mdd_path:
        try:
            resources = list(iter_mdd_raw_resources(mdd_path, limit=mdd_limit))
        except Exception as exc:
            warnings.append(f"failed to read mdd resources: {exc}")

    summary = DictionaryCleaningSummary(
        entry_count=len(entries),
        sense_count=sum(len(entry.senses) for entry in entries),
        example_count=sum(len(sense.examples) for entry in entries for sense in entry.senses),
        phrase_count=sum(len(entry.phrases) for entry in entries),
        warning_count=len(warnings),
    )

    return DictionaryCleaningResult(
        status="completed_with_warnings" if warnings else "completed",
        source_code=source_code,
        display_name=display_name,
        summary=summary,
        entries=entries,
        resources=resources,
        warnings=warnings,
    )


def run_dictionary_cleaning_batch_workflow(
    *,
    source_code: str,
    display_name: str,
    output_dir: str | Path,
    raw_entries: list[DictionaryRawEntry] | None = None,
    mdx_path: str | Path | None = None,
    mdx_limit: int | None = None,
    mdd_path: str | Path | None = None,
    mdd_limit: int | None = None,
    examples_path: str | Path | None = None,
    batch_size: int = 500,
) -> dict[str, Any]:
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    warnings: list[str] = []
    external_examples: list[DictionaryExample] = []
    if examples_path:
        try:
            external_examples = read_dictionary_examples_xlsx(examples_path)
        except Exception as exc:
            warnings.append(f"failed to read examples xlsx: {exc}")
    examples_by_word = build_examples_by_word(external_examples)

    entry_count = 0
    sense_count = 0
    example_count = 0
    phrase_count = 0
    batch: list[dict[str, Any]] = []
    batch_paths: list[str] = []
    batch_index = 1

    def flush_batch() -> None:
        nonlocal batch, batch_index
        if not batch:
            return
        batch_path = output_path / f"entries-{batch_index:05d}.json"
        batch_path.write_text(json.dumps(batch, ensure_ascii=False, default=str), encoding="utf-8")
        batch_paths.append(str(batch_path))
        batch = []
        batch_index += 1

    def source_iterator():
        for item in raw_entries or []:
            yield item
        if mdx_path:
            yield from iter_mdx_raw_entries(mdx_path, limit=mdx_limit)

    try:
        for raw_entry in source_iterator():
            try:
                entry = parse_dictionary_entry_html(
                    raw_entry.headword,
                    raw_entry.html,
                    source_entry_id=raw_entry.source_entry_id,
                )
                merge_examples_into_entry(entry, examples_by_word.get(entry.word.lower(), []))
                entry_count += 1
                sense_count += len(entry.senses)
                example_count += sum(len(sense.examples) for sense in entry.senses)
                phrase_count += len(entry.phrases)
                batch.append(model_to_dict(entry))
                if len(batch) >= max(batch_size, 1):
                    flush_batch()
            except Exception as exc:
                warnings.append(f"failed to parse entry {raw_entry.headword}: {exc}")
    except Exception as exc:
        warnings.append(f"failed to read mdx entries: {exc}")
    flush_batch()

    resources: list[dict[str, object]] = []
    if mdd_path:
        try:
            resources = list(iter_mdd_raw_resources(mdd_path, limit=mdd_limit))
        except Exception as exc:
            warnings.append(f"failed to read mdd resources: {exc}")

    return {
        "workflow": "dictionary_cleaning",
        "status": "completed_with_warnings" if warnings else "completed",
        "source_code": source_code,
        "display_name": display_name,
        "summary": {
            "entry_count": entry_count,
            "sense_count": sense_count,
            "example_count": example_count,
            "phrase_count": phrase_count,
            "warning_count": len(warnings),
        },
        "entries": [],
        "entryBatchPaths": batch_paths,
        "resources": resources,
        "warnings": warnings,
    }


def merge_external_examples(entries: list[DictionaryCleanEntry], examples: list[DictionaryExample]) -> None:
    examples_by_word = build_examples_by_word(examples)

    for entry in entries:
        merge_examples_into_entry(entry, examples_by_word.get(entry.word.lower(), []))


def build_examples_by_word(examples: list[DictionaryExample]) -> dict[str, list[DictionaryExample]]:
    examples_by_word: dict[str, list[DictionaryExample]] = defaultdict(list)
    for example in examples:
        if example.headword:
            examples_by_word[example.headword.lower()].append(example)
    return examples_by_word


def merge_examples_into_entry(entry: DictionaryCleanEntry, entry_examples: list[DictionaryExample]) -> None:
    if not entry_examples:
        return
    if not entry.senses:
        entry.senses.append(DictionarySense())
    existing = {
        (example.text_en, example.text_zh)
        for sense in entry.senses
        for example in sense.examples
    }
    for example in entry_examples:
        key = (example.text_en, example.text_zh)
        if key in existing:
            continue
        entry.senses[0].examples.append(example)
        existing.add(key)


def model_to_dict(value: Any) -> dict[str, Any]:
    if hasattr(value, "model_dump"):
        return value.model_dump(mode="json")
    return value.dict()
