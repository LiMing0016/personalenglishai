from __future__ import annotations

import re
import zipfile
from html import unescape
from pathlib import Path
from xml.etree import ElementTree

from python.ai_orchestrator.schemas.dictionary_cleaning import DictionaryExample


CELL_REF = re.compile(r"([A-Z]+)(\d+)")
WORD_HEADERS = {"word", "headword", "词", "单词", "词条"}
EN_HEADERS = {"english", "example", "sentence", "英文", "英文例句", "例句"}
ZH_HEADERS = {"chinese", "translation", "中文", "中文例句", "译文", "释义"}


def read_dictionary_examples_xlsx(path: str | Path) -> list[DictionaryExample]:
    workbook_path = Path(path)
    with zipfile.ZipFile(workbook_path, "r") as workbook:
        shared_strings = read_shared_strings(workbook)
        sheet_xml = workbook.read("xl/worksheets/sheet1.xml")

    rows = parse_sheet_rows(sheet_xml, shared_strings)
    if not rows:
        return []

    headers = [normalize_header(value) for value in rows[0]]
    word_index = find_header(headers, WORD_HEADERS)
    english_index = find_header(headers, EN_HEADERS)
    chinese_index = find_header(headers, ZH_HEADERS)
    if english_index is None:
        raise ValueError("dictionary examples xlsx must include english/example columns")

    examples: list[DictionaryExample] = []
    for row in rows[1:]:
        headword = value_at(row, word_index) if word_index is not None else None
        text_en = value_at(row, english_index)
        text_zh = value_at(row, chinese_index) if chinese_index is not None else None
        if not text_en:
            continue
        examples.append(DictionaryExample(headword=headword, text_en=text_en, text_zh=text_zh, source="examples_xlsx"))
    return examples


def read_shared_strings(workbook: zipfile.ZipFile) -> list[str]:
    try:
        xml = workbook.read("xl/sharedStrings.xml")
    except KeyError:
        return []

    root = ElementTree.fromstring(xml)
    values: list[str] = []
    for item in root.iter():
        if strip_namespace(item.tag) != "si":
            continue
        parts = [node.text or "" for node in item.iter() if strip_namespace(node.tag) == "t"]
        values.append(unescape("".join(parts)))
    return values


def parse_sheet_rows(sheet_xml: bytes, shared_strings: list[str]) -> list[list[str]]:
    root = ElementTree.fromstring(sheet_xml)
    row_values: dict[int, dict[int, str]] = {}
    for cell in root.iter():
        if strip_namespace(cell.tag) != "c":
            continue
        ref = cell.attrib.get("r", "")
        match = CELL_REF.match(ref)
        if not match:
            continue
        column = column_to_number(match.group(1)) - 1
        row_number = int(match.group(2)) - 1
        cell_type = cell.attrib.get("t")
        raw_value = ""
        for child in cell:
            child_tag = strip_namespace(child.tag)
            if child_tag == "v":
                raw_value = child.text or ""
                break
            if child_tag == "is":
                raw_value = "".join(text_node.text or "" for text_node in child.iter() if strip_namespace(text_node.tag) == "t")
                break
        value = resolve_cell_value(raw_value, cell_type, shared_strings)
        row_values.setdefault(row_number, {})[column] = value

    rows: list[list[str]] = []
    for row_number in sorted(row_values):
        columns = row_values[row_number]
        max_column = max(columns) if columns else -1
        rows.append([columns.get(index, "") for index in range(max_column + 1)])
    return rows


def resolve_cell_value(raw_value: str, cell_type: str | None, shared_strings: list[str]) -> str:
    if cell_type == "s":
        try:
            return shared_strings[int(raw_value)]
        except (ValueError, IndexError):
            return raw_value
    return unescape(raw_value or "").strip()


def find_header(headers: list[str], aliases: set[str]) -> int | None:
    for index, header in enumerate(headers):
        if header in aliases:
            return index
    return None


def value_at(row: list[str], index: int) -> str:
    return row[index].strip() if index < len(row) else ""


def normalize_header(value: str) -> str:
    return re.sub(r"\s+", "", value or "").strip().lower()


def strip_namespace(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def column_to_number(letters: str) -> int:
    value = 0
    for char in letters:
        value = value * 26 + ord(char.upper()) - ord("A") + 1
    return value
