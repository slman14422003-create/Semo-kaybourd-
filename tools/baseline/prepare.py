#!/usr/bin/env python3
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PLUGIN_VERSION = "1.4.1"


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with open(os.path.join(ROOT, path), "w", encoding="utf-8") as f:
        f.write(text)


def replace_once(text, old, new, label):
    if old not in text:
        sys.exit("prepare: anchor not found for %s" % label)
    return text.replace(old, new, 1)


def main():
    src = os.path.join(ROOT, "tools", "baseline", "module")
    dst = os.path.join(ROOT, "baselineprofile")
    if os.path.exists(dst):
        shutil.rmtree(dst)
    shutil.copytree(src, dst)

    s = read("settings.gradle.kts")
    if 'include(":baselineprofile")' not in s:
        write("settings.gradle.kts", s.rstrip("\n") + '\ninclude(":baselineprofile")\n')

    r = read("build.gradle.kts")
    if "androidx.baselineprofile" not in r:
        i = r.index("plugins {")
        j = r.index("\n}", i)
        m = re.search(r'id\("com\.android\.application"\) version "([^"]+)"', r)
        agp = m.group(1) if m else "8.10.1"
        add = (
            '\n    id("com.android.test") version "%s" apply false' % agp +
            '\n    id("androidx.baselineprofile") version "%s" apply false' % PLUGIN_VERSION
        )
        write("build.gradle.kts", r[:j] + add + r[j:])

    a = read("app/build.gradle.kts")
    if "androidx.baselineprofile" not in a:
        a = replace_once(
            a,
            '    id("org.jetbrains.kotlin.plugin.compose")\n',
            '    id("org.jetbrains.kotlin.plugin.compose")\n    id("androidx.baselineprofile")\n',
            "app plugins",
        )
        a = a.rstrip("\n") + (
            '\n\ndependencies {\n'
            '    baselineProfile(project(":baselineprofile"))\n'
            '    implementation("androidx.profileinstaller:profileinstaller:1.4.1")\n'
            '}\n'
        )
        write("app/build.gradle.kts", a)
    print("prepared")


if __name__ == "__main__":
    main()
