package com.semo.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.semo.keyboard.domain.model.AppLanguage
import org.json.JSONObject
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object AppLang {
    var english by mutableStateOf(false)

    @Volatile
    private var table: Map<String, String>? = null

    fun resolve(language: AppLanguage): Boolean = when (language) {
        AppLanguage.ENGLISH -> true
        AppLanguage.ARABIC -> false
        AppLanguage.SYSTEM -> Locale.getDefault().language != "ar"
    }

    fun load(context: Context) {
        if (table != null) return
        val loaded = runCatching {
            val text = context.applicationContext.assets.open("strings_en.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            val map = HashMap<String, String>(json.length() * 2)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.getString(k)
            }
            map as Map<String, String>
        }.getOrDefault(emptyMap())
        table = loaded
    }

    fun translate(text: String): String {
        val map = table ?: return text
        val trimmed = text.trim()
        val en = map[trimmed] ?: return text
        val start = text.indexOf(trimmed)
        return text.substring(0, start) + en + text.substring(start + trimmed.length)
    }
}

fun L(text: String): String = if (AppLang.english) AppLang.translate(text) else text

@Composable
fun appString(@StringRes id: Int): String {
    val base = LocalContext.current
    val english = AppLang.english
    return remember(english, id) {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(if (english) "en" else "ar"))
        base.createConfigurationContext(config).getString(id)
    }
}
