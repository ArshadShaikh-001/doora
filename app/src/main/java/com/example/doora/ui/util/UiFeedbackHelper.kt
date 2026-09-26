package com.example.doora.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.doora.R

object UiFeedbackHelper {

    private var soundPool: SoundPool? = null
    private var completeSoundId: Int = 0
    private var createdSoundId: Int = 0
    private var isLoaded: Boolean = false

    fun init(context: Context) {
        if (soundPool != null) return
        val appContext = context.applicationContext
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
            .apply {
                completeSoundId = load(appContext, R.raw.task_complete, 1)
                createdSoundId = load(appContext, R.raw.task_created, 1)
                setOnLoadCompleteListener { _, _, _ ->
                    isLoaded = true
                }
            }
    }

    private fun isAudioAllowed(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        return audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    fun playCompletionFeedback(context: Context) {
        init(context)
        // 1. Subtle, short, crisp haptic
        performCompletionHaptic(context)

        // 2. Short, soft UI confirmation sound
        if (isAudioAllowed(context) && soundPool != null && completeSoundId != 0) {
            soundPool?.play(completeSoundId, 0.35f, 0.35f, 1, 0, 1.0f)
        }
    }

    fun playTaskCreatedFeedback(context: Context) {
        init(context)
        // 1. Gentle tick haptic
        performCreatedHaptic(context)

        // 2. Soft upward whoosh UI sound
        if (isAudioAllowed(context) && soundPool != null && createdSoundId != 0) {
            soundPool?.play(createdSoundId, 0.30f, 0.30f, 1, 0, 1.0f)
        }
    }

    @Suppress("DEPRECATION")
    private fun performCompletionHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(25L, 120))
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(25L)
            }
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun performCreatedHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(18L, 80))
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(18L)
            }
        } catch (_: Exception) {}
    }
}
