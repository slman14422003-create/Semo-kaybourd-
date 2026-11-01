#!/usr/bin/env python3
import argparse
import json
import os
import random
import re
import statistics
import sys
import time

import numpy as np
import onnxruntime as ort

SPECIAL = 6
BOS = 1
MAX_CTX = 28
DIACRITICS = re.compile("[\u064B-\u0652\u0670\u0653-\u0655\u0640]")
ORDER = ["int8-batch", "int8", "batch"]


def session(path):
    o = ort.SessionOptions()
    o.intra_op_num_threads = 2
    return ort.InferenceSession(path, o, providers=["CPUExecutionProvider"])


def run1(s, ids):
    name = s.get_inputs()[0].name
    return s.run(None, {name: np.asarray([ids], dtype=np.int64)})[0][0]


def load_contexts(tokenizer_path, corpus_path, allow_random, vocab_size):
    rng = random.Random(7)
    ctxs = []
    tok = None
    try:
        from tokenizers import Tokenizer
        tok = Tokenizer.from_file(tokenizer_path)
    except Exception as e:
        if not allow_random:
            raise SystemExit("tokenizers unavailable: %s" % e)
    if tok is not None:
        with open(corpus_path, encoding="utf-8") as f:
            lines = [l.strip() for l in f if l.strip()]
        for line in lines:
            words = DIACRITICS.sub("", line).split()
            if len(words) < 3:
                continue
            ks = list(range(1, len(words)))
            rng.shuffle(ks)
            for k in sorted(ks[:4]):
                ids = tok.encode(" " + " ".join(words[:k])).ids
                ids = [BOS] + ids
                if len(ids) > MAX_CTX:
                    ids = [BOS] + ids[-(MAX_CTX - 1):]
                ctxs.append(ids)
    else:
        for _ in range(300):
            n = rng.randint(4, 20)
            ctxs.append([BOS] + [rng.randint(SPECIAL, vocab_size - 1) for _ in range(n)])
    if len(ctxs) < 20:
        raise SystemExit("not enough evaluation contexts: %d" % len(ctxs))
    return ctxs


def compare(base, cand, ctxs):
    top1 = 0
    overlap = 0.0
    for ids in ctxs:
        a = run1(base, ids)[SPECIAL:]
        b = run1(cand, ids)[SPECIAL:]
        top1 += int(a.argmax() == b.argmax())
        overlap += len(set(np.argsort(-a)[:5]) & set(np.argsort(-b)[:5])) / 5.0
    n = len(ctxs)
    return top1 / n, overlap / n


def median_ms(fn, runs=40, warm=5):
    for _ in range(warm):
        fn()
    t = []
    for _ in range(runs):
        s = time.perf_counter()
        fn()
        t.append((time.perf_counter() - s) * 1000.0)
    return statistics.median(t)


def latency(path):
    s = session(path)
    probe = [BOS] + [SPECIAL + i for i in range(15)]
    single = median_ms(lambda: run1(s, probe))
    res = {"single_ms": round(single, 2)}
    try:
        name = s.get_inputs()[0].name
        batch = np.asarray([probe] * 3, dtype=np.int64)
        three = median_ms(lambda: s.run(None, {name: batch}))
        res["batch3_ms"] = round(three, 2)
        res["three_singles_ms"] = round(single * 3, 2)
    except Exception:
        pass
    return res


def batch_check(path, base, ctxs, length=8):
    pool = [c[:length] for c in ctxs if len(c) >= length][:12]
    if len(pool) < 4:
        return {"ok": False, "detail": "not enough equal length contexts"}
    s = session(path)
    name = s.get_inputs()[0].name
    try:
        out = s.run(None, {name: np.asarray(pool, dtype=np.int64)})[0]
    except Exception as e:
        return {"ok": False, "detail": " ".join(str(e).split())[:200]}
    if out.shape[0] != len(pool):
        return {"ok": False, "detail": "unexpected output shape %s" % (out.shape,)}
    worst = 0.0
    agree = 0
    for i, ids in enumerate(pool):
        ref = run1(base, ids)
        worst = max(worst, float(np.abs(out[i] - ref).max()))
        agree += int(out[i][SPECIAL:].argmax() == ref[SPECIAL:].argmax())
    return {"ok": True, "max_abs_diff": worst, "top1_vs_base": agree / len(pool), "detail": "max abs diff %.2e" % worst}


def make_batch(src, dst):
    import onnx
    from onnx import numpy_helper
    m = onnx.load(src)
    for t in list(m.graph.input) + list(m.graph.output):
        t.type.tensor_type.shape.dim[0].dim_param = "batch"
    del m.graph.value_info[:]
    inits = {i.name: i for i in m.graph.initializer}
    consts = {n.output[0]: n for n in m.graph.node if n.op_type == "Constant"}
    changed = 0
    for n in m.graph.node:
        if n.op_type != "Reshape" or len(n.input) < 2:
            continue
        key = n.input[1]
        if key in inits:
            arr = numpy_helper.to_array(inits[key])
            if arr.ndim == 1 and arr.size >= 2 and arr[0] == 1:
                arr = arr.copy()
                arr[0] = 0
                inits[key].CopyFrom(numpy_helper.from_array(arr, key))
                changed += 1
        elif key in consts:
            for a in consts[key].attribute:
                if a.name == "value":
                    arr = numpy_helper.to_array(a.t)
                    if arr.ndim == 1 and arr.size >= 2 and arr[0] == 1:
                        arr = arr.copy()
                        arr[0] = 0
                        a.t.CopyFrom(numpy_helper.from_array(arr, a.t.name))
                        changed += 1
    onnx.save(m, dst)
    return changed


def make_int8(src, dst):
    from onnxruntime.quantization import QuantType, quantize_dynamic
    pre = dst + ".pre.onnx"
    use = src
    try:
        from onnxruntime.quantization.shape_inference import quant_pre_process
        quant_pre_process(src, pre, skip_symbolic_shape=True)
        use = pre
    except Exception as e:
        print("pre-process skipped: %s" % e)
    quantize_dynamic(use, dst, weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul", "Gemm"])
    if os.path.exists(pre):
        os.remove(pre)


def size_mb(path):
    return round(os.path.getsize(path) / 1048576.0, 2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--tokenizer", required=True)
    ap.add_argument("--corpus", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--min-top1", type=float, default=0.90)
    ap.add_argument("--min-top5", type=float, default=0.85)
    ap.add_argument("--allow-random-ids", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    base = session(a.model)
    vocab = int(base.get_outputs()[0].shape[-1])
    ctxs = load_contexts(a.tokenizer, a.corpus, a.allow_random_ids, vocab)
    report = {"contexts": len(ctxs), "variants": {}, "gates": {"top1": a.min_top1, "top5": a.min_top5}}
    report["base"] = {"size_mb": size_mb(a.model), **latency(a.model)}

    def add(name, path, needs_batch):
        v = {"file": os.path.basename(path), "size_mb": size_mb(path)}
        s = session(path)
        v["top1"], v["top5"] = [round(x, 4) for x in compare(base, s, ctxs)]
        v.update(latency(path))
        quality = v["top1"] >= a.min_top1 and v["top5"] >= a.min_top5
        v["quality_ok"] = quality
        if needs_batch:
            v["batch"] = batch_check(path, base, ctxs)
            tol_ok = v["batch"]["ok"] and (v["batch"]["max_abs_diff"] < 1e-3 or v["batch"]["top1_vs_base"] >= a.min_top1)
            v["pass"] = bool(quality and tol_ok)
        else:
            v["pass"] = bool(quality)
        report["variants"][name] = v

    batch_path = os.path.join(a.out, "ruq-5m-batch.onnx")
    batch_ready = False
    try:
        changed = make_batch(a.model, batch_path)
        report["batch_edit"] = {"reshape_constants_changed": changed}
        add("batch", batch_path, True)
        batch_ready = report["variants"]["batch"]["pass"]
    except Exception as e:
        report["variants"]["batch"] = {"pass": False, "error": "%s: %s" % (type(e).__name__, str(e)[:200])}

    int8_path = os.path.join(a.out, "ruq-5m-int8.onnx")
    try:
        make_int8(a.model, int8_path)
        add("int8", int8_path, False)
    except Exception as e:
        report["variants"]["int8"] = {"pass": False, "error": "%s: %s" % (type(e).__name__, str(e)[:200])}

    if batch_ready:
        both_path = os.path.join(a.out, "ruq-5m-int8-batch.onnx")
        try:
            make_int8(batch_path, both_path)
            add("int8-batch", both_path, True)
        except Exception as e:
            report["variants"]["int8-batch"] = {"pass": False, "error": "%s: %s" % (type(e).__name__, str(e)[:200])}

    best = "none"
    for name in ORDER:
        if report["variants"].get(name, {}).get("pass"):
            best = name
            break
    report["best"] = best
    with open(os.path.join(a.out, "best.txt"), "w") as f:
        f.write(best)
    with open(os.path.join(a.out, "report.json"), "w") as f:
        json.dump(report, f, indent=2)

    lines = ["### Model optimization", "", "Contexts: %d, base: %.2f MB, single run %.2f ms" % (report["contexts"], report["base"]["size_mb"], report["base"]["single_ms"]), ""]
    lines.append("| variant | size MB | top1 | top5 | single ms | batch3 ms | 3 singles ms | batch | pass |")
    lines.append("|---|---|---|---|---|---|---|---|---|")
    for name in ORDER[::-1]:
        v = report["variants"].get(name)
        if not v:
            continue
        if "error" in v:
            lines.append("| %s | | | | | | | %s | no |" % (name, v["error"].replace("|", "/")))
            continue
        b = v.get("batch", {}).get("detail", "n/a")
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            name, v["size_mb"], v["top1"], v["top5"], v.get("single_ms", ""), v.get("batch3_ms", ""),
            v.get("three_singles_ms", ""), b.replace("|", "/"), "yes" if v["pass"] else "no"))
    lines += ["", "Best: **%s**" % best, "", "Latency is measured on the CI x86 runner and only indicates direction; confirm on a phone with the Full check page."]
    text = "\n".join(lines)
    with open(os.path.join(a.out, "summary.md"), "w") as f:
        f.write(text)
    print(text)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as f:
            f.write(text + "\n")


if __name__ == "__main__":
    sys.exit(main())
