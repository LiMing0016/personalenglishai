from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from python.ai_orchestrator.schemas.dictionary_cleaning import DictionaryRawEntry
from python.ai_orchestrator.workflows.dictionary_cleaning.workflow import (
    run_dictionary_cleaning_batch_workflow,
    run_dictionary_cleaning_workflow,
)


def run_dictionary_cleaning_cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run dictionary cleaning workflow for Java import worker.")
    parser.add_argument("--input", required=True, help="JSON request path")
    parser.add_argument("--output", required=True, help="JSON result path")
    args = parser.parse_args(argv)

    input_path = Path(args.input)
    output_path = Path(args.output)
    request = json.loads(input_path.read_text(encoding="utf-8"))

    raw_entries = [
        DictionaryRawEntry(
            headword=str(item.get("headword") or ""),
            html=str(item.get("html") or ""),
            source_entry_id=item.get("sourceEntryId") or item.get("source_entry_id"),
        )
        for item in request.get("rawEntries", [])
        if isinstance(item, dict)
    ]
    limit = request.get("limit")
    batch_size = request.get("entryBatchSize")
    if isinstance(batch_size, int) and batch_size > 0:
        result_payload = run_dictionary_cleaning_batch_workflow(
            source_code=str(request.get("sourceCode") or ""),
            display_name=str(request.get("displayName") or ""),
            output_dir=output_path.parent / f"{output_path.stem}-entries",
            raw_entries=raw_entries,
            mdx_path=request.get("mdxPath"),
            mdx_limit=int(limit) if isinstance(limit, int) and limit > 0 else None,
            mdd_path=request.get("mddPath"),
            mdd_limit=int(limit) if isinstance(limit, int) and limit > 0 else None,
            examples_path=request.get("examplesPath"),
            batch_size=batch_size,
        )
    else:
        result = run_dictionary_cleaning_workflow(
            source_code=str(request.get("sourceCode") or ""),
            display_name=str(request.get("displayName") or ""),
            raw_entries=raw_entries,
            mdx_path=request.get("mdxPath"),
            mdx_limit=int(limit) if isinstance(limit, int) and limit > 0 else None,
            mdd_path=request.get("mddPath"),
            mdd_limit=int(limit) if isinstance(limit, int) and limit > 0 else None,
            examples_path=request.get("examplesPath"),
        )
        result_payload = _model_to_dict(result)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result_payload, ensure_ascii=False, default=str), encoding="utf-8")
    return 0


def _model_to_dict(value: Any) -> dict[str, Any]:
    if hasattr(value, "model_dump"):
        return value.model_dump(mode="json")
    return value.dict()


if __name__ == "__main__":
    raise SystemExit(run_dictionary_cleaning_cli())
