package com.semo.keyboard.diag

import android.content.Context
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException

internal class Outcome(val level: DiagLevel, val detail: String)

internal fun pass(detail: String = ""): Outcome = Outcome(DiagLevel.PASS, detail)
internal fun warn(detail: String): Outcome = Outcome(DiagLevel.WARN, detail)
internal fun fail(detail: String): Outcome = Outcome(DiagLevel.FAIL, detail)
internal fun info(detail: String): Outcome = Outcome(DiagLevel.INFO, detail)

internal class DiagContext(val app: Context, private val emit: (DiagItem) -> Unit) {
    val items = ArrayList<DiagItem>()

    fun add(group: String, name: String, outcome: Outcome, millis: Long) {
        val item = DiagItem(group, name, outcome.level, outcome.detail, millis)
        items.add(item)
        emit(item)
    }
}

internal suspend inline fun DiagContext.probe(group: String, name: String, block: () -> Outcome) {
    val start = SystemClock.elapsedRealtime()
    val outcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Outcome(DiagLevel.FAIL, describe(t))
    }
    add(group, name, outcome, SystemClock.elapsedRealtime() - start)
}

internal fun describe(t: Throwable): String {
    val sb = StringBuilder()
    sb.append(t.javaClass.name)
    val message = t.message
    if (!message.isNullOrBlank()) sb.append(": ").append(message.take(300))
    val frames = t.stackTrace.toList()
    val own = frames.filter { it.className.startsWith("com.semo") }.take(4)
    val shown = if (own.isNotEmpty()) own else frames.take(4)
    for (f in shown) sb.append("\n  at ").append(f.toString())
    val cause = t.cause
    if (cause != null && cause !== t) {
        sb.append("\n  caused by ").append(cause.javaClass.name)
        val cm = cause.message
        if (!cm.isNullOrBlank()) sb.append(": ").append(cm.take(200))
    }
    return sb.toString()
}

internal fun size(n: Long): String = when {
    n >= 1_048_576L -> fmt(n / 1_048_576.0) + " MB"
    n >= 1024L -> fmt(n / 1024.0) + " KB"
    else -> "$n B"
}

internal fun fmt(d: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", d)

internal fun stamp(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
