package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage

/**
 * توقّع الكلمة التالية: أولًا الأزواج التي تعلّمتها اللوحة من كتابتك (الأكثر تكرارًا)، ثم قائمة صغيرة
 * مدمجة بأشهر التراكيب. المفاتيح بصيغة [BigDictionary.key] (أحرف صغيرة وهمزات موحّدة). منطق بحت.
 */
object NextWordModel {

    private val englishSeed: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "will", "have", "think", "love", "don't"),
        "i'm" to listOf("not", "going", "sorry", "so", "here"),
        "you" to listOf("are", "can", "have", "know", "want"),
        "the" to listOf("best", "same", "first", "other", "most"),
        "thank" to listOf("you"),
        "thanks" to listOf("for", "a", "so"),
        "how" to listOf("are", "is", "do", "much", "about"),
        "what" to listOf("is", "are", "do", "time", "about"),
        "where" to listOf("are", "is", "do", "can"),
        "good" to listOf("morning", "night", "evening", "luck", "job"),
        "have" to listOf("a", "to", "been", "you"),
        "to" to listOf("the", "be", "do", "go", "see"),
        "in" to listOf("the", "a", "my", "front"),
        "on" to listOf("the", "my", "my way", "time"),
        "of" to listOf("the", "course", "my", "a"),
        "let" to listOf("me", "us", "know", "it"),
        "let's" to listOf("go", "meet", "talk", "see"),
        "can" to listOf("you", "i", "we", "not"),
        "will" to listOf("be", "you", "see", "call"),
        "please" to listOf("let", "send", "call", "tell"),
        "happy" to listOf("birthday", "new", "to", "with"),
        "see" to listOf("you", "the", "it", "soon"),
        "looking" to listOf("forward", "for", "at"),
        "talk" to listOf("to", "later", "soon", "about"),
        "call" to listOf("me", "you", "back", "later"),
        "do" to listOf("you", "not", "it", "we"),
        "it's" to listOf("a", "not", "ok", "fine", "me"),
        "is" to listOf("it", "the", "a", "not"),
        "are" to listOf("you", "we", "they", "there"),
        "and" to listOf("the", "i", "then", "you"),
        "for" to listOf("the", "you", "a", "me"),
        "with" to listOf("you", "the", "me", "a"),
        "my" to listOf("name", "friend", "phone", "love", "way"),
        "this" to listOf("is", "morning", "evening", "week", "time"),
        "no" to listOf("problem", "worries", "one", "way"),
        "ok" to listOf("thanks", "see you", "great", "fine"),
        "sorry" to listOf("for", "i", "about", "to"),
        "i'll" to listOf("be", "call", "send", "see", "let"),
        "don't" to listOf("know", "worry", "forget", "think", "have"),
        "wait" to listOf("for", "a", "until")
    ).mapKeys { BigDictionary.key(it.key) }

    private val arabicSeed: Map<String, List<String>> = mapOf(
        "انا" to listOf("بخير", "في", "هون", "رايح", "كمان", "مش"),
        "كيف" to listOf("حالك", "الحال", "صحتك", "بدك", "كان"),
        "شو" to listOf("بدك", "اخبارك", "رايك", "صار", "في", "عم"),
        "صباح" to listOf("الخير", "النور"),
        "مساء" to listOf("الخير", "النور"),
        "السلام" to listOf("عليكم"),
        "عليكم" to listOf("السلام", "ورحمة"),
        "ورحمة" to listOf("الله", "الله وبركاته"),
        "ان" to listOf("شاء", "كان", "الله"),
        "شاء" to listOf("الله"),
        "ما" to listOf("شاء", "في", "بعرف", "عندي", "بدي"),
        "جزاك" to listOf("الله", "الله خير"),
        "الله" to listOf("يعطيك", "يسلمك", "يخليك", "يحفظك", "يرحمه"),
        "يعطيك" to listOf("العافية"),
        "الحمد" to listOf("لله"),
        "بارك" to listOf("الله"),
        "تسلم" to listOf("ايدك", "يا", "على"),
        "بدي" to listOf("اروح", "اكل", "اشوفك", "اسألك", "شي"),
        "بدك" to listOf("تجي", "اي", "شي", "تروح"),
        "ليش" to listOf("ما", "هيك", "لسا", "بتسأل"),
        "وين" to listOf("رايح", "انت", "كنت", "بدك"),
        "متى" to listOf("بتجي", "رح", "الموعد"),
        "لا" to listOf("تنسى", "اله", "مشكلة", "بأس", "اعرف"),
        "مش" to listOf("عارف", "مشكلة", "هيك", "معقول", "قادر"),
        "هذا" to listOf("هو", "الشي", "صحيح", "جيد"),
        "في" to listOf("البيت", "الشغل", "المدرسة", "الجامعة", "الصباح"),
        "على" to listOf("فكرة", "طول", "الاقل", "كل", "ما"),
        "من" to listOf("فضلك", "اجل", "زمان", "هون"),
        "كل" to listOf("عام", "يوم", "شي", "سنة", "الاحترام"),
        "عيد" to listOf("مبارك", "سعيد", "الاضحى", "الفطر"),
        "الف" to listOf("شكر", "مبروك", "سلامة", "عافية"),
        "يسلمو" to listOf("ايدك", "ايديك"),
        "تصبح" to listOf("على", "على خير"),
        "ساعة" to listOf("وبكون", "ونص", "واحدة"),
        "بعد" to listOf("اذنك", "قليل", "ساعة", "الظهر"),
        "اذا" to listOf("بدك", "كان", "سمحت", "ما"),
        "شكرا" to listOf("جزيلا", "لك", "كتير", "على"),
        "مرحبا" to listOf("كيفك", "كيف", "بك", "شو"),
        "اهلا" to listOf("وسهلا", "فيك", "بك", "كيفك"),
        "يا" to listOf("رب", "حبيبي", "سلام", "اخي", "عيني"),
        "هلا" to listOf("والله", "فيك", "بيك", "حبيبي"),
        "كيفك" to listOf("شو", "اخبارك", "يا", "انشالله"),
        "شو" to listOf("بدك", "اخبارك", "رايك", "صار", "في", "عم", "هالحكي"),
        "عم" to listOf("بحكي", "بشتغل", "بدرس", "استنى", "اكتب"),
        "رح" to listOf("اجي", "اروح", "احكيك", "شوفك", "بكون"),
        "لسا" to listOf("ما", "بدري", "عم", "هون"),
        "هيك" to listOf("بس", "منيح", "يعني", "احسن"),
        "منيح" to listOf("كتير", "شكرا", "الحمد", "يلا"),
        "كتير" to listOf("حلو", "منيح", "تعبان", "مشتاق", "شكرا"),
        "يلا" to listOf("بقى", "نروح", "شو", "تعال", "باي"),
        "ولا" to listOf("يهمك", "شي", "مرة", "واحد"),
        "تعال" to listOf("هون", "نحكي", "شوف", "معي"),
        "خلص" to listOf("هيك", "تمام", "يلا", "بس"),
        "ان" to listOf("شاء", "كان", "الله", "شاءالله"),
        "ماشي" to listOf("الحال", "يلا", "تمام", "منحكي"),
        "بعدين" to listOf("منحكي", "بحكيك", "بشوفك", "بخبرك"),
        "الله" to listOf("يعطيك", "يسلمك", "يخليك", "يحفظك", "يرحمه", "يعافيك")
    ).mapKeys { BigDictionary.key(it.key) }

    private fun seedFor(language: KeyboardLanguage): Map<String, List<String>> = when (language) {
        KeyboardLanguage.ARABIC -> arabicSeed
        KeyboardLanguage.ENGLISH -> englishSeed
        else -> emptyMap()
    }

    /**
     * أفضل الكلمات التالية بعد [prev] (و[prev2] قبلها). الترتيب: ما تعلّمته اللوحة من كتابتك (ثلاثي ثم ثنائي)،
     * ثم نموذج الجمل (مدمج أو منزَّل من مكتبة اللغات)، ثم القائمة الصغيرة المدمجة.
     */
    private val predictCache = LruCache<List<String>>(192)

    fun clearCaches() {
        predictCache.clear()
    }

    fun predict(
        prev: String,
        language: KeyboardLanguage,
        learned: Map<String, List<String>>,
        limit: Int = 3,
        prev2: String = ""
    ): List<String> {
        val key = "${language.name}|$prev|$prev2|$limit|${ModelVersion.current()}|${System.identityHashCode(learned)}|${learned.size}"
        predictCache.get(key)?.let { return it }
        val result = predictUncached(prev, language, learned, limit, prev2)
        predictCache.put(key, result)
        return result
    }

    private fun predictUncached(
        prev: String,
        language: KeyboardLanguage,
        learned: Map<String, List<String>>,
        limit: Int,
        prev2: String
    ): List<String> {
        val k = BigDictionary.key(prev)
        if (k.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        if (prev2.isNotEmpty()) {
            val k2 = BigDictionary.key(prev2)
            if (k2.isNotEmpty()) learned["$k2 $k"]?.forEach { if (out.size < limit) out.add(it) }
        }
        learned[k]?.forEach { if (out.size < limit) out.add(it) }
        if (out.size < limit) {
            for (w in NgramModel.next(language, prev2, prev, limit)) {
                if (out.size >= limit) break
                if (out.none { it.equals(w, ignoreCase = true) }) out.add(w)
            }
        }
        if (out.size < limit) {
            seedFor(language)[k]?.forEach {
                if (out.size < limit && out.none { o -> o.equals(it, ignoreCase = true) }) out.add(it)
            }
        }
        return out.toList()
    }

    /**
     * تتمة الجملة: تبدأ من [seed] (الكلمة التي اختارتها أو كتبتها) وتكمل كلمة بكلمة بأرجح كلمة تالية حتى [maxWords]،
     * وتتوقف عند نهاية الجملة أو لما تضعف الثقة أو تتكرر الكلمات. تقرأ من كلماتك المتعلَّمة أولًا ثم من نموذج الجمل
     * ثم من القائمة المدمجة. ترجع الكلمات المولَّدة (تبدأ بـ [seed] لو أُعطي)، أو أقل من كلمتين لو ما في تتمة واثقة.
     */
    fun phrase(
        language: KeyboardLanguage,
        learned: Map<String, List<String>>,
        context: List<String>,
        seed: String?,
        maxWords: Int = 4
    ): List<String> {
        val out = ArrayList<String>()
        val ctx = ArrayList<String>(context)
        if (!seed.isNullOrEmpty()) {
            out.add(seed)
            ctx.add(seed)
        }
        var guard = 0
        while (out.size < maxWords && guard < maxWords) {
            guard++
            val prev = ctx.lastOrNull() ?: break
            val prev2 = if (ctx.size >= 2) ctx[ctx.size - 2] else ""
            val k = BigDictionary.key(prev)
            if (k.isEmpty()) break
            val tri = if (prev2.isNotEmpty()) learned[BigDictionary.key(prev2) + " " + k]?.firstOrNull() else null
            val bi = learned[k]?.firstOrNull()
            var next: String? = tri ?: bi
            if (next == null) {
                val best = NgramModel.best(language, prev2, prev)
                if (best != null) {
                    if (best.eos) break
                    // أول كلمة نقبلها بثقة أقل، وما بعدها نطلب ثقة أعلى كي لا تتولّد جمل عشوائية
                    val need = if (out.isEmpty()) 0.12f else 0.3f
                    if (best.confidence >= need) next = best.word
                }
            }
            if (next == null) next = seedFor(language)[k]?.firstOrNull()
            val n = next ?: break
            if (n.isEmpty() || out.any { it.equals(n, ignoreCase = true) } || n.equals(prev, ignoreCase = true)) break
            out.add(n)
            ctx.addAll(n.split(' ').filter { it.isNotEmpty() })
        }
        return out
    }
}
