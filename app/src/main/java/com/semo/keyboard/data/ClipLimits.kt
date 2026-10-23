package com.semo.keyboard.data

import android.content.Context

object ClipLimits {
    const val DEFAULT_FILE_MB = 100
    const val DEFAULT_TOTAL_MB = 500
    val FILE_CHOICES_MB = listOf(25, 100, 250, 500)
    val TOTAL_CHOICES_MB = listOf(200, 500, 1000, 2000)

    private const val PREFS = "clip_limits"
    private const val KEY_FILE = "file_mb"
    private const val KEY_TOTAL = "total_mb"

    private fun read(context: Context, key: String, default: Int): Int = runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key, default)
    }.getOrDefault(default)

    private fun write(context: Context, key: String, value: Int) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(key, value).apply()
        }
    }

    fun fileMb(context: Context): Int = read(context, KEY_FILE, DEFAULT_FILE_MB)
    fun totalMb(context: Context): Int = read(context, KEY_TOTAL, DEFAULT_TOTAL_MB)
    fun setFileMb(context: Context, mb: Int) = write(context, KEY_FILE, mb)
    fun setTotalMb(context: Context, mb: Int) = write(context, KEY_TOTAL, mb)

    fun fileBytes(context: Context): Long = fileMb(context) * 1024L * 1024L
    fun totalBytes(context: Context): Long = totalMb(context) * 1024L * 1024L
}
