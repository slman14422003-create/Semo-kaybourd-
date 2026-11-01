#!/usr/bin/env python3
import glob
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def lines_of(path):
    with open(path, encoding="utf-8") as f:
        return [l.rstrip("\n") for l in f if l.strip() and not l.startswith("#")]


def main():
    out = sys.argv[1]
    found = sorted(glob.glob(os.path.join(ROOT, "app", "src", "*", "generated", "baselineProfiles", "*.txt")))
    found = [p for p in found if os.path.basename(p).startswith("baseline-prof")]
    if not found:
        sys.exit("merge: no generated baseline profile found")
    generated = []
    for p in found:
        generated += lines_of(p)
    existing = lines_of(os.path.join(ROOT, "app", "src", "main", "baseline-prof.txt"))
    seen = set()
    merged = []
    for l in generated + existing:
        if l not in seen:
            seen.add(l)
            merged.append(l)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(merged) + "\n")
    methods = sum(1 for l in generated if l.startswith(("H", "S", "P", "HS", "HSP", "SP")))
    summary = "Generated rules: %d (%d method rules), existing rules kept: %d, merged total: %d" % (
        len(generated), methods, len(existing), len(merged))
    print(summary)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write("### Baseline profile\n\n" + summary + "\n")


if __name__ == "__main__":
    main()
