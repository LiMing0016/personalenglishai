from __future__ import annotations

import unittest
import zipfile
import json
from pathlib import Path

from python.ai_orchestrator.schemas.dictionary_cleaning import DictionaryRawEntry
from python.ai_orchestrator.tools.dictionary.entry_parser import parse_dictionary_entry_html
from python.ai_orchestrator.tools.dictionary.mdict_reader import iter_mdd_raw_resources, iter_mdx_raw_entries
from python.ai_orchestrator.tools.dictionary.xlsx_examples_reader import read_dictionary_examples_xlsx
from python.ai_orchestrator.workflows.dictionary_cleaning.cli import run_dictionary_cleaning_cli
from python.ai_orchestrator.workflows.dictionary_cleaning.workflow import run_dictionary_cleaning_workflow


TEST_TEMP_ROOT = Path(__file__).resolve().parent / "tmp_write_check"


class DictionaryCleaningTest(unittest.TestCase):
    def test_mdict_reader_normalizes_mdx_records_into_raw_entries(self) -> None:
        def fake_reader(_: Path):
            return [
                (b"home", b"<h1>home</h1>"),
                ("school", "<h1>school</h1>"),
            ]

        entries = list(iter_mdx_raw_entries(Path("fake.mdx"), reader_factory=fake_reader))

        self.assertEqual([entry.headword for entry in entries], ["home", "school"])
        self.assertEqual(entries[0].html, "<h1>home</h1>")

    def test_mdict_reader_normalizes_mdd_records_into_resources(self) -> None:
        def fake_reader(_: Path):
            return [
                (b"\\pic\\home.png", b"image-bytes"),
                ("style.css", b"body{}"),
            ]

        resources = list(iter_mdd_raw_resources(Path("fake.mdd"), limit=2, reader_factory=fake_reader))

        self.assertEqual(resources[0]["resource_key"], "\\pic\\home.png")
        self.assertEqual(resources[0]["file_name"], "home.png")
        self.assertEqual(resources[0]["resource_type"], "image")
        self.assertEqual(resources[0]["size_bytes"], 11)
        self.assertEqual(resources[1]["resource_type"], "css")

    def test_entry_parser_cleans_html_and_extracts_learning_fields(self) -> None:
        html = """
        <html>
          <head><script>alert('x')</script><style>.hide{display:none}</style></head>
          <body>
            <article class="entry">
              <h1>home</h1>
              <span class="phon">BrE /həʊm/</span>
              <span class="pos">noun</span>
              <section class="sense">
                <span class="def">the house or flat that you live in</span>
                <span class="zh">家；住所</span>
                <ul>
                  <li>
                    <span class="x">We are not far from home now.</span>
                    <span class="xt">我们现在离家不远了。</span>
                  </li>
                </ul>
              </section>
              <div class="idm-g">
                <span class="phrase">at home</span>
                <span class="def">comfortable and relaxed</span>
              </div>
            </article>
          </body>
        </html>
        """

        entry = parse_dictionary_entry_html("home", html)

        self.assertEqual(entry.word, "home")
        self.assertEqual(entry.part_of_speech, "noun")
        self.assertEqual(entry.phonetics[0].text, "BrE /həʊm/")
        self.assertEqual(entry.senses[0].definition_en, "the house or flat that you live in")
        self.assertEqual(entry.senses[0].definition_zh, "家；住所")
        self.assertEqual(entry.senses[0].examples[0].text_en, "We are not far from home now.")
        self.assertEqual(entry.phrases[0].text, "at home")
        self.assertNotIn("alert", entry.clean_text)
        self.assertNotIn(".hide", entry.clean_text)

    def test_entry_parser_extracts_oxford_mdx_custom_tags(self) -> None:
        html = """
        <head><script src="oalecd9.js"></script></head>
        <h-g>
          <top-g>
            <h>aban·don</h>
            <pron-gs>
              <pron-g-blk><brelabel>BrE</brelabel><phon-blk>/<phon>əˈbændən</phon>/</phon-blk></pron-g-blk>
              <pron-g-blk><namelabel>NAmE</namelabel><phon-blk>/<phon>əˈbændən</phon>/</phon-blk></pron-g-blk>
            </pron-gs>
          </top-g>
          <pos-g><pos-blk><pos><xhtml:a href="helpp:v">verb</xhtml:a></pos></pos-blk></pos-g>
          <sn-g>
            <def>
              <xhtml:a href="d:to">to</xhtml:a> <xhtml:a href="d:leave">leave</xhtml:a> somebody
              <chnsep> </chnsep><chn>离弃，遗弃，抛弃</chn>
            </def>
            <x-gs>
              <x e_inline="y" wd="The baby had been abandoned by its mother.">
                <xhtml:a href="x:The">The</xhtml:a> baby had been abandoned by its mother.
                <xhtml:br></xhtml:br><chn>这个婴儿被母亲遗弃了。</chn>
              </x>
            </x-gs>
          </sn-g>
          <unbox><unbox-title>Which Word?</unbox-title><p>Abandon is stronger than leave.</p></unbox>
          <idm-g>
            <top-g><idm-blk><idm>with ˈgay abandon</idm></idm-blk></top-g>
            <sn-g><def>without thinking about the results<chnsep> </chnsep><chn>放纵地；恣意地</chn></def></sn-g>
          </idm-g>
        </h-g>
        """

        entry = parse_dictionary_entry_html("abandon", html)

        self.assertEqual(entry.word, "aban·don")
        self.assertEqual(entry.part_of_speech, "verb")
        self.assertEqual([phonetic.text for phonetic in entry.phonetics], ["əˈbændən"])
        self.assertEqual(entry.senses[0].definition_en, "to leave somebody")
        self.assertEqual(entry.senses[0].definition_zh, "离弃，遗弃，抛弃")
        self.assertEqual(entry.senses[0].examples[0].text_en, "The baby had been abandoned by its mother.")
        self.assertEqual(entry.senses[0].examples[0].text_zh, "这个婴儿被母亲遗弃了。")
        self.assertEqual(entry.phrases[0].text, "with ˈgay abandon")
        self.assertEqual(entry.phrases[0].definition_en, "without thinking about the results")
        self.assertEqual(entry.phrases[0].definition_zh, "放纵地；恣意地")
        self.assertEqual(entry.usage_notes[0], "Which Word? Abandon is stronger than leave.")

    def test_xlsx_reader_extracts_bilingual_examples_by_headword(self) -> None:
        TEST_TEMP_ROOT.mkdir(exist_ok=True)
        path = TEST_TEMP_ROOT / "examples_reader.xlsx"
        write_minimal_xlsx(
            path,
            rows=[
                ["word", "english", "chinese"],
                ["home", "We are not far from home now.", "我们现在离家不远了。"],
                ["home", "She leaves home at 7 every day.", "她每天七点钟离家。"],
            ],
        )

        examples = read_dictionary_examples_xlsx(path)

        self.assertEqual(len(examples), 2)
        self.assertEqual(examples[0].headword, "home")
        self.assertEqual(examples[0].text_en, "We are not far from home now.")
        self.assertEqual(examples[1].text_zh, "她每天七点钟离家。")

    def test_xlsx_reader_accepts_bilingual_examples_without_headword(self) -> None:
        TEST_TEMP_ROOT.mkdir(exist_ok=True)
        path = TEST_TEMP_ROOT / "examples_reader_without_headword.xlsx"
        write_minimal_xlsx(
            path,
            rows=[
                ["英文", "中文"],
                ["We are not far from home now.", "我们现在离家不远了。"],
            ],
        )

        examples = read_dictionary_examples_xlsx(path)

        self.assertEqual(len(examples), 1)
        self.assertIsNone(examples[0].headword)
        self.assertEqual(examples[0].text_en, "We are not far from home now.")

    def test_workflow_merges_entries_and_external_examples(self) -> None:
        html = """
        <h1>home</h1>
        <span class="pos">noun</span>
        <section class="sense">
          <span class="def">the house or flat that you live in</span>
          <span class="zh">家；住所</span>
        </section>
        <div class="idm-g"><span class="phrase">at home</span></div>
        """
        TEST_TEMP_ROOT.mkdir(exist_ok=True)
        examples_path = TEST_TEMP_ROOT / "workflow_examples.xlsx"
        write_minimal_xlsx(
            examples_path,
            rows=[
                ["word", "english", "chinese"],
                ["home", "We are not far from home now.", "我们现在离家不远了。"],
            ],
        )

        result = run_dictionary_cleaning_workflow(
            source_code="oald9",
            display_name="牛津高阶英汉双解词典（第9版）",
            raw_entries=[DictionaryRawEntry(headword="home", html=html)],
            examples_path=examples_path,
        )

        self.assertEqual(result.status, "completed")
        self.assertEqual(result.summary.entry_count, 1)
        self.assertEqual(result.summary.example_count, 1)
        self.assertEqual(result.summary.phrase_count, 1)
        self.assertEqual(result.entries[0].senses[0].examples[0].text_zh, "我们现在离家不远了。")

    def test_cli_writes_cleaning_result_for_java_worker(self) -> None:
        TEST_TEMP_ROOT.mkdir(exist_ok=True)
        request_path = TEST_TEMP_ROOT / "dictionary_import_request.json"
        output_path = TEST_TEMP_ROOT / "dictionary_import_result.json"
        request_path.write_text(
            json.dumps(
                {
                    "sourceCode": "oald9",
                    "displayName": "Oxford",
                    "limit": 1,
                    "rawEntries": [
                        {
                            "headword": "home",
                            "html": """
                            <h>home</h><pos>noun</pos>
                            <sn-g><def>the house you live in<chn>家</chn></def>
                            <x>We are not far from home now.<chn>我们现在离家不远了。</chn></x></sn-g>
                            """
                        }
                    ],
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )

        exit_code = run_dictionary_cleaning_cli(["--input", str(request_path), "--output", str(output_path)])

        self.assertEqual(exit_code, 0)
        payload = json.loads(output_path.read_text(encoding="utf-8"))
        self.assertEqual(payload["status"], "completed")
        self.assertEqual(payload["summary"]["entry_count"], 1)
        self.assertEqual(payload["entries"][0]["word"], "home")
        self.assertEqual(payload["entries"][0]["senses"][0]["examples"][0]["text_zh"], "我们现在离家不远了。")


def write_minimal_xlsx(path: Path, rows: list[list[str]]) -> None:
    shared_strings: list[str] = []
    shared_index: dict[str, int] = {}

    def shared(value: str) -> int:
        if value not in shared_index:
            shared_index[value] = len(shared_strings)
            shared_strings.append(value)
        return shared_index[value]

    sheet_rows: list[str] = []
    for row_number, row in enumerate(rows, start=1):
        cells = []
        for col_index, value in enumerate(row, start=1):
            column = chr(ord("A") + col_index - 1)
            cells.append(f'<c r="{column}{row_number}" t="s"><v>{shared(value)}</v></c>')
        sheet_rows.append(f'<row r="{row_number}">{"".join(cells)}</row>')

    with zipfile.ZipFile(path, "w") as workbook:
        workbook.writestr("[Content_Types].xml", "<Types></Types>")
        workbook.writestr("xl/workbook.xml", '<workbook><sheets><sheet name="Sheet1"/></sheets></workbook>')
        workbook.writestr("xl/worksheets/sheet1.xml", f'<worksheet><sheetData>{"".join(sheet_rows)}</sheetData></worksheet>')
        workbook.writestr(
            "xl/sharedStrings.xml",
            "<sst>" + "".join(f"<si><t>{value}</t></si>" for value in shared_strings) + "</sst>",
        )


if __name__ == "__main__":
    unittest.main()
