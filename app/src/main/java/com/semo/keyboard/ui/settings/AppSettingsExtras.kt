package com.semo.keyboard.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.data.BackupManager
import com.semo.keyboard.data.CacheCleaner
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.AppLanguage
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.ui.anim.IosEaseInOut
import com.semo.keyboard.ui.appString
import com.semo.keyboard.ui.theme.SemoPalette
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun AppLanguageBlock(repo: SettingsRepository, settings: SemoSettings) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LanguageTile("ع", appString(R.string.lang_arabic), settings.appLanguage == AppLanguage.ARABIC, Modifier.weight(1f)) {
                scope.launch { repo.setAppLanguage(AppLanguage.ARABIC) }
            }
            LanguageTile("Aa", appString(R.string.lang_english), settings.appLanguage == AppLanguage.ENGLISH, Modifier.weight(1f)) {
                scope.launch { repo.setAppLanguage(AppLanguage.ENGLISH) }
            }
            LanguageTile("A/ع", appString(R.string.lang_system), settings.appLanguage == AppLanguage.SYSTEM, Modifier.weight(1f)) {
                scope.launch { repo.setAppLanguage(AppLanguage.SYSTEM) }
            }
        }
        Text(appString(R.string.lang_hint), color = SemoPalette.TextHint, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun LanguageTile(glyph: String, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (selected) 1.03f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "langTileScale"
    )
    val bg by animateColorAsState(
        if (selected) SemoPalette.AccentSoft else SemoPalette.SurfaceHigh,
        tween(260, easing = IosEaseInOut),
        label = "langTileBg"
    )
    val ring by animateColorAsState(
        if (selected) SemoPalette.Accent else Color.Transparent,
        tween(260, easing = IosEaseInOut),
        label = "langTileRing"
    )
    Column(
        modifier = modifier
            .scale(scale)
            .background(bg, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.5.dp, ring), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(if (selected) SemoPalette.Accent else SemoPalette.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = Color.White, fontSize = if (glyph.length > 2) 13.sp else 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            name,
            color = if (selected) SemoPalette.TextPrimary else SemoPalette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
internal fun CacheBlock(repo: SettingsRepository, settings: SemoSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(-1L) }
    var busy by remember { mutableStateOf(false) }
    val doneTemplate = appString(R.string.cache_cleared)
    LaunchedEffect(Unit) { size = CacheCleaner.sizeNow(context) }
    SwitchRow(
        title = appString(R.string.cache_auto),
        iconRes = R.drawable.ic_set_hold,
        tint = IconTeal,
        checked = settings.autoCacheClear,
        subtitle = appString(R.string.cache_auto_sub)
    ) { v -> scope.launch { repo.setAutoCacheClear(v) } }
    Divider()
    val sizeText = if (size < 0L) "…" else Formatter.formatShortFileSize(context, size)
    ActionRow(appString(R.string.cache_clear_now) + " (" + sizeText + ")") {
        if (busy) return@ActionRow
        busy = true
        scope.launch {
            val freed = CacheCleaner.clearNow(context, force = true)
            status = doneTemplate.format(Formatter.formatShortFileSize(context, freed))
            size = CacheCleaner.sizeNow(context)
            busy = false
        }
    }
    if (status.isNotEmpty()) StatusLine(status, true)
}

@Composable
internal fun BackupBlock() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(true) }
    val exportOk = appString(R.string.backup_export_ok)
    val exportFail = appString(R.string.backup_export_fail)
    val importOk = appString(R.string.backup_import_ok)
    val importFail = appString(R.string.backup_import_fail)

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val done = BackupManager.export(context, uri)
            ok = done
            status = if (done) exportOk else exportFail
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = BackupManager.import(context, uri)
            ok = result.ok
            status = if (result.ok) importOk.format(result.keys) else importFail
        }
    }
    ActionRow(appString(R.string.backup_export)) { exporter.launch(backupFileName()) }
    HintRow(appString(R.string.backup_export_sub))
    Divider()
    ActionRow(appString(R.string.backup_import)) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
    HintRow(appString(R.string.backup_import_sub))
    if (status.isNotEmpty()) StatusLine(status, ok)
}

private fun backupFileName(): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
    return "semo-keyboard-backup-$stamp.json"
}

@Composable
private fun HintRow(text: String) {
    Text(
        text,
        color = SemoPalette.TextHint,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    )
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Text(
        text,
        color = if (ok) SemoPalette.Ok else SemoPalette.Warn,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    )
    Spacer(Modifier.height(0.dp))
}
