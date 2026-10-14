package com.semo.keyboard.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Glue of the Semo ecosystem (Gallery, Music, Files, Notes, Keyboard): the apps stay separate but find each
 * other and hand work over. Nothing is hard-coded to a package name: every Semo app declares an activity filter
 * for [ACTION_ECO] plus `<meta-data app.semo.ROLE>` (gallery | music | files | notes | keyboard) in its manifest.
 */
object Semo {
    const val ACTION_ECO = "app.semo.ECOSYSTEM"
    const val META_ROLE = "app.semo.ROLE"
    const val ROLE_NOTES = "notes"
    const val ROLE_FILES = "files"

    const val ACTION_SHOW_IN_FILES = "app.semo.SHOW_IN_FILES"

    data class Sibling(
        val pkg: String,
        val role: String,
        val label: String,
        val version: String,
        val code: Long
    )

    @Volatile private var cache: List<Sibling>? = null
    @Volatile private var cacheAt = 0L

    /** Other Semo apps installed on this phone (this app excluded). Cached for a few seconds. */
    @Suppress("DEPRECATION")
    fun siblings(c: Context): List<Sibling> {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheAt < 5000) return it }
        val out = ArrayList<Sibling>()
        runCatching {
            val pm = c.packageManager
            val probe = Intent(ACTION_ECO).addCategory(Intent.CATEGORY_DEFAULT)
            val seen = HashSet<String>()
            for (ri in pm.queryIntentActivities(probe, 0)) {
                val p = ri.activityInfo?.packageName ?: continue
                if (p == c.packageName || !seen.add(p)) continue
                runCatching {
                    val ai = pm.getApplicationInfo(p, PackageManager.GET_META_DATA)
                    val pi = pm.getPackageInfo(p, 0)
                    out.add(
                        Sibling(
                            pkg = p,
                            role = ai.metaData?.getString(META_ROLE).orEmpty(),
                            label = pm.getApplicationLabel(ai).toString(),
                            version = pi.versionName.orEmpty(),
                            code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
                        )
                    )
                }
            }
        }
        cache = out
        cacheAt = now
        return out
    }

    fun byRole(c: Context, role: String): Sibling? = siblings(c).firstOrNull { it.role == role }

    fun open(c: Context, pkg: String): Boolean = runCatching {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
        true
    }.getOrDefault(false)

    fun hasNotes(c: Context): Boolean = byRole(c, ROLE_NOTES) != null

    /** Sends plain text to the Notes app (its public "share" doorway opens a new note with it). */
    fun sendToNotes(c: Context, text: String, title: String = ""): Boolean {
        val s = byRole(c, ROLE_NOTES) ?: return false
        if (text.isEmpty()) return false
        return runCatching {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(s.pkg)
            i.putExtra(Intent.EXTRA_TEXT, text)
            if (title.isNotEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, title)
            if (c !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            true
        }.getOrDefault(false)
    }
}
