package com.example.engine

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Non-blocking arcade audio feedback.
 * All ToneGenerator Binder IPC calls execute on a dedicated single background thread
 * so sound playback never stalls the 60fps Compose UI thread.
 */
class ArcadeSoundPlayer {
    @Volatile
    private var generator: ToneGenerator? = null

    @Volatile
    var soundEnabled: Boolean = true

    private val audioExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wastecrush-audio").apply { isDaemon = true }
    }

    private fun playAsync(tone: Int, durationMs: Int) {
        if (!soundEnabled) return
        try {
            audioExecutor.execute {
                if (!soundEnabled) return@execute
                try {
                    var gen = generator
                    if (gen == null) {
                        gen = ToneGenerator(AudioManager.STREAM_MUSIC, 58)
                        generator = gen
                    }
                    gen.startTone(tone, durationMs)
                } catch (e: Exception) {
                    Log.w("ArcadeSoundPlayer", "Audio ToneGenerator unavailable: ${e.message}")
                }
            }
        } catch (_: Exception) {}
    }

    fun playSwap() = playAsync(ToneGenerator.TONE_DTMF_5, 22)

    fun playInvalidSwap() = playAsync(ToneGenerator.TONE_PROP_NACK, 65)

    fun playMatch(combo: Int, streak: Int = 1) {
        val intensity = combo + (streak / 2)
        val tone = when {
            intensity >= 5 -> ToneGenerator.TONE_PROP_BEEP2
            intensity >= 3 -> ToneGenerator.TONE_PROP_ACK
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        playAsync(tone, if (intensity >= 4) 70 else 45)
    }

    fun playMobImpact(isWeak: Boolean, isCritical: Boolean) {
        val tone = when {
            isCritical -> ToneGenerator.TONE_DTMF_D
            isWeak -> ToneGenerator.TONE_DTMF_9
            else -> ToneGenerator.TONE_DTMF_2
        }
        playAsync(tone, if (isCritical) 50 else 32)
    }

    fun playMobDefeated() = playAsync(ToneGenerator.TONE_CDMA_ANSWER, 130)

    fun playSkillReady() = playAsync(ToneGenerator.TONE_PROP_PROMPT, 90)

    fun playAbilityBlast() = playAsync(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)

    fun playRouletteTick() = playAsync(ToneGenerator.TONE_DTMF_1, 28)

    fun playVictory() = playAsync(ToneGenerator.TONE_CDMA_CONFIRM, 220)

    fun release() {
        try {
            audioExecutor.execute {
                try {
                    generator?.release()
                } catch (_: Exception) {}
                generator = null
            }
        } catch (_: Exception) {}
    }
}
