package com.semo.keyboard.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.semo.keyboard.R

enum class VoiceCue { START, END, CANCEL }

class VoiceCuePlayer(context: Context) {

    private val app = context.applicationContext

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<VoiceCue, Int>()
    private val loaded = HashSet<Int>()

    init {
        runCatching {
            pool.setOnLoadCompleteListener { _, id, status ->
                if (status == 0) synchronized(loaded) { loaded.add(id) }
            }
            ids[VoiceCue.START] = pool.load(app, R.raw.voice_start, 1)
            ids[VoiceCue.END] = pool.load(app, R.raw.voice_end, 1)
            ids[VoiceCue.CANCEL] = pool.load(app, R.raw.voice_cancel, 1)
        }
    }

    fun play(cue: VoiceCue, volume: Float): Boolean {
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0.001f) return false
        val id = ids[cue] ?: return false
        if (synchronized(loaded) { id !in loaded }) return false
        val level = (0.25f + 0.75f * v).coerceIn(0f, 1f)
        return runCatching { pool.play(id, level, level, 1, 0, 1f) }.getOrDefault(0) != 0
    }

    fun release() {
        runCatching { pool.release() }
        ids.clear()
        synchronized(loaded) { loaded.clear() }
    }
}

class SystemBeepMuter(context: Context) {

    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var muted = false
    private val releaseTask = Runnable { restore() }

    fun mute(durationMs: Long) {
        val am = audio ?: return
        if (!muted) {
            val already = runCatching { am.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(true)
            if (already) return
            muted = runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) }.isSuccess
        }
        handler.removeCallbacks(releaseTask)
        if (muted) handler.postDelayed(releaseTask, durationMs)
    }

    fun restore() {
        handler.removeCallbacks(releaseTask)
        if (!muted) return
        muted = false
        runCatching { audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
    }
}
