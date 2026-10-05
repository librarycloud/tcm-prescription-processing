package com.tcm.admin

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object InteractionFeedback {
    private const val PREFS = "TcmPrefs"
    private const val SOUND_KEY = "soundEffectsEnabled"
    private const val HAPTIC_KEY = "actionHapticEnabled"

    fun isSoundEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(SOUND_KEY, true)

    fun isHapticEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(HAPTIC_KEY, true)

    fun success(context: Context) {
        playTone(context, ToneGenerator.TONE_PROP_ACK)
        haptic(context)
    }

    fun error(context: Context) {
        playTone(context, ToneGenerator.TONE_PROP_NACK)
        haptic(context)
    }

    fun haptic(context: Context) {
        if (!isHapticEnabled(context)) return
        runCatching {
            val vibration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibration?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibration?.vibrate(50)
            }
        }
    }

    private fun playTone(context: Context, tone: Int) {
        if (!isSoundEnabled(context)) return
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(tone, 140)
            Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, 220)
        }
    }
}
