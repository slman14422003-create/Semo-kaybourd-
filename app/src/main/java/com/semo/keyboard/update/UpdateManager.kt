package com.semo.keyboard.update

import com.semo.keyboard.ui.L
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** معلومات آخر إصدار منشور على ريليس جيتهاب */
data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSizeBytes: Long
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

sealed class DownloadState {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

/**
 * يتحقق من آخر إصدار على ريليس جيتهاب للمشروع، ويُنزّل ملف الـ APK منه ويُشغّل تثبيته.
 * لا تبعيات خارجية (HttpURLConnection + org.json فقط، متوفرتان بأندرويد مباشرة).
 */
object UpdateManager {

    private const val REPO = "slman14422003-create/Semo-kaybourd-"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 20_000

    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val body = httpGetString(API_URL)
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name")
            if (tag.isBlank()) return@withContext UpdateCheckResult.Error(L("تعذّرت قراءة بيانات الإصدار"))
            val normalized = tag.removePrefix("v").removePrefix("V").trim()

            val assets: JSONArray = obj.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkName = ""
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                    apkName = name
                    apkSize = a.optLong("size", 0L)
                    break
                }
            }
            if (apkUrl == null) return@withContext UpdateCheckResult.Error(L("لا يوجد ملف APK مرفق بآخر إصدار"))

            val current = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "0"

            if (!isNewer(normalized, current)) return@withContext UpdateCheckResult.UpToDate

            val notes = obj.optString("body").trim()
            UpdateCheckResult.Available(UpdateInfo(tag, normalized, notes, apkUrl, apkName, apkSize))
        } catch (e: Exception) {
            UpdateCheckResult.Error(describeError(e))
        }
    }

    /** مقارنة إصدارات بسيطة بالأرقام (1.2.10 أحدث من 1.2.9 مثلًا)، وتتجاهل أي لاحقة نصية */
    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun httpGetString(urlStr: String): String {
        var connection = openConnection(urlStr, "application/vnd.github+json")
        try {
            connection = followRedirects(connection)
            if (connection.responseCode !in 200..299) {
                if (connection.responseCode == 404) throw IOException(L("لا يوجد إصدار منشور بالمستودع بعد (HTTP 404)"))
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val target = File(dir, info.apkName.ifBlank { "semo-update.apk" })

        var connection = openConnection(info.apkUrl)
        connection = followRedirects(connection)
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP ${connection.responseCode}")
        }
        val total = connection.contentLengthLong.let { if (it > 0) it else info.apkSizeBytes }
        try {
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    /** يفتح نافذة تثبيت الحزمة عبر FileProvider (نفس مزوّد ملفات صور الحافظة بالتطبيق) */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.clipfiles", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun canInstallUnknownApps(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** يفتح شاشة نظام للسماح بتثبيت تطبيقات من هذا المصدر (مطلوبة قبل أول تثبيت فقط) */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun openConnection(urlStr: String, accept: String? = null): HttpURLConnection {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Semo-Keyboard-Updater")
        if (accept != null) connection.setRequestProperty("Accept", accept)
        return connection
    }

    /** GitHub يُعيد توجيه تنزيل الأصول لعنوان مختلف (amazonaws)، وHttpURLConnection لا يتبعه تلقائيًا بين النطاقات */
    private fun followRedirects(start: HttpURLConnection): HttpURLConnection {
        var connection = start
        var redirects = 0
        while (connection.responseCode in REDIRECT_CODES && redirects < 5) {
            val location = connection.getHeaderField("Location") ?: break
            connection.disconnect()
            connection = openConnection(java.net.URL(java.net.URL(connection.url.toString()), location).toString())
            redirects++
        }
        return connection
    }

    private fun describeError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> L("لا يوجد اتصال بالإنترنت")
        is java.net.SocketTimeoutException -> L("انتهت مهلة الاتصال، حاول مجددًا")
        else -> e.message ?: L("تعذّر التحقق من التحديثات")
    }

    private val REDIRECT_CODES = intArrayOf(301, 302, 303, 307, 308)
}
