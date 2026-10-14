package com.semo.keyboard.ime

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

object VoiceBus {
    @Volatile
    var onPermission: ((Boolean) -> Unit)? = null
}

class VoicePermissionActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            deliver(true)
            return
        }
        if (savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        deliver(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
    }

    @Suppress("DEPRECATION")
    private fun deliver(granted: Boolean) {
        val cb = VoiceBus.onPermission
        VoiceBus.onPermission = null
        finish()
        overridePendingTransition(0, 0)
        cb?.invoke(granted)
    }

    private companion object {
        const val REQUEST = 4101
    }
}
