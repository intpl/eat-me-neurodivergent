#!/usr/bin/env python3
"""Fail if a banned word reaches text the user can see.

Only string literals and XML string resources are scanned, so comments and
identifiers are free to use ordinary English.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BANNED = [
    "missed", "skipped", "overdue", "late", "failed", "failure", "streak",
    "cheat", "should", "need to", "don't forget", "dont forget",
]

ROOT = Path(__file__).resolve().parent.parent
KOTLIN_COPY = [ROOT / "core/src/main/kotlin/pl/gladecki/eatmen/Text.kt"]
XML_COPY = sorted(Path(ROOT, "app/src/main/res").glob("values*/strings.xml"))
EXTRA_KOTLIN = sorted(Path(ROOT, "app/src/main/java").rglob("*.kt"))


def kotlin_literals(path: Path):
    text = path.read_text(encoding="utf-8")
    # Strip line comments and block comments first, so prose is not checked.
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    for match in re.finditer(r'"((?:[^"\\]|\\.)*)"', text):
        yield match.group(1)


def xml_strings(path: Path):
    try:
        tree = ET.parse(path)
    except ET.ParseError as error:
        yield "${path.name}: unparsable (${error})"
        return
    for node in tree.iter("string"):
        if node.text:
            yield node.text


def check(label: str, strings):
    problems = 0
    for text in strings:
        lowered = text.lower()
        for word in BANNED:
            if word in lowered:
                print(f"${label}: '${word}' in: ${text.strip()[:120]}")
                problems += 1
    return problems


problems = 0
for path in KOTLIN_COPY:
    problems += check(str(path.relative_to(ROOT)), kotlin_literals(path))
for path in EXTRA_KOTLIN:
    if path.name in {"Text.kt"}:
        continue
    problems += check(str(path.relative_to(ROOT)), kotlin_literals(path))
for path in XML_COPY:
    problems += check(str(path.relative_to(ROOT)), xml_strings(path))

if problems:
    print(f"${problems} banned word(s) in copy", file=sys.stderr)
    sys.exit(1)
print("wording ok")
