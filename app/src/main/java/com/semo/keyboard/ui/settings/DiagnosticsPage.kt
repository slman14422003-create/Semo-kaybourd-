package com.semo.keyboard.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.diag.CrashLog
import com.semo.keyboard.diag.DiagItem
import com.semo.keyboard.diag.DiagLevel
import com.semo.keyboard.diag.DiagnosticsRunner
import com.semo.keyboard.ui.appString
import com.semo.keyboard.ui.theme.SemoPalette
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val DiagFail = Color(0xFFFF453A)
private val DiagInfo = Color(0xFF8E8E93)

private fun levelColor(level: DiagLevel): Color = when (level) {
    DiagLevel.PASS -> SemoPalette.Ok
    DiagLevel.WARN -> SemoPalette.Warn
    DiagLevel.FAIL -> DiagFail
    DiagLevel.INFO -> DiagInfo
}

@Composable
internal fun DiagnosticsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<DiagItem>() }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    var onlyIssues by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }

    fun start() {
        if (running) return
        running = true
        copied = false
        report = ""
        items.clear()
        scope.launch {
            val text = try {
                DiagnosticsRunner.run(context) { item -> main.post { items.add(item) } }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                "DIAGNOSTICS FAILED\n" + t.stackTraceToString().take(4_000)
            }
            report = text
            running = false
        }
    }

    LaunchedEffect(Unit) { start() }

    val fails = items.count { it.level == DiagLevel.FAIL }
    val warns = items.count { it.level == DiagLevel.WARN }
    val passes = items.count { it.level == DiagLevel.PASS }
    val statusColor = if (running) SemoPalette.TextSecondary else if (fails > 0) DiagFail else if (warns > 0) SemoPalette.Warn else SemoPalette.Ok
    val statusText = if (running) {
        appString(R.string.diag_running).format(Locale.US, items.size)
    } else if (fails == 0 && warns == 0) {
        appString(R.string.diag_none)
    } else {
        appString(R.string.diag_summary).format(Locale.US, passes, warns, fails)
    }

    SubPage(appString(R.string.diag_title), onBack) {
        Section {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(statusText, color = statusColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    appString(R.string.diag_counts).format(Locale.US, items.size, passes, warns, fails),
                    color = SemoPalette.TextHint,
                    fontSize = 12.sp
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SolidButton(
                text = if (copied) appString(R.string.diag_copied) else appString(R.string.diag_copy),
                enabled = report.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("semo-diagnostics", report))
                copied = true
            }
            SolidButton(
                text = appString(R.string.diag_share),
                enabled = report.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Semo diagnostics")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
            SolidButton(
                text = appString(R.string.diag_rerun),
                enabled = !running,
                modifier = Modifier.weight(1f)
            ) { start() }
        }

        Segmented(
            options = listOf(false to appString(R.string.diag_all), true to appString(R.string.diag_issues)),
            selected = onlyIssues,
            onSelect = { onlyIssues = it }
        )

        val visible = if (onlyIssues) items.filter { it.level == DiagLevel.FAIL || it.level == DiagLevel.WARN } else items.toList()
        if (visible.isEmpty() && !running && onlyIssues) Hint(appString(R.string.diag_none))

        visible.groupBy { it.group }.forEach { (group, list) ->
            Section(group) {
                list.forEachIndexed { index, item ->
                    if (index > 0) Divider()
                    DiagRow(item)
                }
            }
        }

        Section {
            ActionRow(appString(R.string.diag_clear_crash)) { CrashLog.clear(context) }
        }

        Hint(appString(R.string.diag_hint))
    }
}

@Composable
private fun DiagRow(item: DiagItem) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(levelColor(item.level), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    item.name,
                    color = SemoPalette.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("${item.millis} ms", color = SemoPalette.TextHint, fontSize = 11.sp)
            }
            if (item.detail.isNotBlank()) {
                Text(
                    item.detail.take(1_200),
                    color = SemoPalette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 19.dp)
                )
            }
        }
    }
}
