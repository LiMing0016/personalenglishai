from __future__ import annotations

from collections import defaultdict
from pathlib import Path

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


def merge_external_examples(entries: list[DictionaryCleanEntry], examples: list[DictionaryExample]) -> None:
    examples_by_word: dict[str, list[DictionaryExample]] = defaultdict(list)
    for example in examples:
        if example.headword:
            examples_by_word[example.headword.lower()].append(example)

    for entry in entries:
        entry_examples = examples_by_word.get(entry.word.lower(), [])
        if not entry_examples:
            continue
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
