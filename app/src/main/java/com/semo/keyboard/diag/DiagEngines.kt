package com.semo.keyboard.diag

import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.domain.logic.Arabizi
import com.semo.keyboard.domain.logic.AutoCorrect
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.logic.CalcEngine
import com.semo.keyboard.domain.logic.ClipClassifier
import com.semo.keyboard.domain.logic.ClipKind
import com.semo.keyboard.domain.logic.EmojiData
import com.semo.keyboard.domain.logic.EmojiSearch
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.logic.MathEvaluator
import com.semo.keyboard.domain.logic.NeuralLm
import com.semo.keyboard.domain.logic.NextWordModel
import com.semo.keyboard.domain.logic.NgramModel
import com.semo.keyboard.domain.logic.SentenceRules
import com.semo.keyboard.domain.logic.SuggestionEngine
import com.semo.keyboard.domain.logic.TranslateLanguages
import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardStyle
import com.semo.keyboard.domain.model.KeyboardTheme
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.ShiftState
import com.semo.keyboard.ui.settings.themeNameRes
import com.semo.keyboard.ui.theme.semoColors
import java.math.BigDecimal

private object Sample {
    const val AR_PREFIX = "\u0645\u0631"
    const val AR_BOOK = "\u0643\u062a\u0627\u0628"
    const val AR_IN = "\u0641\u064a"
    const val AR_HEART = "\u0642\u0644\u0628"
    const val AR_WENT = "\u0630\u0647\u0628"
    const val AR_BOY = "\u0627\u0644\u0648\u0644\u062f"
    const val AR_TO = "\u0625\u0644\u0649"
    const val AR_THIS = "\u0647\u0630\u0627"
    const val AR_SAID = "\u0642\u0627\u0644"
    const val AR_MOHAMMED = "\u0645\u062d\u0645\u062f"
    const val AR_WAS = "\u0643\u0627\u0646"
    const val AR_KNOWLEDGE = "\u0627\u0644\u0639\u0644\u0645"
    const val AR_THE = "\u0627\u0644"

    val AR_PREFIXES = listOf(
        "\u0645\u0631", "\u0643\u062a", "\u0627\u0644", "\u0639\u0644", "\u0645\u062d",
        "\u0642\u0627", "\u0630\u0647", "\u0647\u0630", "\u0641\u064a", "\u062b\u0645"
    )
    val EN_PREFIXES = listOf("th", "he", "wh", "qu", "pro", "con", "re", "ex", "un", "st", "ma", "di")
}

internal object DiagEngines {

    suspend fun run(c: DiagContext) {
        engines(c)
        layouts(c)
        themes(c)
        performance(c)
    }

    private suspend fun engines(c: DiagContext) {
        val g = "Engines"
        val en = KeyboardLanguage.ENGLISH
        val ar = KeyboardLanguage.ARABIC

        c.probe(g, "Dictionaries loaded") {
            val parts = ArrayList<String>()
            var broken = false
            for (lang in listOf(en, ar)) {
                if (!BigDictionary.isLoaded(lang)) DictionaryManager.load(c.app, lang)
                val words = BigDictionary.size(lang)
                parts.add("${lang.code}: $words words, ngram=${NgramModel.isLoaded(lang)}")
                // the built-in lists are small by design (en_core 562 words; en_valid is the spell-check list, not suggestions);
                // only a downloaded dictionary is expected to be large
                val minWords = if (com.semo.keyboard.data.DictionaryManager.hasWords(c.app, lang)) 1000 else 500
                if (words < minWords) broken = true
            }
            if (broken) fail(parts.joinToString("; ")) else pass(parts.joinToString("; "))
        }

        c.probe(g, "Auto-correct") {
            AutoCorrect.attach(c.app)
            val problems = ArrayList<String>()
            val teh = AutoCorrect.fix("teh")
            if (teh != "the") problems.add("fix(teh) = $teh")
            if (AutoCorrect.fix("hello") != null) problems.add("fix(hello) should be null")
            var checked = 0
            var wrong = 0
            val examples = ArrayList<String>()
            c.app.assets.open("dict/ar_fix.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (checked >= 120) break
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val key = line.substring(0, tab)
                    val expected = line.substring(tab + 1).trim()
                    checked++
                    val got = AutoCorrect.fix(key)
                    if (got != expected) {
                        wrong++
                        if (examples.size < 3) examples.add("$key -> $got (expected $expected)")
                    }
                }
            }
            val text = "table sample: $wrong of $checked differ" + if (examples.isEmpty()) "" else "; " + examples.joinToString("; ")
            if (problems.isNotEmpty()) fail(problems.joinToString("; ") + "; " + text)
            else if (checked > 0 && wrong * 10 > checked) warn(text)
            else pass(text)
        }

        c.probe(g, "Suggestions and spelling") {
            val problems = ArrayList<String>()
            if (SuggestionEngine.suggest("th", en, emptyList()).isEmpty()) problems.add("no English suggestions for prefix th")
            if (SuggestionEngine.suggest(Sample.AR_PREFIX, ar, emptyList()).isEmpty()) problems.add("no Arabic suggestions for a common prefix")
            if (!SuggestionEngine.isKnown("hello", en, emptySet())) problems.add("isKnown(hello) is false")
            if (SuggestionEngine.isKnown("zzqxjvv", en, emptySet())) problems.add("isKnown(gibberish) is true")
            if (!SuggestionEngine.isKnown(Sample.AR_BOOK, ar, emptySet())) problems.add("isKnown(common Arabic word) is false")
            val fixes = SuggestionEngine.corrections("helo", en, emptySet())
            if (fixes.none { it.equals("hello", ignoreCase = true) }) problems.add("corrections(helo) = $fixes")
            val smart = SuggestionEngine.smartFix("teh", en, emptySet())
            if (smart != "the") problems.add("smartFix(teh) = $smart")
            if (SuggestionEngine.currentWord("hello wor") != "wor") problems.add("currentWord failed")
            if (SuggestionEngine.languageOf("hello", ar) != en) problems.add("languageOf(latin word) not English")
            if (SuggestionEngine.languageOf(Sample.AR_BOOK, en) != ar) problems.add("languageOf(arabic word) not Arabic")
            if (problems.isEmpty()) pass("10 assertions") else warn(problems.joinToString("; "))
        }

        c.probe(g, "Next-word prediction") {
            val problems = ArrayList<String>()
            val e = NextWordModel.predict("i", en, emptyMap())
            if (e.isEmpty()) problems.add("no English prediction after i")
            val a = NextWordModel.predict(Sample.AR_IN, ar, emptyMap())
            if (a.isEmpty()) problems.add("no Arabic prediction after a common word")
            if (problems.isEmpty()) pass("en=${e.size} ar=${a.size}") else warn(problems.joinToString("; "))
        }

        c.probe(g, "Neural language model (Arabic)") {
            val t0 = SystemClock.elapsedRealtime()
            if (!NeuralLm.ready) NeuralLm.load(c.app)
            val loadMs = SystemClock.elapsedRealtime() - t0
            if (!NeuralLm.ready) {
                fail("model did not load after ${loadMs}ms: ${NeuralLm.loadError ?: "check ai/ruq-5m.onnx, ai/tokenizer.json and onnxruntime"}")
            } else {
                val contexts = listOf(
                    listOf(Sample.AR_WENT, Sample.AR_BOY),
                    listOf(Sample.AR_THIS),
                    listOf(Sample.AR_IN, Sample.AR_THIS),
                    listOf(Sample.AR_WENT, Sample.AR_TO),
                    listOf(Sample.AR_SAID, Sample.AR_MOHAMMED),
                    listOf(Sample.AR_WAS, Sample.AR_KNOWLEDGE)
                )
                val times = ArrayList<Long>()
                var empty = 0
                for (ctx in contexts) {
                    val s = System.nanoTime()
                    val out = NeuralLm.nextWords(ctx, 3)
                    times.add((System.nanoTime() - s) / 1_000_000L)
                    if (out.isEmpty()) empty++
                }
                val avg = times.average()
                val max = times.maxOrNull() ?: 0L
                val text = "ready, load ${loadMs}ms, ${contexts.size} queries avg ${fmt(avg)}ms max ${max}ms, empty=$empty"
                if (empty == contexts.size) fail(text) else if (avg > 150.0) warn(text) else pass(text)
            }
        }

        c.probe(g, "Arabizi") {
            val ok = Arabizi.looksArabizi("mar7aba")
            val cands = Arabizi.candidates("mar7aba", 3)
            if (!ok || cands.isEmpty()) warn("looksArabizi=$ok candidates=$cands") else pass("${cands.size} candidates")
        }

        c.probe(g, "Calculator engine") {
            val tolerance = BigDecimal("0.000000001")
            val cases = listOf(
                "2+3*4" to "14", "(1+2)\u00d73" to "9", "10\u00f74" to "2.5", "2^10" to "1024",
                "\u221a(16)" to "4", "5!" to "120", "log(100)" to "2", "sin(30)" to "0.5"
            )
            val bad = ArrayList<String>()
            for ((expr, want) in cases) {
                when (val r = CalcEngine.evaluate(expr, true, BigDecimal.ZERO)) {
                    is CalcEngine.Outcome.Ok -> {
                        if (r.value.subtract(BigDecimal(want)).abs().compareTo(tolerance) > 0) {
                            bad.add("$expr = ${r.value.toPlainString()} (expected $want)")
                        }
                    }
                    is CalcEngine.Outcome.Fail -> bad.add("$expr failed")
                }
            }
            for (expr in listOf("5/0", "2+")) {
                when (CalcEngine.evaluate(expr, true, BigDecimal.ZERO)) {
                    is CalcEngine.Outcome.Ok -> bad.add("$expr should fail")
                    is CalcEngine.Outcome.Fail -> Unit
                }
            }
            if (bad.isEmpty()) pass("${cases.size + 2} expressions") else warn(bad.joinToString("; "))
        }

        c.probe(g, "Inline math detection") {
            val problems = ArrayList<String>()
            val r = MathEvaluator.detect("12+8=")
            if (r == null || r.value != "20") problems.add("detect(12+8=) = ${r?.value}")
            if (MathEvaluator.detect("hello world") != null) problems.add("plain text detected as math")
            if (MathEvaluator.detect("2026-10-09") != null) problems.add("date detected as math")
            if (problems.isEmpty()) pass() else warn(problems.joinToString("; "))
        }

        c.probe(g, "Clipboard classifier") {
            val problems = ArrayList<String>()
            if (ClipClassifier.kindOf("https://example.com/a") != ClipKind.LINK) problems.add("url not classified as link")
            if (ClipClassifier.kindOf("hello world") != ClipKind.TEXT) problems.add("plain text not classified as text")
            if (ClipClassifier.extractUrl("see https://a.com/x ok") == null) problems.add("embedded url not extracted")
            if (problems.isEmpty()) pass() else warn(problems.joinToString("; "))
        }

        c.probe(g, "Emoji data and search") {
            EmojiSearch.attach(c.app)
            val cats = EmojiData.categories
            val total = cats.sumOf { it.emojis.size }
            val unique = cats.flatMap { it.emojis }.toSet().size
            val problems = ArrayList<String>()
            if (cats.any { it.emojis.isEmpty() }) problems.add("empty emoji category")
            if (EmojiSearch.search("heart", 5).isEmpty()) problems.add("no result for heart")
            if (EmojiSearch.search(Sample.AR_HEART, 5).isEmpty()) problems.add("no result for Arabic query")
            val text = "${cats.size} categories, $total emojis, ${total - unique} duplicates"
            if (problems.isEmpty()) pass(text) else warn(problems.joinToString("; ") + "; " + text)
        }

        c.probe(g, "Sentence rules") {
            SentenceRules.attach(c.app)
            val a = SentenceRules.apply("hello  world how are you")
            val b = SentenceRules.dedupe("the the cat")
            val q = SentenceRules.finishQuestion("how are you")
            val s = SentenceRules.spacing("hello ,world")
            if (a.isBlank() || b.isBlank() || q.isBlank() || s.isBlank()) fail("empty result") else pass("dedupe=\"$b\"")
        }

        c.probe(g, "Translate language list") {
            val codes = TranslateLanguages.codes
            val problems = ArrayList<String>()
            if (codes.toSet().size != codes.size) problems.add("duplicate language codes")
            if (TranslateLanguages.displayName("fr").isBlank()) problems.add("blank display name")
            if (problems.isEmpty()) info("${codes.size} languages (network not tested)") else warn(problems.joinToString("; "))
        }
    }

    private suspend fun layouts(c: DiagContext) {
        val g = "Keyboard layouts"

        c.probe(g, "Letter pages (all languages, shift states, number row)") {
            val structural = ArrayList<String>()
            val duplicates = ArrayList<String>()
            var combos = 0
            var keys = 0
            for (lang in KeyboardLanguage.values()) for (shift in ShiftState.values()) for (numRow in listOf(false, true)) {
                combos++
                val state = KeyboardUiState(page = KeyboardPage.LETTERS, language = lang, shiftState = shift, numberRow = numRow)
                val rows = KeyboardLayoutProvider.rows(state)
                keys += rows.sumOf { it.size }
                val tag = "${lang.name}/${shift.name}/numRow=$numRow"
                if (rows.size < 3 && structural.size < 10) structural.add("$tag has ${rows.size} rows")
                if (rows.any { it.isEmpty() } && structural.size < 10) structural.add("$tag has an empty row")
                val emptyChar = rows.any { row -> row.any { k -> val a = k.action; a is KeyAction.Character && a.char.isEmpty() } }
                if (emptyChar && structural.size < 10) structural.add("$tag has a key with an empty character")
                if (shift == ShiftState.OFF && !numRow) {
                    val letters = rows.flatten().mapNotNull { k ->
                        val a = k.action
                        if (a is KeyAction.Character && a.char.isNotEmpty() && a.char.all { it.isLetter() }) a.char else null
                    }
                    val dup = letters.groupBy { it }.filter { it.value.size > 1 }.keys
                    if (dup.isNotEmpty() && duplicates.size < 8) duplicates.add("${lang.name}: ${dup.joinToString(",")}")
                }
            }
            val text = "$combos combinations, $keys keys"
            if (structural.isNotEmpty()) fail(structural.joinToString("; ") + "; " + text)
            else if (duplicates.isNotEmpty()) warn("repeated letters: ${duplicates.joinToString("; ")}; $text")
            else pass(text)
        }

        c.probe(g, "Symbols and number pages") {
            val problems = ArrayList<String>()
            var combos = 0
            val pages = listOf(KeyboardPage.SYMBOLS_1, KeyboardPage.SYMBOLS_2, KeyboardPage.NUMPAD)
            for (lang in KeyboardLanguage.values()) for (page in pages) {
                combos++
                val rows = KeyboardLayoutProvider.rows(KeyboardUiState(page = page, language = lang))
                if ((rows.isEmpty() || rows.any { it.isEmpty() }) && problems.size < 10) problems.add("${lang.name}/${page.name} is empty")
            }
            if (problems.isEmpty()) pass("$combos pages") else fail(problems.joinToString("; "))
        }

        c.probe(g, "Layout variants and previews") {
            val problems = ArrayList<String>()
            var variants = 0
            for (lang in KeyboardLanguage.values()) {
                for (v in KeyboardLayoutProvider.variantsFor(lang)) {
                    variants++
                    if (v.rows.isEmpty() || v.rows.any { it.isEmpty() }) problems.add("${lang.name}/${v.id} has empty rows")
                }
            }
            for (e in EnglishLayout.values()) for (a in ArabicLayout.values()) {
                val en = KeyboardLayoutProvider.previewRows(KeyboardLanguage.ENGLISH, e, a)
                val ar = KeyboardLayoutProvider.previewRows(KeyboardLanguage.ARABIC, e, a)
                if (en.isEmpty() || ar.isEmpty()) problems.add("preview empty for ${e.name}/${a.name}")
            }
            if (problems.isEmpty()) pass("$variants variants") else fail(problems.take(10).joinToString("; "))
        }
    }

    private fun ratio(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private suspend fun themes(c: DiagContext) {
        val g = "Themes"

        c.probe(g, "Keyboard themes (palettes and contrast)") {
            val low = ArrayList<String>()
            var count = 0
            for (theme in KeyboardTheme.values()) for (dark in listOf(false, true)) {
                count++
                val col = semoColors(KeyboardStyle.IOS18, dark, theme)
                val label = theme.name + "/" + if (dark) "dark" else "light"
                val pairs = listOf(
                    "text on key" to ratio(col.text, col.key),
                    "text on panel" to ratio(col.text, col.panel),
                    "text on accent" to ratio(col.textOnAccent, col.keyAccent),
                    "shift active" to ratio(col.onShiftActive, col.shiftActive)
                )
                for ((name, r) in pairs) if (r < 2.5) low.add("$label $name ${fmt(r)}")
            }
            if (low.isEmpty()) pass("$count palettes") else warn("low contrast: " + low.take(10).joinToString("; "))
        }

        c.probe(g, "Theme names") {
            val blank = KeyboardTheme.values().filter { c.app.getString(themeNameRes(it)).isBlank() }
            if (blank.isEmpty()) pass("${KeyboardTheme.values().size} themes") else fail("blank names: ${blank.joinToString { it.name }}")
        }
    }

    private suspend fun performance(c: DiagContext) {
        val g = "Performance"

        c.probe(g, "Suggestion latency (uncached)") {
            fun measure(prefixes: List<String>, lang: KeyboardLanguage): List<Long> {
                val out = ArrayList<Long>()
                for ((i, p) in prefixes.withIndex()) {
                    val s = System.nanoTime()
                    SuggestionEngine.suggest(p, lang, emptyList(), 3, "diag$i$p")
                    out.add((System.nanoTime() - s) / 1000L)
                }
                return out
            }
            val en = measure(Sample.EN_PREFIXES, KeyboardLanguage.ENGLISH)
            val ar = measure(Sample.AR_PREFIXES, KeyboardLanguage.ARABIC)
            val all = en + ar
            val avgMs = all.average() / 1000.0
            val maxMs = (all.maxOrNull() ?: 0L) / 1000.0
            val text = "${all.size} calls, avg ${fmt(avgMs, 2)}ms, max ${fmt(maxMs, 2)}ms (en avg ${fmt(en.average() / 1000.0, 2)}ms, ar avg ${fmt(ar.average() / 1000.0, 2)}ms)"
            if (avgMs > 8.0 || maxMs > 60.0) warn(text) else pass(text)
        }

        c.probe(g, "Layout build time") {
            val state = KeyboardUiState(page = KeyboardPage.LETTERS, language = KeyboardLanguage.ENGLISH)
            val runs = 200
            val s = System.nanoTime()
            repeat(runs) { KeyboardLayoutProvider.rows(state) }
            val avgMs = (System.nanoTime() - s) / 1_000_000.0 / runs
            val text = "avg ${fmt(avgMs, 3)}ms per build"
            if (avgMs > 2.0) warn(text) else pass(text)
        }

        c.probe(g, "Heap after checks") {
            val rt = Runtime.getRuntime()
            val used = rt.totalMemory() - rt.freeMemory()
            val text = "${size(used)} used of ${size(rt.maxMemory())}"
            if (used > rt.maxMemory() * 0.85) warn(text) else info(text)
        }
    }
}
