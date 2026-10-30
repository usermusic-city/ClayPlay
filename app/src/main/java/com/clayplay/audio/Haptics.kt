package com.clayplay.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Touch feedback.
 *
 * Clay should feel like it resists slightly and then gives, so the pattern is a
 * short soft tap followed by a lighter release tick rather than one hard buzz.
 * Older devices fall back to plain vibrate().
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (t: Throwable) {
        Log.w(TAG, "no vibrator: " + t.message)
        null
    }

    private var enabled = true

    fun setEnabled(v: Boolean) { enabled = v }
    fun isEnabled() = enabled

    fun hasVibrator(): Boolean = vibrator?.hasVibrator() == true

    fun tap(strength: Int = 22) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // soft press then a light tail: two segments, both low amplitude
                v.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 16, 26, 10),
                        intArrayOf(0, strength, 0, strength / 3),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(22L)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "vibrate failed: " + t.message)
        }
    }

    companion object {
        private const val TAG = "Haptics"
    }
}
