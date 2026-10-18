import os
import sys
import urllib.request
import xml.etree.ElementTree as ET

BASE = "https://raw.githubusercontent.com/unicode-org/cldr/main/common/"
DEFAULT_SOURCES = [
    BASE + "annotations/ar.xml",
    BASE + "annotationsDerived/ar.xml",
    BASE + "annotations/ar_short.xml",
]
OUTPUT = "app/src/main/assets/emoji/ar_keywords.txt"


def read(source):
    if source.startswith("http"):
        try:
            with urllib.request.urlopen(source, timeout=60) as r:
                return r.read()
        except Exception:
            return None
    with open(source, "rb") as f:
        return f.read()


def words_of(text):
    for part in text.replace("\u060c", ",").replace(",", "|").split("|"):
        w = " ".join(part.split())
        if w:
            yield w


def main():
    sources = sys.argv[1:] or DEFAULT_SOURCES
    out = OUTPUT
    table = {}
    for source in sources:
        data = read(source)
        if data is None:
            continue
        root = ET.fromstring(data)
        for node in root.iter("annotation"):
            cp = node.get("cp")
            if not cp or not node.text:
                continue
            bucket = table.setdefault(cp, [])
            for w in words_of(node.text):
                if w not in bucket:
                    bucket.append(w)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        for cp, words in table.items():
            if words:
                f.write(cp + "\t" + ",".join(words) + "\n")
    print(len(table), "emoji,", sum(len(w) for w in table.values()), "keywords")


main()
