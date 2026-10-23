package com.semo.keyboard.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.semo.keyboard.R
import com.semo.keyboard.ui.keyboard.KeyFeedback

/**
 * يشغّل أصوات الضغط من ملفات صوتية حقيقية (res/raw) بدل الصوت المولَّد برمجيًا:
 * key_press_click (الحروف العادية)، key_press_delete (الحذف)، key_press_space (المسافة)، key_press_modifier
 * (Shift/مسافة/Return وبقية المفاتيح الوظيفية). نستخدم SoundPool لأن زمن استجابته منخفض
 * جدًا ومناسب لصوت لمسة فورية، وهو يدعم تحميل عدة أصوات قصيرة دفعة واحدة بذاكرة قليلة.
 */
class KeyClickPlayer(context: Context) {

    private val appContext = context.applicationContext

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = HashMap<KeyFeedback, Int>()
    @Volatile private var loaded = false

    init {
        runCatching {
            pool.setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded = true }
            val click = pool.load(appContext, R.raw.key_press_click, 1)
            soundIds[KeyFeedback.STANDARD] = click
            soundIds[KeyFeedback.MODIFIER] = click
            soundIds[KeyFeedback.DELETE] = pool.load(appContext, R.raw.key_press_delete, 1)
            soundIds[KeyFeedback.SPACE] = pool.load(appContext, R.raw.key_press_space, 1)
            soundIds[KeyFeedback.RETURN] = pool.load(appContext, R.raw.key_press_return, 1)
        }
    }

    /** [volume] من 0 إلى 1 (قيمة الشريط). نطبّق منحنى تربيعيًا لأن الأذن لا تدرك الصوت خطيًا. */
    fun play(kind: KeyFeedback, volume: Float = 1f) {
        val id = soundIds[kind] ?: soundIds[KeyFeedback.STANDARD] ?: return
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0.001f) return
        val level = v * v
        runCatching { pool.play(id, level, level, 1, 0, 1f) }
    }

    fun release() {
        runCatching { pool.release() }
        soundIds.clear()
    }
}
