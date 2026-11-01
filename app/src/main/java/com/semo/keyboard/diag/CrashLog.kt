package com.semo.keyboard.diag

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {

    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 24_000

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            if (previous != null) previous.uncaughtException(thread, error)
        }
    }

    private fun record(app: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "time=$stamp thread=${thread.name} pid=${android.os.Process.myPid()}\n$trace"
        val merged = entry + "\n----\n" + read(app)
        File(app.filesDir, FILE).writeText(merged.take(MAX_CHARS))
    }

    fun read(context: Context): String = runCatching {
        val f = File(context.applicationContext.filesDir, FILE)
        if (f.exists()) f.readText() else ""
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
