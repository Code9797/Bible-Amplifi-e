#!/usr/bin/env python3
"""Convert Quanda's simple Bible XML into OSIS 2.1.1 for SWORD/JSword.

Input structure expected:
  <bible><testament><book number="1"><chapter number="1"><verse number="1">...</verse>

This converter intentionally preserves verse text as supplied by the source XML.
"""

from __future__ import annotations

import argparse
import html
import xml.etree.ElementTree as ET
from pathlib import Path

OSIS_BOOKS = [
    "Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
    "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
    "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
    "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal", "Matt",
    "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph", "Phil",
    "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas", "1Pet",
    "2Pet", "1John", "2John", "3John", "Jude", "Rev",
]


def verse_text(node: ET.Element) -> str:
    return "".join(node.itertext()).strip()


def convert(source: Path, target: Path) -> None:
    root = ET.parse(source).getroot()
    books = list(root.iter("book"))
    if len(books) != 66:
        raise SystemExit(f"Expected 66 books, found {len(books)}")

    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="\n") as out:
        out.write('<?xml version="1.0" encoding="UTF-8"?>\n')
        out.write('<osis xmlns="http://www.bibletechnologies.net/2003/OSIS/namespace">\n')
        out.write('  <osisText osisIDWork="QuandaAMP" osisRefWork="Bible" canonical="true" xml:lang="en">\n')
        out.write('    <header>\n')
        out.write('      <work osisWork="QuandaAMP">\n')
        out.write('        <title>Quanda Bible Amplifiée</title>\n')
        out.write('        <identifier type="OSIS">QuandaAMP</identifier>\n')
        out.write('        <language type="SIL">eng</language>\n')
        out.write('        <refSystem>Bible.KJV</refSystem>\n')
        out.write('      </work>\n')
        out.write('      <work osisWork="Bible"><refSystem>Bible.KJV</refSystem></work>\n')
        out.write('    </header>\n')

        for idx, book in enumerate(books, start=1):
            osis_book = OSIS_BOOKS[idx - 1]
            out.write(f'    <div type="book" osisID="{osis_book}">\n')
            for chapter in book.findall("chapter"):
                chapter_num = int(chapter.attrib["number"])
                out.write(f'      <chapter osisID="{osis_book}.{chapter_num}">\n')
                for verse in chapter.findall("verse"):
                    verse_num = int(verse.attrib["number"])
                    text = html.escape(verse_text(verse), quote=False)
                    osis_id = f"{osis_book}.{chapter_num}.{verse_num}"
                    out.write(f'        <verse osisID="{osis_id}">{text}</verse>\n')
                out.write('      </chapter>\n')
            out.write('    </div>\n')

        out.write('  </osisText>\n')
        out.write('</osis>\n')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    args = parser.parse_args()
    convert(args.source, args.target)


if __name__ == "__main__":
    main()
