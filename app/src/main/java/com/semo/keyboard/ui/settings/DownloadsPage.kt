package com.semo.keyboard.ui.settings

import android.text.format.Formatter
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.data.ArabicPacks
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.ime.VoskModelManager
import com.semo.keyboard.ui.L
import com.semo.keyboard.ui.appString
import com.semo.keyboard.ui.theme.SemoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** كل ما يخص التنزيل بمكان واحد: حزم الصوت، قواميس اللغات ونماذج الجمل، والتخزين والكاش (التحديثات لها زرها بصفحة «حول») */
@Composable
internal fun DownloadsPage(repo: SettingsRepository, settings: SemoSettings, onBack: () -> Unit) {
    SubPage(L("التنزيلات والتخزين"), onBack) {
        StorageSummarySection()
        VoiceDownloadsBlock()
        LanguageDownloadsBlock(repo, settings)
        ArabicPacksBlock()
        Section(L("الكاش")) {
            CacheBlock(repo, settings)
        }
    }
}

@Composable
private fun StorageSummarySection() {
    val context = LocalContext.current
    val voiceStates by VoskModelManager.states.collectAsState()
    val active by DictionaryManager.activeLanguage.collectAsState()
    var voiceBytes by remember { mutableStateOf(0L) }
    var wordBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(voiceStates, active) {
        withContext(Dispatchers.IO) {
            voiceBytes = VoskModelManager.totalInstalledBytes(context)
            wordBytes = KeyboardLanguage.values().sumOf { DictionaryManager.downloadedBytes(context, it) } +
                ArabicPacks.totalBytes(context)
        }
    }
    Section(L("المساحة المستعملة")) {
        SummaryRow(L("حزم الصوت"), Formatter.formatShortFileSize(context, voiceBytes))
        Divider()
        SummaryRow(L("القواميس ونماذج الجمل"), Formatter.formatShortFileSize(context, wordBytes))
    }
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = SemoPalette.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
internal fun LanguageDownloadsBlock(repo: SettingsRepository, settings: SemoSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active by DictionaryManager.activeLanguage.collectAsState()
    val progress by DictionaryManager.progressText.collectAsState()
    val result by DictionaryManager.lastResult.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    Section(L("القواميس ونماذج الجمل")) {
        Text(
            L("لكل لغة قاموس ونموذج جمل للإكمال وتوقّع الكلمة التالية، بيشتغلوا بعد التنزيل بدون إنترنت. ") +
                L("القواميس من FrequencyWords (CC-BY-SA 4.0) والجمل من Tatoeba (CC-BY 2.0 FR)."),
            color = SemoPalette.TextHint,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(14.dp)
        )
        KeyboardLanguage.values().forEach { lang ->
            Divider()
            val hasWords = remember(refresh, active) { DictionaryManager.hasWords(context, lang) }
            val hasModel = remember(refresh, active) { DictionaryManager.hasModel(context, lang) }
            val bytes = remember(refresh, active) { DictionaryManager.downloadedBytes(context, lang) }
            val running = active == lang
            val outdated = remember(refresh, active) { DictionaryManager.isOutdated(context, lang) }
            val state = when {
                running -> progress.ifEmpty { L("جاري الاتصال...") }
                outdated -> L("نسخة قديمة أصغر وأقل دقة، اضغط «تحديث» لقاموس أضخم ونموذج جمل أكبر")
                hasWords && hasModel -> L("منزَّلة: قاموس + نموذج جمل") + " · " + Formatter.formatShortFileSize(context, bytes)
                hasWords -> L("منزَّلة: قاموس فقط") + " · " + Formatter.formatShortFileSize(context, bytes)
                lang.builtIn -> L("مدمجة (تنزيل القاموس الكامل اختياري)")
                else -> L("غير منزَّلة")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(R.drawable.ic_ios_globe, if (hasWords) IconGreen else IconBlue)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(lang.nativeName, color = SemoPalette.TextPrimary, fontSize = 15.sp)
                    Text(state, color = SemoPalette.TextHint, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SolidButton(
                    text = if (running) L("جاري التنزيل...") else if (hasWords) L("تحديث") else L("تنزيل"),
                    enabled = active == null,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            DictionaryManager.download(context, lang) { }
                        }
                        outcome.onSuccess { info ->
                            DictionaryManager.lastResult.value =
                                "${L("تم تنزيل ")}${lang.nativeName}: ${info.words}${L(" كلمة")}" +
                                    if (info.sentences > 0) "${L(" + نموذج من ")}${info.sentences}${L(" جملة")}"
                                    else L(" (تعذّر نموذج الجمل، القاموس يعمل)")
                            if (lang !in settings.enabledLanguages) repo.setEnabledLanguages(settings.enabledLanguages + lang)
                        }.onFailure {
                            DictionaryManager.lastResult.value = L("فشل التنزيل، تأكد من الاتصال بالإنترنت ثم أعد المحاولة")
                        }
                        refresh++
                    }
                }
                if (hasWords) {
                    SolidButton(text = L("حذف"), enabled = active == null, modifier = Modifier.weight(1f)) {
                        scope.launch {
                            withContext(Dispatchers.IO) { DictionaryManager.delete(context, lang) }
                            DictionaryManager.lastResult.value = "${L("حُذف ما نُزِّل للغة ")}${lang.nativeName}"
                            refresh++
                        }
                    }
                }
            }
        }
        if (result.isNotEmpty() && active == null) {
            Text(result, color = SemoPalette.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
        }
    }
}

@Composable
private fun ArabicPacksBlock() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active by ArabicPacks.activePack.collectAsState()
    val progress by ArabicPacks.progressText.collectAsState()
    val result by ArabicPacks.lastResult.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    val connecting = appString(R.string.packs_connecting)
    val installedLabel = appString(R.string.packs_installed)
    val notInstalledLabel = appString(R.string.packs_not_installed)
    val wordsLabel = appString(R.string.packs_words)
    val doneLabel = appString(R.string.packs_done)
    val failedLabel = appString(R.string.packs_failed)
    val deletedLabel = appString(R.string.packs_deleted)
    Section(appString(R.string.packs_section)) {
        Text(
            appString(R.string.packs_intro),
            color = SemoPalette.TextHint,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(14.dp)
        )
        ArabicPacks.all.forEach { pack ->
            Divider()
            val installed = remember(refresh, active) { ArabicPacks.isInstalled(context, pack) }
            val bytes = remember(refresh, active) { ArabicPacks.installedBytes(context, pack) }
            val count = remember(refresh, active) { if (installed) ArabicPacks.wordCount(context, pack) else 0 }
            val running = active == pack.id
            val busy = active != null
            val packTitle = appString(pack.titleRes)
            val state = when {
                running -> progress.ifEmpty { connecting }
                installed -> "$installedLabel · $count $wordsLabel · " + Formatter.formatShortFileSize(context, bytes)
                else -> notInstalledLabel
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(R.drawable.ic_set_dictionary, if (installed) IconGreen else IconBlue)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(packTitle, color = SemoPalette.TextPrimary, fontSize = 15.sp)
                    Text(appString(pack.descRes), color = SemoPalette.TextHint, fontSize = 12.sp, lineHeight = 17.sp)
                    Text(state, color = SemoPalette.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SolidButton(
                    text = if (running) appString(R.string.packs_downloading) else if (installed) appString(R.string.packs_update) else appString(R.string.packs_download),
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) { ArabicPacks.download(context, pack) { } }
                        outcome.onSuccess { n ->
                            ArabicPacks.lastResult.value = "$doneLabel: $packTitle · $n $wordsLabel"
                        }.onFailure {
                            ArabicPacks.lastResult.value = failedLabel
                        }
                        refresh++
                    }
                }
                if (installed) {
                    SolidButton(text = appString(R.string.packs_delete), enabled = !busy, modifier = Modifier.weight(1f)) {
                        scope.launch {
                            withContext(Dispatchers.IO) { ArabicPacks.delete(context, pack) }
                            ArabicPacks.lastResult.value = deletedLabel
                            refresh++
                        }
                    }
                }
            }
        }
        if (result.isNotEmpty() && active == null) {
            Text(result, color = SemoPalette.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
        }
    }
}
