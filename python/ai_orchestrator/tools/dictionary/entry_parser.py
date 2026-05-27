from __future__ import annotations

import re
from html import unescape
from html.parser import HTMLParser

from python.ai_orchestrator.schemas.dictionary_cleaning import (
    DictionaryCleanEntry,
    DictionaryExample,
    DictionaryPhrase,
    DictionaryPhonetic,
    DictionarySense,
)


SCRIPT_OR_STYLE = re.compile(r"<(script|style)\b[^>]*>.*?</\1>", re.IGNORECASE | re.DOTALL)
TAG = re.compile(r"<[^>]+>")
WHITESPACE = re.compile(r"\s+")


def parse_dictionary_entry_html(headword: str, html: str, source_entry_id: str | None = None) -> DictionaryCleanEntry:
    normalized_html = SCRIPT_OR_STYLE.sub(" ", html or "")
    clean_text = normalize_text(TAG.sub(" ", normalized_html))
    word = first_text_for_tags(normalized_html, ["h", "h1", "h2"]) or normalize_text(headword)
    part_of_speech = first_text_for_tags(normalized_html, ["pos"]) or first_text_by_class(normalized_html, ["pos", "part-of-speech"])
    phonetic_texts = texts_for_tags(normalized_html, ["phon"]) or texts_by_class(normalized_html, ["phon", "phonetic", "pron"])
    phonetics = [DictionaryPhonetic(text=text) for text in unique_nonblank(phonetic_texts)]
    senses = parse_senses(normalized_html, word)
    phrases = parse_phrases(normalized_html, word)
    usage_notes = parse_usage_notes(normalized_html)

    return DictionaryCleanEntry(
        word=word,
        part_of_speech=part_of_speech,
        phonetics=phonetics,
        senses=senses,
        phrases=phrases,
        usage_notes=usage_notes,
        clean_text=clean_text,
        source_entry_id=source_entry_id,
    )


def parse_senses(html: str, headword: str) -> list[DictionarySense]:
    blocks = element_blocks_for_tags(html, ["sn-g"]) or element_blocks_by_class(html, ["sense", "sn-g", "sense-g"])
    if not blocks:
        definition_en = first_definition_en(html) or first_text_by_class(html, ["def", "definition"])
        definition_zh = first_definition_zh(html) or first_text_by_class(html, ["zh", "chn", "translation"])
        examples = parse_examples(html, headword)
        if definition_en or definition_zh or examples:
            return [DictionarySense(definition_en=definition_en, definition_zh=definition_zh, examples=examples)]
        return []

    senses: list[DictionarySense] = []
    for block in blocks:
        senses.append(
            DictionarySense(
                definition_en=first_definition_en(block) or first_text_by_class(block, ["def", "definition"]),
                definition_zh=first_definition_zh(block) or first_text_by_class(block, ["zh", "chn", "translation"]),
                examples=parse_examples(block, headword),
            )
        )
    return senses


def parse_phrases(html: str, headword: str) -> list[DictionaryPhrase]:
    blocks = element_blocks_for_tags(html, ["idm-g", "pv-g"]) or element_blocks_by_class(html, ["idm-g", "phrase-g", "phr-g", "colloc-g"])
    phrases: list[DictionaryPhrase] = []
    for block in blocks:
        text = first_text_for_tags(block, ["idm", "pv"]) or first_text_by_class(block, ["phrase", "idm", "colloc"]) or first_text_for_tags(block, ["h3", "h4"])
        if not text:
            continue
        phrases.append(
            DictionaryPhrase(
                text=text,
                definition_en=first_definition_en(block) or first_text_by_class(block, ["def", "definition"]),
                definition_zh=first_definition_zh(block) or first_text_by_class(block, ["zh", "chn", "translation"]),
                examples=parse_examples(block, headword),
            )
        )
    return phrases


def parse_examples(html: str, headword: str) -> list[DictionaryExample]:
    custom_blocks = element_blocks_for_tags(html, ["x"])
    if custom_blocks:
        examples: list[DictionaryExample] = []
        for block in custom_blocks:
            text_en = text_without_child_tags(block, ["chn"])
            text_zh = first_text_for_tags(block, ["chn"])
            if text_en:
                examples.append(DictionaryExample(headword=headword, text_en=text_en, text_zh=text_zh, source="entry_html"))
        return examples

    english = texts_by_class(html, ["x", "example", "ex"])
    chinese = texts_by_class(html, ["xt", "example-zh", "translation"])
    examples: list[DictionaryExample] = []
    for index, text_en in enumerate(english):
        text_zh = chinese[index] if index < len(chinese) else None
        examples.append(DictionaryExample(headword=headword, text_en=text_en, text_zh=text_zh, source="entry_html"))
    return examples


def parse_usage_notes(html: str) -> list[str]:
    notes: list[str] = []
    for block in element_blocks_for_tags(html, ["unbox"]):
        text = normalize_text(TAG.sub(" ", block))
        if text:
            notes.append(text)
    return unique_nonblank(notes)


def first_definition_en(html: str) -> str | None:
    for block in element_blocks_for_tags(html, ["def"]):
        text = text_without_child_tags(block, ["chn"])
        if text:
            return text
    return None


def first_definition_zh(html: str) -> str | None:
    for block in element_blocks_for_tags(html, ["def"]):
        text = first_text_for_tags(block, ["chn"])
        if text:
            return text
    return None


def first_text_by_class(html: str, class_names: list[str]) -> str | None:
    values = texts_by_class(html, class_names)
    return values[0] if values else None


def texts_by_class(html: str, class_names: list[str]) -> list[str]:
    values: list[str] = []
    for block in element_blocks_by_class(html, class_names):
        value = normalize_text(TAG.sub(" ", block))
        if value:
            values.append(value)
    return values


def texts_for_tags(html: str, tag_names: list[str]) -> list[str]:
    values: list[str] = []
    for tag_name in tag_names:
        for block in element_blocks_for_tags(html, [tag_name]):
            value = normalize_text(TAG.sub(" ", block))
            if value:
                values.append(value)
    return values


def first_text_for_tags(html: str, tag_names: list[str]) -> str | None:
    for tag_name in tag_names:
        match = re.search(rf"<{re.escape(tag_name)}(?=[\s>/])[^>]*>(.*?)</{re.escape(tag_name)}>", html, re.IGNORECASE | re.DOTALL)
        if match:
            value = normalize_text(TAG.sub(" ", match.group(1)))
            if value:
                return value
    return None


def element_blocks_for_tags(html: str, tag_names: list[str]) -> list[str]:
    blocks: list[str] = []
    for tag_name in tag_names:
        pattern = re.compile(rf"<{re.escape(tag_name)}(?=[\s>/])[^>]*>.*?</{re.escape(tag_name)}>", re.IGNORECASE | re.DOTALL)
        blocks.extend(match.group(0) for match in pattern.finditer(html or ""))
    return blocks


def text_without_child_tags(html: str, child_tags: list[str]) -> str:
    text = html or ""
    for tag_name in child_tags:
        text = re.sub(rf"<{re.escape(tag_name)}(?=[\s>/])[^>]*>.*?</{re.escape(tag_name)}>", " ", text, flags=re.IGNORECASE | re.DOTALL)
    text = re.sub(r"<xhtml:br(?=[\s>/])[^>]*>.*?</xhtml:br>|<xhtml:br(?=[\s>/])[^>]*/?>", " ", text, flags=re.IGNORECASE | re.DOTALL)
    text = re.sub(r"<chnsep(?=[\s>/])[^>]*>.*?</chnsep>", " ", text, flags=re.IGNORECASE | re.DOTALL)
    return normalize_text(TAG.sub(" ", text))


def element_blocks_by_class(html: str, class_names: list[str]) -> list[str]:
    parser = ClassBlockParser(class_names)
    parser.feed(html or "")
    parser.close()
    return parser.blocks


def normalize_text(value: str | None) -> str:
    return WHITESPACE.sub(" ", unescape(value or "")).strip()


def unique_nonblank(values: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for value in values:
        normalized = normalize_text(value)
        if normalized and normalized not in seen:
            seen.add(normalized)
            out.append(normalized)
    return out


class ClassBlockParser(HTMLParser):
    def __init__(self, class_names: list[str]) -> None:
        super().__init__(convert_charrefs=False)
        self.class_names = set(class_names)
        self.blocks: list[str] = []
        self._capture_depth = 0
        self._capture_parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr_map = {name: value or "" for name, value in attrs}
        class_tokens = set(attr_map.get("class", "").split())
        should_capture = bool(class_tokens & self.class_names)
        if should_capture and self._capture_depth == 0:
            self._capture_parts = []
            self._capture_depth = 1
            self._capture_parts.append(self.get_starttag_text() or f"<{tag}>")
            return
        if self._capture_depth > 0:
            self._capture_depth += 1
            self._capture_parts.append(self.get_starttag_text() or f"<{tag}>")

    def handle_endtag(self, tag: str) -> None:
        if self._capture_depth <= 0:
            return
        self._capture_parts.append(f"</{tag}>")
        self._capture_depth -= 1
        if self._capture_depth == 0:
            self.blocks.append("".join(self._capture_parts))
            self._capture_parts = []

    def handle_data(self, data: str) -> None:
        if self._capture_depth > 0:
            self._capture_parts.append(data)

    def handle_entityref(self, name: str) -> None:
        if self._capture_depth > 0:
            self._capture_parts.append(f"&{name};")

    def handle_charref(self, name: str) -> None:
        if self._capture_depth > 0:
            self._capture_parts.append(f"&#{name};")
