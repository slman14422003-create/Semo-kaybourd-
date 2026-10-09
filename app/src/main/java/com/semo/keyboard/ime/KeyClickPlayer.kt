package com.semo.keyboard.ime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.semo.keyboard.ui.keyboard.KeyFeedback
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * يولّد أصوات الضغط برمجيًا بدل صوت النظام (FX_KEYPRESS_STANDARD) الذي لا يشبه آيفون.
 *
 * آيفون عنده ثلاثة أصوات "تَك" قصيرة وجافة: للحروف، وللحذف (أغلظ)، وللمفاتيح الوظيفية
 * (مسافة/Shift/Return — أنعم قليلًا). نركّبها هنا من جسم رنّان يتلاشى بسرعة + نقرة ضوضاء
 * قصيرة جدًا في البداية، ثم نحمّلها بـ AudioTrack ثابت (MODE_STATIC) بزمن استجابة منخفض.
 * لا ملفات صوتية ولا أذونات، والمستوى يتبع صوت النظام للأصوات التفاعلية.
 */
class KeyClickPlayer {

    private class Clip(val track: AudioTrack)

    private val clips = HashMap<KeyFeedback, Clip>()

    init {
        runCatching {
            clips[KeyFeedback.STANDARD] = build(freq = 1850.0, decayMs = 5.5, lengthMs = 38, noise = 0.55, gain = 0.80)
            clips[KeyFeedback.DELETE] = build(freq = 1150.0, decayMs = 7.0, lengthMs = 44, noise = 0.45, gain = 0.85)
            clips[KeyFeedback.MODIFIER] = build(freq = 1450.0, decayMs = 6.0, lengthMs = 40, noise = 0.50, gain = 0.80)
        }
    }

    fun play(kind: KeyFeedback) {
        val clip = clips[kind] ?: clips[KeyFeedback.STANDARD] ?: return
        runCatching {
            val t = clip.track
            t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        clips.values.forEach { runCatching { it.track.release() } }
        clips.clear()
    }

    private fun build(freq: Double, decayMs: Double, lengthMs: Int, noise: Double, gain: Double): Clip {
        val rate = SAMPLE_RATE
        val n = rate * lengthMs / 1000
        val pcm = ShortArray(n)
        val tau = decayMs / 1000.0
        var seed = 0x2F6E2B1L // ضوضاء ثابتة: نفس الصوت بكل ضغطة
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            // انزلاق نغمي خفيف نحو الأسفل (يعطي إحساس "تَك" خشبي بدل نغمة صافية)
            val glide = 1.0 - 0.18 * (1.0 - exp(-t / 0.006))
            val body = sin(2.0 * PI * freq * glide * t) * exp(-t / tau)
            val overtone = 0.30 * sin(2.0 * PI * freq * 2.35 * t) * exp(-t / (tau * 0.55))
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            val white = (seed.toDouble() / 0x7fffffffL) * 2.0 - 1.0
            lp += 0.35 * (white - lp) // مرشّح تمرير منخفض بسيط للنقرة
            val click = lp * exp(-t / 0.0012) * noise
            var v = (body + overtone + click) * gain
            // تلاشي بآخر 3ms لمنع الفرقعة
            val tail = n - i
            val fade = rate * 3 / 1000
            if (tail < fade) v *= tail.toDouble() / fade
            pcm[i] = (max(-1.0, minOf(1.0, v)) * Short.MAX_VALUE * 0.9).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track.write(pcm, 0, pcm.size)
        return Clip(track)
    }

    private companion object {
        const val SAMPLE_RATE = 44100
    }
}
