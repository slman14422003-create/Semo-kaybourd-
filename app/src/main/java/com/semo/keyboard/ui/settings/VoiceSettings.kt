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
import androidx.compose.runtime.collectAsState
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
import com.semo.keyboard.ime.VoskFailure
import com.semo.keyboard.ime.VoskModelManager
import com.semo.keyboard.ime.VoskModels
import com.semo.keyboard.ime.VoskState
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

@Composable
internal fun VoiceDownloadsBlock() {
    val context = LocalContext.current
    val allStates by VoskModelManager.states.collectAsState()
    LaunchedEffect(Unit) { VoskModelManager.refresh(context) }

    val downloadLabel = appString(R.string.voice_pack_download)

    run {
        Section(appString(R.string.voice_builtin_title)) {
            Text(
                appString(R.string.voice_builtin_hint),
                color = SemoPalette.TextHint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(14.dp)
            )
            KeyboardLanguage.values().forEach { lang ->
                val spec = VoskModels.specFor(lang.code)
                Divider()
                if (spec == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(R.drawable.ic_ios_mic, IconGray)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lang.nativeName, color = SemoPalette.TextPrimary, fontSize = 15.sp)
                            Text(appString(R.string.voice_lang_no_model), color = SemoPalette.TextHint, fontSize = 12.sp)
                        }
                    }
                } else {
                    val st = VoskModelManager.stateOf(allStates, lang.code)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(R.drawable.ic_ios_mic, if (st == VoskState.Ready) IconGreen else IconBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lang.nativeName, color = SemoPalette.TextPrimary, fontSize = 15.sp)
                            val status = when (st) {
                                is VoskState.Downloading -> appString(R.string.voice_builtin_status_downloading) + " " + st.percent + "%"
                                is VoskState.Extracting -> appString(R.string.voice_builtin_status_extracting) + " " + st.percent + "%"
                                VoskState.Ready -> appString(R.string.voice_builtin_status_ready) + " · " +
                                    android.text.format.Formatter.formatShortFileSize(
                                        context,
                                        remember(st) { VoskModelManager.installedBytes(context, lang.code) }
                                    )
                                else -> appString(R.string.voice_builtin_status_missing) + " (" + spec.megabytes + " MB)"
                            }
                            Text(status, color = SemoPalette.TextHint, fontSize = 12.sp)
                        }
                        when (st) {
                            is VoskState.Downloading, is VoskState.Extracting -> SolidButton(
                                text = appString(R.string.voice_builtin_cancel),
                                enabled = true,
                                modifier = Modifier.width(104.dp)
                            ) { VoskModelManager.cancel(lang.code) }
                            VoskState.Ready -> SolidButton(
                                text = appString(R.string.voice_builtin_delete),
                                enabled = true,
                                modifier = Modifier.width(104.dp)
                            ) { VoskModelManager.delete(context, lang.code) }
                            else -> SolidButton(
                                text = downloadLabel,
                                enabled = true,
                                modifier = Modifier.width(104.dp)
                            ) { VoskModelManager.download(context, lang.code) }
                        }
                    }
                    (st as? VoskState.Failed)?.let { failed ->
                        Text(
                            appString(
                                when (failed.reason) {
                                    VoskFailure.NETWORK -> R.string.voice_builtin_failed_network
                                    VoskFailure.SPACE -> R.string.voice_builtin_failed_space
                                    VoskFailure.CORRUPT -> R.string.voice_builtin_failed_corrupt
                                    VoskFailure.UNKNOWN -> R.string.voice_builtin_failed_unknown
                                }
                            ),
                            color = SemoPalette.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Divider()
            Text(
                appString(R.string.voice_builtin_always_on),
                color = SemoPalette.TextHint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}
