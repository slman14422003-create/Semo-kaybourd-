package com.semo.keyboard.domain.logic

import android.content.Context

object AutoCorrect {

    private val english: Map<String, String> = mapOf(
        "i" to "I", "im" to "I'm", "ive" to "I've", "i'm" to "I'm", "i'll" to "I'll", "i've" to "I've", "i'd" to "I'd",
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't", "doesnt" to "doesn't",
        "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
        "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't",
        "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't",
        "youre" to "you're", "theyre" to "they're", "thats" to "that's", "whats" to "what's",
        "hes" to "he's", "shes" to "she's",
        "teh" to "the", "adn" to "and", "taht" to "that", "thier" to "their", "becuase" to "because",
        "recieve" to "receive", "definately" to "definitely", "seperate" to "separate",
        "occured" to "occurred", "wich" to "which", "alot" to "a lot", "untill" to "until", "goign" to "going",
        "tommorow" to "tomorrow", "tomorow" to "tomorrow", "tommorrow" to "tomorrow", "wierd" to "weird",
        "acommodate" to "accommodate", "accomodate" to "accommodate", "adress" to "address", "agressive" to "aggressive",
        "apparantly" to "apparently", "beleive" to "believe", "calender" to "calendar", "commited" to "committed",
        "completly" to "completely", "concious" to "conscious", "enviroment" to "environment", "existance" to "existence",
        "foriegn" to "foreign", "freind" to "friend", "goverment" to "government", "grammer" to "grammar",
        "happend" to "happened", "immediatly" to "immediately", "independant" to "independent", "intresting" to "interesting",
        "knowlege" to "knowledge", "libary" to "library", "maintainance" to "maintenance", "neccessary" to "necessary",
        "necesary" to "necessary", "noticable" to "noticeable", "occassion" to "occasion", "persistant" to "persistent",
        "posible" to "possible", "prefered" to "preferred", "probly" to "probably", "publically" to "publicly",
        "realy" to "really", "refered" to "referred", "relevent" to "relevant", "restaraunt" to "restaurant",
        "sence" to "sense", "succesful" to "successful", "surprize" to "surprise",
        "tounge" to "tongue", "truely" to "truly", "writting" to "writing", "yestarday" to "yesterday",
        "thx" to "thanks", "pls" to "please", "plz" to "please", "whos" to "who's",
        "theres" to "there's", "heres" to "here's",
        "youve" to "you've", "weve" to "we've", "theyve" to "they've", "youll" to "you'll", "theyll" to "they'll",
        "itll" to "it'll", "thatll" to "that'll", "hows" to "how's", "wheres" to "where's",
        "waht" to "what", "wnat" to "want", "thsi" to "this", "jsut" to "just", "kno" to "know", "abotu" to "about",
        "peice" to "piece", "beggining" to "beginning", "begining" to "beginning", "buisness" to "business",
        "wouldve" to "would've", "couldve" to "could've", "shouldve" to "should've", "mustnt" to "mustn't"
    )

    /**
     * يصلح الضغط الخاطئ على Caps Lock: «hELLO» ← «Hello». نتحقق أن الصورة المصغّرة كلمة إنجليزية صالحة
     * حتى لا نمسّ أسماء مثل iPhone وmRNA وeBay.
     */
    private fun capsLockSlip(word: String): String? {
        if (word.length < 4 || !word[0].isLowerCase()) return null
        val rest = word.substring(1)
        if (!rest.all { it.isUpperCase() }) return null
        val lower = word.lowercase()
        if (ValidWords.english?.contains(lower) != true) return null
        return lower.replaceFirstChar { it.uppercase() }
    }

    private const val ARABIC_ASSET = "dict/ar_fix.txt"
    /** يُولَّد آليًا بـ tools/build_ar_fix.py عبر GitHub Actions؛ التصحيحات اليدوية أعلاه تتغلّب عليه عند التعارض */
    private const val ARABIC_AUTO_ASSET = "dict/ar_fix_auto.txt"
    private const val MIN_STEM = 4
    private val prefixes = listOf("وب", "ول", "وك", "و", "ب", "ل", "ك")

    @Volatile
    private var arabic: Map<String, String> = emptyMap()

    fun attach(context: Context) {
        if (arabic.isNotEmpty()) return
        val loaded = HashMap<String, String>(4096)
        // المولَّد أولًا ثم اليدوي فوقه، فتتغلّب التصحيحات اليدوية عند التعارض
        for (asset in listOf(ARABIC_AUTO_ASSET, ARABIC_ASSET)) {
            runCatching {
                context.applicationContext.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val tab = line.indexOf('\t')
                        if (tab <= 0) continue
                        val right = line.substring(tab + 1).trim()
                        if (right.isNotEmpty()) loaded[line.substring(0, tab)] = right
                    }
                }
            }
        }
        if (loaded.isNotEmpty()) {
            arabic = loaded
            ModelVersion.bump()
        }
    }

    fun fix(word: String): String? {
        if (word.isEmpty() || word.length > 20) return null
        if (ArabicText.isArabicWord(word)) return fixArabic(word)
        if (word.length > 14) return null
        capsLockSlip(word)?.let { return it }
        val fixed = english[word.lowercase()] ?: return null
        if (fixed == word) return null
        val result = when {
            fixed.startsWith("I") -> fixed
            word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() } -> fixed.uppercase()
            word.first().isUpperCase() -> fixed.replaceFirstChar { it.uppercase() }
            else -> fixed
        }
        return if (result == word) null else result
    }

    private val alPrefixes = listOf("وبال", "وكال", "وفال", "وال", "بال", "كال", "فال")

    private fun squeezeElongation(word: String): String? {
        if (word.length < 4) return null
        val sb = StringBuilder(word.length)
        var i = 0
        var changed = false
        while (i < word.length) {
            var j = i
            while (j < word.length && word[j] == word[i]) j++
            val run = j - i
            if (run >= 3 && word[i].isLetter()) { sb.append(word[i]); changed = true } else sb.append(word, i, j)
            i = j
        }
        if (!changed) return null
        val result = sb.toString()
        if (result.length < 2 || result.toSet().size < 2) return null
        if (result.all { it == 'ه' || it == 'ا' || it == 'ح' }) return null
        return result
    }

    private fun fixArabic(word: String): String? {
        val table = arabic
        squeezeElongation(word)?.let { squeezed ->
            val again = if (table.isNotEmpty()) table[squeezed] else null
            return again ?: squeezed
        }
        if (table.isEmpty()) return null
        table[word]?.let { return it.takeIf { r -> r != word } }
        for (p in alPrefixes) {
            if (!word.startsWith(p)) continue
            val stem = "ال" + word.substring(p.length)
            if (stem.length < 5) continue
            val hit = table[stem] ?: continue
            if (hit.startsWith("ال")) return p + hit.substring(2)
        }
        for (prefix in prefixes) {
            if (!word.startsWith(prefix)) continue
            val rest = word.substring(prefix.length)
            if (rest.length < MIN_STEM) continue
            val hit = table[rest] ?: continue
            return prefix + hit
        }
        if (word.length >= 5 && word.startsWith("لل")) {
            val hit = table["ال" + word.substring(2)]
            if (hit != null && hit.startsWith("ال")) return "لل" + hit.substring(2)
        }
        return null
    }
}
