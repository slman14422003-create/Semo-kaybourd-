package com.semo.keyboard.domain.logic

import android.content.Context

object AutoCorrect {

    private val englishBase: Map<String, String> = mapOf(
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

    private val englishMore: Map<String, String> = mapOf(
        "definatly" to "definitely", "definatley" to "definitely", "definitly" to "definitely", "occurence" to "occurrence",
        "occurrance" to "occurrence", "aswell" to "as well", "infact" to "in fact", "incase" to "in case",
        "eachother" to "each other", "alittle" to "a little", "thankyou" to "thank you", "everytime" to "every time",
        "atleast" to "at least", "heigth" to "height", "wieght" to "weight", "lenght" to "length",
        "stregth" to "strength", "strenght" to "strength", "diffrent" to "different", "diferent" to "different",
        "diffrence" to "difference", "beautifull" to "beautiful", "beatiful" to "beautiful", "beutiful" to "beautiful",
        "excelent" to "excellent", "exellent" to "excellent", "experiance" to "experience", "familar" to "familiar",
        "finaly" to "finally", "gaurd" to "guard", "gratefull" to "grateful", "harrass" to "harass",
        "humerous" to "humorous", "ignorence" to "ignorance", "imediately" to "immediately", "importent" to "important",
        "inteligent" to "intelligent", "liason" to "liaison", "lisence" to "license", "mispell" to "misspell",
        "millenium" to "millennium", "mischievious" to "mischievous", "neice" to "niece", "ocasion" to "occasion",
        "ocurred" to "occurred", "oppurtunity" to "opportunity", "oppertunity" to "opportunity", "parliment" to "parliament",
        "particulary" to "particularly", "percieve" to "perceive", "plesant" to "pleasant", "presense" to "presence",
        "priviledge" to "privilege", "pronounciation" to "pronunciation", "questionaire" to "questionnaire", "reccomend" to "recommend",
        "recomend" to "recommend", "refering" to "referring", "religous" to "religious", "remeber" to "remember",
        "repitition" to "repetition", "resistence" to "resistance", "responsability" to "responsibility", "rythm" to "rhythm",
        "scedule" to "schedule", "secratary" to "secretary", "sieze" to "seize", "similiar" to "similar",
        "sincerly" to "sincerely", "speach" to "speech", "succes" to "success", "sucess" to "success",
        "supercede" to "supersede", "suprise" to "surprise", "temperture" to "temperature", "thourough" to "thorough",
        "transfered" to "transferred", "twelth" to "twelfth", "unforseen" to "unforeseen", "unfortunatly" to "unfortunately",
        "usally" to "usually", "usualy" to "usually", "vaccum" to "vacuum", "vehical" to "vehicle",
        "visable" to "visible", "wellcome" to "welcome", "welcom" to "welcome", "wendsday" to "Wednesday",
        "wensday" to "Wednesday", "withold" to "withhold", "writen" to "written", "youself" to "yourself",
        "yuo" to "you", "yoru" to "your", "hte" to "the", "nad" to "and",
        "ahve" to "have", "hvae" to "have", "thta" to "that", "tihs" to "this",
        "wiht" to "with", "whcih" to "which", "woudl" to "would", "shoudl" to "should",
        "coudl" to "could", "becuz" to "because", "beacuse" to "because", "becasue" to "because",
        "becaus" to "because", "becouse" to "because", "cna" to "can", "tehy" to "they",
        "thye" to "they", "recive" to "receive", "mroe" to "more", "olny" to "only",
        "veyr" to "very", "verry" to "very", "thanx" to "thanks", "tonite" to "tonight",
        "sory" to "sorry", "sorrry" to "sorry", "congradulations" to "congratulations", "pleaes" to "please",
        "plase" to "please", "plesae" to "please", "thnak" to "thank", "thansk" to "thanks",
        "helo" to "hello", "togeather" to "together", "togather" to "together", "togther" to "together",
        "youd" to "you'd", "theyd" to "they'd", "oclock" to "o'clock", "recieved" to "received",
        "recieving" to "receiving", "recieves" to "receives", "freinds" to "friends", "seperated" to "separated",
        "seperately" to "separately", "definate" to "definite"
    )

    private val english: Map<String, String> = englishBase + englishMore

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
        val lower = word.lowercase()
        val fixed = english[lower] ?: inflected(lower) ?: return null
        if (fixed == word) return null
        val result = when {
            fixed.startsWith("I") -> fixed
            word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() } -> fixed.uppercase()
            word.first().isUpperCase() -> fixed.replaceFirstChar { it.uppercase() }
            else -> fixed
        }
        return if (result == word) null else result
    }

    private val inflections = listOf("ing", "ed", "es", "s", "d")

    private fun inflected(lower: String): String? {
        if (lower.length < 6) return null
        for (suffix in inflections) {
            if (!lower.endsWith(suffix)) continue
            val stem = lower.dropLast(suffix.length)
            if (stem.length < 4) continue
            val direct = english[stem]
            if (direct != null && '\'' !in direct && ' ' !in direct) {
                return validated(direct + suffix)
            }
            val withE = english[stem + "e"]
            if (withE != null && '\'' !in withE && ' ' !in withE) {
                val joined = when (suffix) {
                    "ing" -> withE.dropLast(1) + "ing"
                    "ed" -> withE + "d"
                    else -> null
                }
                if (joined != null) return validated(joined)
            }
        }
        return null
    }

    private fun validated(candidate: String): String? {
        val valid = ValidWords.english ?: return candidate
        return if (valid.contains(candidate)) candidate else null
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
