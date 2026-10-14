package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.clipDisplayText
import com.semo.keyboard.domain.model.isClipImage

/** نوع عنصر الحافظة للتصنيف والتصفية بلوحة الحافظة */
enum class ClipKind { TEXT, LINK, CODE, IMAGE }

/**
 * يصنّف عنصر الحافظة من نصه فقط (بلا قراءة ملفات): صورة، رابط، كود، أو نص عادي.
 * للنص الطويل يُصنَّف من المعاينة المخزّنة معه. منطق بحت بلا اعتماد على Android.
 */
object ClipClassifier {

    private val linkRegex = Regex("""^(?:https?://|ftp://|www\.)\S+$""", RegexOption.IGNORE_CASE)
    private val bareDomainRegex = Regex("""^[a-z0-9][a-z0-9\-]*(?:\.[a-z0-9\-]+)*\.(?:com|net|org|io|dev|app|me|co|ai|info|tv|sa|ae|eg|jo|iq|sy|ly|dz|ma|tn)(?:[/?#]\S*)?$""", RegexOption.IGNORE_CASE)
    // بحث عن رابط داخل نص أطول (رابط مرفق بسطر عنوان أو نص مقتبس حوله): غير مرتبط ببداية/نهاية السطر
    private val embeddedLinkRegex = Regex("""(?:https?://|ftp://|www\.)[^\s"'<>\u0600-\u06FF]+""", RegexOption.IGNORE_CASE)

    /** كلمات مفتاحية تدل غالبًا على كود برمجي (تُطابق في بداية سطر) */
    private val codeLineRegex = Regex(
        """(?m)^\s*(?:import |package |from \S+ import |#include|using |def |class |fun |val |var |let |const |function |public |private |protected |static |return\b|if\s*\(|for\s*\(|while\s*\(|else\b|<\?php|<[a-z!/][^>]*>|@[A-Za-z]+|\}\s*$|\{\s*$|//|/\*|#!|SELECT |INSERT |UPDATE |DELETE |CREATE TABLE)"""
    )

    fun kindOf(item: ClipItem): ClipKind = kindOf(item.text)

    fun kindOf(stored: String): ClipKind {
        if (stored.isClipImage()) return ClipKind.IMAGE
        val t = stored.clipDisplayText().trim()
        if (t.isEmpty()) return ClipKind.TEXT
        // الحالة الشائعة: النص كله رابط واحد بلا شيء آخر
        if (!t.contains('\n') && !t.contains(' ') && (linkRegex.matches(t) || bareDomainRegex.matches(t))) return ClipKind.LINK
        // رابط مرفق بنص قصير حوله (عنوان مُقتبَس، أو "via @user")، طالما الرابط يشكّل جزءًا كبيرًا من النص القصير
        if (t.length <= 300) {
            val found = embeddedLinkRegex.find(t)
            if (found != null && found.value.length >= (t.length * 0.4).toInt().coerceAtLeast(8)) return ClipKind.LINK
        }
        return if (looksLikeCode(t)) ClipKind.CODE else ClipKind.TEXT
    }

    /** يستخرج أول رابط داخل النص (للفتح بالمتصفح)، أو null لو ما فيه */
    fun extractUrl(stored: String): String? {
        val t = stored.clipDisplayText().trim()
        if (t.isEmpty()) return null
        if (linkRegex.matches(t)) return normalizeUrl(t)
        if (bareDomainRegex.matches(t)) return normalizeUrl(t)
        val found = embeddedLinkRegex.find(t)?.value ?: return null
        return normalizeUrl(found.trimEnd('.', ',', ')', ']', '؛', '،'))
    }

    private fun normalizeUrl(url: String): String =
        if (url.startsWith("http://", true) || url.startsWith("https://", true) || url.startsWith("ftp://", true)) url
        else "https://$url"

    /** كود: عدة أسطر فيها كلمات/رموز برمجية، أو سطر واحد بإشارات برمجية واضحة (=> ; {} () ->) */
    fun looksLikeCode(text: String): Boolean {
        val t = text.trim()
        if (t.length < 6) return false
        if (t.startsWith("```")) return true
        val lines = t.lineSequence().filter { it.isNotBlank() }.take(40).toList()
        val codeLines = lines.count { codeLineRegex.containsMatchIn(it) }
        val symbols = t.count { it in "{}();=<>[]\\|&_$#" }
        val density = symbols.toFloat() / t.length
        val indented = lines.count { it.startsWith("    ") || it.startsWith("\t") }
        // نص عربي/إنكليزي عادي فيه علامات ترقيم قليلة، أما الكود فكثافة رموزه عالية
        if (lines.size >= 2) {
            if (codeLines >= 2) return true
            if (indented >= 2 && density > 0.02f) return true
            if (density > 0.07f) return true
        } else {
            if (t.contains("=>") || t.contains("->") || t.contains("::")) return density > 0.04f
            if (t.endsWith(";") && density > 0.04f) return true
            if (density > 0.12f && t.length > 12) return true
        }
        return false
    }
}
