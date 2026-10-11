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
        "يا" to listOf("رب", "حبيبي", "سلام", "اخي", "عيني")
    ).mapKeys { BigDictionary.key(it.key) }

    fun predict(
        prev: String,
        language: KeyboardLanguage,
        learned: Map<String, List<String>>,
        limit: Int = 3
    ): List<String> {
        val k = BigDictionary.key(prev)
        if (k.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        learned[k]?.forEach { if (out.size < limit) out.add(it) }
        val seed = if (language == KeyboardLanguage.ARABIC) arabicSeed else englishSeed
        seed[k]?.forEach { if (out.size < limit) out.add(it) }
        return out.toList()
    }
}
