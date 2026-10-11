package com.semo.keyboard.domain.logic

/**
 * تصحيح تلقائي محافظ (مثل iOS): الاختصارات بدون فاصلة علوية (dont → don't)،
 * أخطاء إملائية شائعة، وهمزات عربية شائعة. جدول ثابت بدل مطابقة تقريبية،
 * لأن القاموس المدمج صغير والمطابقة التقريبية كانت رح تغيّر كلمات سليمة.
 */
object AutoCorrect {

    private val table: Map<String, String> = mapOf(
        // اختصارات إنكليزية
        "i" to "I", "im" to "I'm", "ive" to "I've", "i'm" to "I'm", "i'll" to "I'll", "i've" to "I've", "i'd" to "I'd",
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't", "doesnt" to "doesn't",
        "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
        "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't",
        "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't",
        "youre" to "you're", "theyre" to "they're", "thats" to "that's", "whats" to "what's",
        "hes" to "he's", "shes" to "she's",
        // أخطاء إملائية شائعة
        "teh" to "the", "adn" to "and", "taht" to "that", "thier" to "their", "becuase" to "because",
        "recieve" to "receive", "definately" to "definitely", "seperate" to "separate",
        "occured" to "occurred", "wich" to "which", "alot" to "a lot", "untill" to "until", "goign" to "going",
        // همزات عربية شائعة
        "الى" to "إلى", "انا" to "أنا", "انت" to "أنت", "اكيد" to "أكيد", "اهلا" to "أهلا", "اين" to "أين",
        "اخي" to "أخي", "اختي" to "أختي", "ابي" to "أبي", "امي" to "أمي", "لاكن" to "لكن",
        "هاذا" to "هذا", "هاذه" to "هذه"
    )

    /** يرجع الكلمة المصحّحة، أو null لو ما تحتاج تصحيح */
    fun fix(word: String): String? {
        if (word.isEmpty() || word.length > 14) return null
        val fixed = table[word.lowercase()] ?: return null
        if (fixed == word) return null
        val result = when {
            // الضمير I والاختصارات اللي تبدأ به تُكتب كبيرة دائمًا
            fixed.startsWith("I") -> fixed
            word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() } -> fixed.uppercase()
            word.first().isUpperCase() -> fixed.replaceFirstChar { it.uppercase() }
            else -> fixed
        }
        return if (result == word) null else result
    }
}
