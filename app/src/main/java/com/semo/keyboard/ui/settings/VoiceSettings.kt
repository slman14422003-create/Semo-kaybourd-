package com.semo.keyboard.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.VoiceDialect
import com.semo.keyboard.ime.OfflinePack
import com.semo.keyboard.ime.OfflineSpeech
import com.semo.keyboard.ime.VoiceLanguages
import com.semo.keyboard.ui.appString
import com.semo.keyboard.ui.theme.SemoPalette
import kotlinx.coroutines.launch

@StringRes
internal fun dialectRes(dialect: VoiceDialect): Int = when (dialect) {
    VoiceDialect.AUTO -> R.string.voice_dialect_auto
    VoiceDialect.SYRIA -> R.string.voice_dialect_sy
    VoiceDialect.LEBANON -> R.string.voice_dialect_lb
    VoiceDialect.JORDAN -> R.string.voice_dialect_jo
    VoiceDialect.PALESTINE -> R.string.voice_dialect_ps
    VoiceDialect.IRAQ -> R.string.voice_dialect_iq
    VoiceDialect.EGYPT -> R.string.voice_dialect_eg
    VoiceDialect.SAUDI -> R.string.voice_dialect_sa
    VoiceDialect.UAE -> R.string.voice_dialect_ae
    VoiceDialect.KUWAIT -> R.string.voice_dialect_kw
    VoiceDialect.QATAR -> R.string.voice_dialect_qa
    VoiceDialect.YEMEN -> R.string.voice_dialect_ye
    VoiceDialect.SUDAN -> R.string.voice_dialect_sd
    VoiceDialect.LIBYA -> R.string.voice_dialect_ly
    VoiceDialect.TUNISIA -> R.string.voice_dialect_tn
    VoiceDialect.ALGERIA -> R.string.voice_dialect_dz
    VoiceDialect.MOROCCO -> R.string.voice_dialect_ma
}

private class PackRow(val tag: String, val name: String)

@Composable
internal fun VoicePage(repo: SettingsRepository, settings: SemoSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs = remember { mutableStateMapOf<String, OfflinePack>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val progress = remember { mutableStateMapOf<String, Int>() }
    var message by remember { mutableStateOf("") }

    val arabicName = appString(R.string.lang_arabic)
    val rows = remember(settings.voiceDialect, settings.enabledLanguages) {
        val list = ArrayList<PackRow>()
        list.add(PackRow(VoiceLanguages.tagFor(KeyboardLanguage.ARABIC, dialect = settings.voiceDialect), arabicName))
        settings.enabledLanguages.filter { it != KeyboardLanguage.ARABIC }.forEach { lang ->
            list.add(PackRow(VoiceLanguages.tagFor(lang), lang.nativeName))
        }
        list.distinctBy { it.tag }
    }

    LaunchedEffect(rows) {
        rows.forEach { row ->
            OfflineSpeech.check(context, row.tag) { packs[row.tag] = it }
        }
    }

    val doneText = appString(R.string.voice_pack_done)
    val failedText = appString(R.string.voice_pack_failed)
    val checkingText = appString(R.string.voice_pack_checking)
    val installedText = appString(R.string.voice_pack_installed)
    val missingText = appString(R.string.voice_pack_downloadable)
    val pendingText = appString(R.string.voice_pack_pending)
    val unsupportedText = appString(R.string.voice_pack_unsupported)
    val downloadingText = appString(R.string.voice_pack_downloading)
    val downloadLabel = appString(R.string.voice_pack_download)

    SubPage(appString(R.string.voice_title), onBack) {
        Section(appString(R.string.voice_dialect_title)) {
            Text(
                appString(R.string.voice_dialect_hint),
                color = SemoPalette.TextHint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(14.dp)
            )
            VoiceDialect.values().forEach { dialect ->
                Divider()
                val selected = dialect == settings.voiceDialect
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { repo.setVoiceDialect(dialect) } }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(R.drawable.ic_ios_globe, if (selected) IconGreen else IconGray)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        appString(dialectRes(dialect)),
                        color = SemoPalette.TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    CheckDot(selected, 22)
                }
            }
        }

        Section(appString(R.string.voice_offline_title)) {
            Text(
                appString(R.string.voice_offline_hint),
                color = SemoPalette.TextHint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(14.dp)
            )
            SwitchRow(
                appString(R.string.voice_offline_pref_title),
                R.drawable.ic_ios_mic,
                IconOrange,
                settings.voiceOfflineFirst,
                subtitle = appString(R.string.voice_offline_pref_hint)
            ) { scope.launch { repo.setVoiceOfflineFirst(it) } }
            rows.forEach { row ->
                Divider()
                val pack = packs[row.tag]
                val working = busy[row.tag] == true
                val status = when {
                    working -> downloadingText + (progress[row.tag]?.takeIf { it > 0 }?.let { " $it%" } ?: "")
                    pack == null -> checkingText
                    pack == OfflinePack.INSTALLED -> installedText
                    pack == OfflinePack.PENDING -> pendingText
                    pack == OfflinePack.DOWNLOADABLE -> missingText
                    else -> unsupportedText
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(R.drawable.ic_ios_mic, if (pack == OfflinePack.INSTALLED) IconGreen else IconBlue)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.name, color = SemoPalette.TextPrimary, fontSize = 15.sp)
                        Text("${row.tag} · $status", color = SemoPalette.TextHint, fontSize = 12.sp)
                    }
                    if (pack == OfflinePack.DOWNLOADABLE || pack == OfflinePack.PENDING || working) {
                        SolidButton(
                            text = downloadLabel,
                            enabled = !working,
                            modifier = Modifier.width(104.dp)
                        ) {
                            busy[row.tag] = true
                            progress[row.tag] = 0
                            message = ""
                            OfflineSpeech.download(
                                context,
                                row.tag,
                                { percent -> progress[row.tag] = percent },
                                { result ->
                                    busy[row.tag] = false
                                    packs[row.tag] = result
                                    message = if (result == OfflinePack.INSTALLED) doneText else failedText
                                }
                            )
                        }
                    }
                }
            }
            if (message.isNotEmpty()) {
                Text(message, color = SemoPalette.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    appString(if (OfflineSpeech.canManage) R.string.voice_unsupported_hint else R.string.voice_old_android_hint),
                    color = SemoPalette.TextHint,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                SolidButton(
                    text = appString(R.string.voice_open_system),
                    enabled = true,
                    modifier = Modifier.fillMaxWidth()
                ) { OfflineSpeech.openSystemSettings(context) }
            }
        }
    }
}
