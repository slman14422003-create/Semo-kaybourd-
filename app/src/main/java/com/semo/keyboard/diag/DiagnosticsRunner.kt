package com.semo.keyboard.diag

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.semo.keyboard.R
import com.semo.keyboard.data.BackupManager
import com.semo.keyboard.data.CacheCleaner
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.ime.SemoKeyboardService
import com.semo.keyboard.ime.VoskModelManager
import com.semo.keyboard.ime.VoskModels
import com.semo.keyboard.util.KeyboardStatusHelper
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object DiagnosticsRunner {

    private class Req(val path: String, val minBytes: Long, val required: Boolean = true)

    private val requiredAssets = listOf(
        Req("dict/en_core.txt", 1_000),
        Req("dict/en_valid.txt", 100_000),
        Req("dict/ar_core.txt", 1_000),
        Req("dict/ar_extra.txt", 100),
        Req("dict/ar_rules.txt", 10),
        Req("dict/ar_fix.txt", 100),
        Req("dict/ar_fix_auto.txt", 0, false),
        Req("corpus/en.txt", 1_000),
        Req("corpus/ar.txt", 1_000),
        Req("strings_en.json", 1_000),
        Req("ai/ruq-5m.onnx", 1_000_000),
        Req("ai/tokenizer.json", 1_000)
    )

    suspend fun run(context: Context, emit: (DiagItem) -> Unit): String = withContext(Dispatchers.Default) {
        val c = DiagContext(context.applicationContext, emit)
        val start = SystemClock.elapsedRealtime()
        environment(c)
        keyboardService(c)
        permissions(c)
        resources(c)
        assets(c)
        data(c)
        DiagEngines.run(c)
        history(c)
        buildReport(c.items, SystemClock.elapsedRealtime() - start)
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()

    private suspend fun environment(c: DiagContext) {
        val g = "Environment"
        val app = c.app

        c.probe(g, "App build") {
            val pi = app.packageManager.getPackageInfo(app.packageName, 0)
            val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            info("${app.packageName} ${pi.versionName} (${versionCodeOf(pi)}) debuggable=$debuggable")
        }

        c.probe(g, "Device") {
            info("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ABI ${Build.SUPPORTED_ABIS.joinToString()}")
        }

        c.probe(g, "Display and locale") {
            val cfg = app.resources.configuration
            val dm = app.resources.displayMetrics
            val night = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            info("locale=${cfg.locales[0]} ${dm.widthPixels}x${dm.heightPixels} ${dm.densityDpi}dpi fontScale=${cfg.fontScale} night=$night")
        }

        c.probe(g, "Memory") {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val rt = Runtime.getRuntime()
            val used = rt.totalMemory() - rt.freeMemory()
            val max = rt.maxMemory()
            val text = "device free ${size(mi.availMem)} of ${size(mi.totalMem)}, lowMemory=${mi.lowMemory}, heap ${size(used)} of ${size(max)}, memoryClass=${am.memoryClass}MB"
            if (mi.lowMemory || used > max * 0.85) warn(text) else pass(text)
        }

        c.probe(g, "Free storage") {
            val free = app.filesDir.usableSpace
            val text = size(free) + " free"
            if (free < 50L * 1_048_576L) fail(text) else if (free < 300L * 1_048_576L) warn(text) else pass(text)
        }

        c.probe(g, "Bundled libraries") {
            // class literals (not strings): R8 renames classes in release builds, so Class.forName("...") would wrongly report them missing
            val checks = listOf<Pair<String, () -> Class<*>>>(
                "onnxruntime" to { ai.onnxruntime.OrtEnvironment::class.java },
                "vosk" to { org.vosk.Model::class.java },
                "jna" to { com.sun.jna.Native::class.java },
                "datastore" to { androidx.datastore.core.DataStore::class.java }
            )
            val missing = checks.filter { (_, load) -> runCatching { load() }.isFailure }.map { it.first }
            if (missing.isEmpty()) pass("onnxruntime, vosk, jna, datastore") else fail("missing classes: ${missing.joinToString()}")
        }
    }

    private suspend fun keyboardService(c: DiagContext) {
        val g = "Keyboard service"
        val app = c.app

        c.probe(g, "Enabled in system settings") {
            if (KeyboardStatusHelper.isKeyboardEnabled(app)) pass() else warn("keyboard is not enabled in system settings")
        }

        c.probe(g, "Selected as current keyboard") {
            if (KeyboardStatusHelper.isKeyboardSelected(app)) pass() else warn("keyboard is not the selected input method")
        }

        c.probe(g, "Service declaration") {
            val si = app.packageManager.getServiceInfo(ComponentName(app, SemoKeyboardService::class.java), 0)
            if (si.permission != Manifest.permission.BIND_INPUT_METHOD) fail("wrong permission: ${si.permission}")
            else pass("exported=${si.exported} enabled=${si.enabled}")
        }

        c.probe(g, "Registered with input method manager") {
            val imm = app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val mine = imm.inputMethodList.firstOrNull { it.packageName == app.packageName }
            if (mine == null) fail("not listed by InputMethodManager (check res/xml/method.xml and the service intent filter)")
            else pass("${mine.id}, subtypes=${mine.subtypeCount}, settings=${mine.settingsActivity}")
        }

        c.probe(g, "Activities and provider") {
            val pm = app.packageManager
            val problems = ArrayList<String>()
            for (cls in listOf("com.semo.keyboard.ui.MainActivity", "com.semo.keyboard.ime.VoicePermissionActivity")) {
                if (runCatching { pm.getActivityInfo(ComponentName(app.packageName, cls), 0) }.isFailure) problems.add(cls)
            }
            if (pm.resolveContentProvider("${app.packageName}.clipfiles", 0) == null) problems.add("clipfiles provider")
            if (problems.isEmpty()) pass() else fail("missing: ${problems.joinToString()}")
        }

        c.probe(g, "Launcher icon alias") {
            val state = app.packageManager.getComponentEnabledSetting(ComponentName(app.packageName, "com.semo.keyboard.ui.MainActivityLauncher"))
            info(
                when (state) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "enabled"
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "disabled"
                    else -> "default"
                }
            )
        }
    }

    private suspend fun permissions(c: DiagContext) {
        val g = "Permissions and connectivity"
        val app = c.app

        c.probe(g, "Microphone permission") {
            val granted = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            info(if (granted) "granted" else "not granted (voice typing asks when used)")
        }

        c.probe(g, "Internet permission") {
            if (app.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) pass() else fail("INTERNET not granted")
        }

        c.probe(g, "Install unknown apps") {
            info(if (app.packageManager.canRequestPackageInstalls()) "allowed (in-app update can install)" else "not allowed yet (asked on first update)")
        }

        c.probe(g, "Speech recognition services") {
            val online = SpeechRecognizer.isRecognitionAvailable(app)
            val onDevice = if (Build.VERSION.SDK_INT >= 31) SpeechRecognizer.isOnDeviceRecognitionAvailable(app) else false
            val text = "system recognizer=$online, on-device=$onDevice"
            if (online || onDevice) pass(text) else warn("$text (voice typing will use Vosk only)")
        }

        c.probe(g, "Network") {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps == null) info("offline")
            else info("online wifi=${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)} cellular=${caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
        }
    }

    private fun localized(app: Context, code: String): Context {
        val cfg = Configuration(app.resources.configuration)
        cfg.setLocale(Locale(code))
        return app.createConfigurationContext(cfg)
    }

    private val placeholderRegex = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[sdf]")

    private fun placeholders(s: String): List<Char> =
        placeholderRegex.findAll(s.replace("%%", "")).map { it.value.last() }.toList().sorted()

    private suspend fun resources(c: DiagContext) {
        val g = "Resources"
        val app = c.app

        c.probe(g, "String resources (en and ar)") {
            val en = localized(app, "en")
            val ar = localized(app, "ar")
            var count = 0
            val problems = ArrayList<String>()
            for (f in R.string::class.java.fields) {
                val id = runCatching { f.getInt(null) }.getOrNull() ?: continue
                count++
                val a = runCatching { en.getString(id) }.getOrNull()
                val b = runCatching { ar.getString(id) }.getOrNull()
                if (a == null || b == null) {
                    if (problems.size < 8) problems.add("${f.name}: unreadable")
                } else if (a.isBlank() || b.isBlank()) {
                    if (problems.size < 8) problems.add("${f.name}: blank")
                } else if (placeholders(a) != placeholders(b)) {
                    if (problems.size < 8) problems.add("${f.name}: placeholders differ")
                }
            }
            if (problems.isEmpty()) pass("$count strings") else fail("${problems.size}+ problems of $count: ${problems.joinToString("; ")}")
        }

        c.probe(g, "Drawables and icons inflate") {
            var count = 0
            val bad = ArrayList<String>()
            for (cls in listOf(R.drawable::class.java, R.mipmap::class.java)) {
                for (f in cls.fields) {
                    val id = runCatching { f.getInt(null) }.getOrNull() ?: continue
                    count++
                    val ok = runCatching { ContextCompat.getDrawable(app, id) != null }.getOrDefault(false)
                    if (!ok) bad.add(f.name)
                }
            }
            if (bad.isEmpty()) pass("$count resources") else fail("${bad.size} of $count failed: ${bad.take(8).joinToString()}")
        }

        c.probe(g, "Sound files") {
            var count = 0
            val bad = ArrayList<String>()
            for (f in R.raw::class.java.fields) {
                val id = runCatching { f.getInt(null) }.getOrNull() ?: continue
                count++
                val head = ByteArray(4)
                val read = runCatching { app.resources.openRawResource(id).use { it.read(head) } }.getOrDefault(-1)
                val tag = String(head, Charsets.ISO_8859_1)
                val ok = read == 4 && (tag == "RIFF" || tag == "OggS")
                if (!ok) bad.add(f.name)
            }
            if (bad.isEmpty()) pass("$count files") else fail("unreadable or wrong format: ${bad.joinToString()}")
        }
    }

    private class AssetStat(val bytes: Long, val lines: Int)

    private fun statAsset(app: Context, path: String): AssetStat {
        val countLines = path.endsWith(".txt")
        var bytes = 0L
        var lines = 0
        app.assets.open(path).use { input ->
            val buf = ByteArray(65_536)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                bytes += n
                if (countLines) {
                    for (i in 0 until n) if (buf[i] == 10.toByte()) lines++
                }
            }
        }
        return AssetStat(bytes, lines)
    }

    private fun readWordSet(app: Context, path: String): Set<String> {
        val out = HashSet<String>()
        app.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { seq ->
            for (line in seq) {
                val t = line.trim()
                if (t.isNotEmpty()) out.add(t)
            }
        }
        return out
    }

    private suspend fun assets(c: DiagContext) {
        val g = "Assets"
        val app = c.app

        for (req in requiredAssets) {
            c.probe(g, req.path) {
                val st = try {
                    statAsset(app, req.path)
                } catch (e: IOException) {
                    null
                }
                if (st == null) {
                    if (req.required) fail("missing from the APK") else info("not present")
                } else if (st.bytes == 0L && !req.required) {
                    info("empty (generated file)")
                } else if (st.bytes < req.minBytes) {
                    fail("too small: ${st.bytes} bytes, expected at least ${req.minBytes}")
                } else {
                    pass(size(st.bytes) + if (req.path.endsWith(".txt")) ", ${st.lines} lines" else "")
                }
            }
        }

        c.probe(g, "tokenizer.json structure") {
            val text = app.assets.open("ai/tokenizer.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val model = JSONObject(text).optJSONObject("model")
            val vocab = model?.optJSONObject("vocab")
            val merges = model?.optJSONArray("merges")?.length() ?: 0
            if (vocab == null) fail("model.vocab missing")
            else if (vocab.length() != 8192) warn("vocab size ${vocab.length()}, the model outputs 8192 logits; merges=$merges. This is not the tokenizer of ruq-5m: replace ai/tokenizer.json with the one from Ruqiya/ruqlm")
            else pass("vocab 8192, merges=$merges")
        }

        c.probe(g, "strings_en.json entries") {
            val text = app.assets.open("strings_en.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            var empty = 0
            var same = 0
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.optString(k)
                if (v.isBlank()) empty++
                if (v == k) same++
            }
            val text2 = "${json.length()} entries, empty=$empty, untranslated=$same"
            if (empty > 0) fail(text2) else if (same > 0) warn(text2) else pass(text2)
        }

        c.probe(g, "ar_fix.txt consistency") {
            val core = readWordSet(app, "dict/ar_core.txt") + readWordSet(app, "dict/ar_extra.txt")
            var total = 0
            var malformed = 0
            var duplicates = 0
            var selfMap = 0
            var conflicts = 0
            val seen = HashSet<String>()
            val examples = ArrayList<String>()
            app.assets.open("dict/ar_fix.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                for (line in seq) {
                    if (line.isBlank() || line.startsWith("#")) continue
                    total++
                    val tab = line.indexOf('\t')
                    if (tab <= 0 || tab == line.length - 1) {
                        malformed++
                        if (examples.size < 3) examples.add("malformed: $line")
                        continue
                    }
                    val key = line.substring(0, tab)
                    val value = line.substring(tab + 1).trim()
                    if (!seen.add(key)) duplicates++
                    if (key == value) selfMap++
                    if (key in core) {
                        conflicts++
                        if (examples.size < 3) examples.add("known word used as key: $key")
                    }
                }
            }
            val text = "$total pairs, malformed=$malformed, duplicates=$duplicates, identity=$selfMap, known-word keys=$conflicts" +
                if (examples.isEmpty()) "" else "; " + examples.joinToString("; ")
            // keys that are also dictionary words are deliberate overrides in the hand-written table (hamza and taa marbuta fixes), so they are only informational
            if (malformed > 0) fail(text) else if (duplicates + selfMap > 0) warn(text) else if (conflicts > 0) info(text) else pass(text)
        }
    }

    private suspend fun data(c: DiagContext) {
        val g = "Data and storage"
        val app = c.app

        c.probe(g, "Settings store") {
            val s = withTimeoutOrNull(4_000L) { SettingsRepository(app).settings.first() }
            if (s == null) fail("settings read timed out after 4s")
            else pass("languages=${s.enabledLanguages.joinToString { it.code }}, theme=${s.keyboardTheme}, size=${s.size}, scale=${s.keyboardScale}")
        }

        c.probe(g, "Backup export") {
            val tmp = File(app.cacheDir, "diag_backup.json")
            try {
                val ok = BackupManager.export(app, Uri.fromFile(tmp))
                if (!ok) {
                    fail("export returned false")
                } else {
                    val root = JSONObject(tmp.readText())
                    pass("${root.optJSONObject("items")?.length() ?: 0} settings exported, ${size(tmp.length())}")
                }
            } finally {
                tmp.delete()
            }
        }

        c.probe(g, "Clipboard database") {
            // read-only connection: opening/closing a second ClipDb helper tried to switch WAL to TRUNCATE while the service held the DB ("database is locked")
            val file = app.getDatabasePath("semo_clips.db")
            if (!file.exists()) {
                info("no database yet")
            } else {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                try {
                    val verdict = db.rawQuery("PRAGMA integrity_check", null).use { cur ->
                        if (cur.moveToFirst()) cur.getString(0) else "no result"
                    }
                    val rows = runCatching {
                        db.rawQuery("SELECT COUNT(*) FROM clips", null).use { cur -> if (cur.moveToFirst()) cur.getInt(0) else 0 }
                    }.getOrDefault(0)
                    if (verdict == "ok") pass("integrity ok, $rows items") else fail("integrity_check: $verdict")
                } finally {
                    db.close()
                }
            }
        }

        c.probe(g, "Cache size") {
            info(size(CacheCleaner.sizeNow(app)))
        }

        c.probe(g, "Downloaded dictionaries") {
            val parts = KeyboardLanguage.values().mapNotNull { l ->
                val b = DictionaryManager.downloadedBytes(app, l)
                if (b > 0L) "${l.code}=${size(b)}" else null
            }
            info(if (parts.isEmpty()) "none (built-in only)" else parts.joinToString())
        }

        c.probe(g, "Offline voice models") {
            val ready = VoskModels.all.keys.filter { VoskModelManager.isReady(app, it) }
            val leftovers = VoskModelManager.leftoverBytes(app)
            val text = "installed: ${if (ready.isEmpty()) "none" else ready.joinToString()}, total ${size(VoskModelManager.totalInstalledBytes(app))}"
            if (leftovers > 0L) warn("$text; partial downloads ${size(leftovers)}") else info(text)
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN($reason)"
    }

    private fun isAbnormalExit(reason: Int): Boolean =
        reason == ApplicationExitInfo.REASON_ANR ||
            reason == ApplicationExitInfo.REASON_CRASH ||
            reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
            reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ||
            reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE

    /** Platform/vendor log lines that come from the OS or the HAL, not from the app, and cannot be fixed here */
    private val SYSTEM_NOISE = listOf(
        "E/ashmem", "W/HWUI", "E/VibratorInfo", "W/libc", "W/InteractionJankMonitor",
        "Setting accessibility_reduce_transparency", "Suspending all threads took"
    )

    private fun logcatTail(): List<String> {
        val out = ArrayList<String>()
        val process = ProcessBuilder("logcat", "-d", "-t", "600", "-v", "time", "*:W").redirectErrorStream(true).start()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { seq ->
                    for (line in seq) synchronized(out) { out.add(line) }
                }
            }
        }
        reader.start()
        reader.join(2_500L)
        if (reader.isAlive) process.destroy()
        return synchronized(out) { ArrayList(out) }
    }

    private suspend fun history(c: DiagContext) {
        val g = "History"
        val app = c.app

        c.probe(g, "Process exit history") {
            if (Build.VERSION.SDK_INT < 30) {
                info("not available before Android 11")
            } else {
                val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val list = am.getHistoricalProcessExitReasons(app.packageName, 0, 12)
                if (list.isEmpty()) {
                    pass("no records")
                } else {
                    val now = System.currentTimeMillis()
                    val recentBad = list.count { isAbnormalExit(it.reason) && now - it.timestamp < 14L * 86_400_000L }
                    val lines = list.take(8).joinToString("\n") {
                        "${stamp(it.timestamp)} ${exitReasonName(it.reason)} pss=${it.pss}KB ${it.description.orEmpty()}"
                    }
                    if (recentBad > 0) warn("$recentBad abnormal exits in the last 14 days\n$lines") else info(lines)
                }
            }
        }

        c.probe(g, "Recorded crashes") {
            val text = CrashLog.read(app)
            if (text.isBlank()) pass("none recorded") else warn(text.take(4_000))
        }

        c.probe(g, "Recent warnings and errors (logcat of this app)") {
            val lines = runCatching { logcatTail() }.getOrDefault(emptyList()).filter { it.contains(" E/") || it.contains(" W/") || it.contains("FATAL") }
                .filterNot { line -> SYSTEM_NOISE.any { line.contains(it) } }
            val errors = lines.count { it.contains(" E/") || it.contains("FATAL") }
            val tail = lines.takeLast(40).joinToString("\n")
            if (lines.isEmpty()) pass("clean") else if (errors > 0) warn("$errors errors, ${lines.size - errors} warnings\n$tail") else info("${lines.size} warnings\n$tail")
        }
    }

    private fun indent(detail: String): String {
        val parts = detail.split('\n')
        val sb = StringBuilder(parts.first())
        for (i in 1 until parts.size) sb.append("\n    ").append(parts[i])
        return sb.toString()
    }

    private fun buildReport(items: List<DiagItem>, totalMs: Long): String {
        val sb = StringBuilder()
        sb.append("SEMO KEYBOARD DIAGNOSTICS\n")
        sb.append("generated: ").append(stamp(System.currentTimeMillis())).append('\n')
        for (name in listOf("App build", "Device", "Display and locale")) {
            val item = items.firstOrNull { it.name == name } ?: continue
            sb.append(name.lowercase(Locale.US)).append(": ").append(item.detail).append('\n')
        }
        val pass = items.count { it.level == DiagLevel.PASS }
        val warn = items.count { it.level == DiagLevel.WARN }
        val fail = items.count { it.level == DiagLevel.FAIL }
        val info = items.count { it.level == DiagLevel.INFO }
        sb.append("summary: pass=$pass warn=$warn fail=$fail info=$info, ${items.size} checks in ${totalMs}ms\n\n")

        sb.append("== ISSUES ==\n")
        val issues = items.filter { it.level == DiagLevel.FAIL } + items.filter { it.level == DiagLevel.WARN }
        if (issues.isEmpty()) sb.append("none\n")
        for (item in issues) {
            sb.append('[').append(item.level.name).append("] ").append(item.group).append(" / ").append(item.name)
            if (item.detail.isNotBlank()) sb.append(": ").append(indent(item.detail))
            sb.append('\n')
        }

        sb.append("\n== FULL LOG ==")
        var current = ""
        for (item in items) {
            if (item.group != current) {
                current = item.group
                sb.append("\n\n-- ").append(current).append(" --")
            }
            sb.append("\n[").append(item.level.name).append("] ").append(item.name).append(" (").append(item.millis).append(" ms)")
            if (item.detail.isNotBlank()) sb.append(": ").append(indent(item.detail))
        }
        sb.append('\n')
        return sb.toString().take(90_000)
    }
}
