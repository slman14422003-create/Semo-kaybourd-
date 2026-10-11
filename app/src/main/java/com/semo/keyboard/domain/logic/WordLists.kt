package com.semo.keyboard.domain.logic

/** قوائم كلمات صغيرة مدمجة للإكمال التلقائي، مرتبة تقريبًا حسب الشيوع. لا اتصال بالإنترنت. */
object WordLists {

    val englishStarters = listOf("I", "The", "I'm")
    val arabicStarters = listOf("أنا", "كيف", "مرحبا")

    val english: List<String> = listOf(
        "the", "to", "and", "that", "have", "for", "not", "with", "you", "this", "but", "from", "they",
        "will", "would", "there", "their", "what", "about", "which", "when", "make", "like", "time",
        "just", "know", "take", "people", "into", "year", "your", "good", "some", "could", "them",
        "other", "than", "then", "now", "look", "only", "come", "over", "think", "also", "back",
        "after", "work", "first", "well", "even", "want", "because", "these", "give", "most",
        "hello", "thanks", "thank", "please", "sorry", "okay", "yes", "love", "where", "here",
        "today", "tomorrow", "tonight", "morning", "night", "friend", "home", "going", "doing",
        "really", "very", "still", "always", "never", "maybe", "right", "great", "nice", "happy",
        "message", "call", "phone", "minutes", "meeting", "later", "soon", "already", "anything",
        "something", "everything", "nothing", "everyone", "someone", "should", "being", "before",
        "between", "through", "without", "again", "another", "around", "believe", "better",
        "coming", "different", "enough", "family", "feeling", "finish", "forget", "getting",
        "important", "interesting", "language", "little", "looking", "remember", "thought",
        "together", "understand", "waiting", "welcome", "wonderful", "yesterday", "tomorrow",
        "I'm", "I'll", "I've", "don't", "can't", "it's", "that's", "what's", "didn't", "doesn't",
        "isn't", "won't", "you're", "we're", "they're", "let's", "how", "why", "who", "can", "does"
    )

    val arabic: List<String> = listOf(
        "في", "من", "على", "إلى", "أن", "هذا", "هذه", "ذلك", "التي", "الذي", "كان", "لا", "ما", "هل",
        "كيف", "كيفك", "أين", "متى", "لماذا", "ماذا", "أنا", "أنت", "هو", "هي", "نحن", "هم", "مع",
        "عن", "كل", "بعد", "قبل", "بين", "عند", "لكن", "أو", "ثم", "إذا", "لقد", "قد", "كما",
        "أيضا", "جدا", "شكرا", "مرحبا", "أهلا", "وسهلا", "صباح", "مساء", "الخير", "النور",
        "الله", "يعطيك", "العافية", "الحمد", "لله", "إن", "شاء", "ماشي", "تمام", "طيب", "يلا",
        "هلا", "بخير", "شو", "ليش", "وين", "هلق", "هون", "هناك", "اليوم", "بكرا", "أمس", "الآن",
        "دائما", "أبدا", "ربما", "ممكن", "أكيد", "طبعا", "صح", "خطأ", "حبيبي", "صديقي", "أخي",
        "أختي", "بيت", "شغل", "مدرسة", "جامعة", "سيارة", "طعام", "ماء", "وقت", "يوم", "ليلة",
        "أسبوع", "شهر", "سنة", "عمل", "حياة", "عائلة", "أمي", "أبي", "مشكلة", "سؤال", "جواب",
        "رسالة", "اتصال", "رقم", "موعد", "اجتماع", "سلام", "عفوا", "آسف", "تفضل", "تسلم",
        "الحمد", "بالتوفيق", "مبروك", "إنشاء", "صباحا", "مساءً", "السلام", "عليكم", "ورحمة"
    )
}
