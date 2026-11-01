package com.semo.keyboard.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun typing() {
        val pkg = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.semo.keyboard"
        rule.collect(packageName = pkg, includeInStartupProfile = false) {
            val ime = "$pkg/com.semo.keyboard.ime.SemoKeyboardService"
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            device.executeShellCommand("ime enable $ime")
            device.executeShellCommand("ime set $ime")
            val intent = Intent().setClassName(pkg, "com.semo.keyboard.bench.BenchInputActivity")
            startActivityAndWait(intent)
            val w = device.displayWidth
            val h = device.displayHeight
            repeat(5) { round ->
                device.click(w / 2, (h * 0.12f).toInt())
                device.waitForIdle()
                for (row in 0 until 5) {
                    val y = (h * (0.60f + row * 0.075f)).toInt()
                    for (col in 0 until 10) {
                        val x = (w * (0.05f + col * 0.10f)).toInt()
                        device.click(x, y)
                    }
                    device.waitForIdle()
                }
                val y1 = (h * 0.68f).toInt()
                device.swipe((w * 0.10f).toInt(), y1, (w * 0.90f).toInt(), y1, 30)
                device.swipe((w * 0.20f).toInt(), (h * 0.75f).toInt(), (w * 0.80f).toInt(), (h * 0.68f).toInt(), 30)
                device.swipe((w * 0.30f).toInt(), (h * 0.68f).toInt(), (w * 0.30f).toInt(), (h * 0.68f).toInt(), 200)
                device.swipe((w * 0.50f).toInt(), (h * 0.90f).toInt(), (w * 0.50f).toInt(), (h * 0.90f).toInt(), 200)
                for (i in 0 until 3) device.click((w * (0.25f + i * 0.25f)).toInt(), (h * 0.55f).toInt())
                device.waitForIdle()
                if (round % 2 == 1) device.click((w * 0.08f).toInt(), (h * 0.95f).toInt())
                device.waitForIdle()
            }
        }
    }
}
