#!/usr/bin/env python3
"""يتحقق من ملفات التصحيح العربية. يخرج بكود 1 عند أي خطأ (يُستعمل بالـ workflow قبل فتح الـ PR)."""
import os, sys

DICT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'assets', 'dict')


def words(name):
    p = os.path.join(DICT, name)
    return {l.strip() for l in open(p, encoding='utf-8') if l.strip()} if os.path.exists(p) else set()


def check(name, strict):
    p = os.path.join(DICT, name)
    if not os.path.exists(p):
        print(f'{name}: missing'); return 0, 1
    valid = words('ar_core.txt') | words('ar_extra.txt')
    seen, errs, n = set(), [], 0
    for i, line in enumerate(open(p, encoding='utf-8'), 1):
        line = line.rstrip('\n')
        if not line or line.startswith('#'):
            continue
        parts = line.split('\t')
        if len(parts) != 2 or not parts[0] or not parts[1].strip():
            errs.append(f'{name}:{i}: bad line'); continue
        k, v = parts[0], parts[1].strip()
        n += 1
        if k == v: errs.append(f'{name}:{i}: key == value ({k})')
        if k in seen: errs.append(f'{name}:{i}: duplicate key ({k})')
        seen.add(k)
        if strict:
            if ' ' in k: errs.append(f'{name}:{i}: space in key ({k})')
            if k in valid: errs.append(f'{name}:{i}: key is a valid dictionary word ({k})')
    for e in errs[:30]: print(e)
    print(f'{name}: {n} pairs, {len(errs)} problems')
    return n, len(errs)


if __name__ == '__main__':
    bad = 0
    for name, strict in (('ar_fix_auto.txt', True),):
        bad += check(name, strict)[1]
    # الملف اليدوي: نبلّغ فقط عن التكرار والصيغة، ولا نفشل على المفاتيح التي فيها مسافات (عبارات قديمة)
    bad += check('ar_fix.txt', False)[1]
    sys.exit(1 if bad else 0)
