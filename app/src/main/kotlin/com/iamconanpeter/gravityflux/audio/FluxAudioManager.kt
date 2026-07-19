/*
 * FluxAudioManager.kt
 *
 * Lightweight audio layer built on top of Android's SoundPool. All sounds are
 * synthesized in code as short PCM buffers (no asset files, no copyrighted
 * audio). Four events are supported: rotate (whoosh), collect (chime),
 * win (hum), hazard (thud).
 */

package com.iamconanpeter.gravityflux.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream

/**
 * Game audio events.
 */
enum class FluxEvent {
    ROTATE,   // gravity bend / whoosh
    COLLECT,  // node collected / chime
    WIN,      // chamber cleared / hum
    HAZARD    // hit a hazard / thud
}

class FluxAudioManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<FluxEvent, Int>()

    @Volatile
    private var muted: Boolean = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        // Build, wrap as WAV, and load each synthesized clip.
        soundIds[FluxEvent.ROTATE] = soundPool.load(writeWav(generateWhoosh()), 1)
        soundIds[FluxEvent.COLLECT] = soundPool.load(writeWav(generateChime()), 1)
        soundIds[FluxEvent.WIN] = soundPool.load(writeWav(generateHum()), 1)
        soundIds[FluxEvent.HAZARD] = soundPool.load(writeWav(generateThud()), 1)
    }

    /**
     * Play a synthesized event. No-op when muted or sound not yet loaded.
     */
    fun play(event: FluxEvent) {
        if (muted) return
        val id = soundIds[event] ?: return
        soundPool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    fun isMuted(): Boolean = muted

    /**
     * Release all audio resources. Call from onDestroy.
     */
    fun release() {
        soundPool.release()
        soundIds.clear()
    }

    /**
     * Wrap raw 16-bit mono PCM into a WAV file inside the cache dir and return its
     * path so SoundPool can load it. No external asset files are used.
     */
    private fun writeWav(pcm: ByteArray): String {
        val file = File(appContext.cacheDir, "flux_${System.nanoTime()}.wav")
        FileOutputStream(file).use { out ->
            val dataSize = pcm.size
            val header = ByteArray(44)
            fun putIntLE(offset: Int, value: Int, bytes: Int) {
                var v = value
                for (i in 0 until bytes) {
                    header[offset + i] = (v and 0xFF).toByte()
                    v = v ushr 8
                }
            }
            "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
            putIntLE(4, 36 + dataSize, 4)
            "WAVE".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
            "fmt ".toByteArray(Charsets.US_ASCII).copyInto(header, 12)
            putIntLE(16, 16, 4)          // subchunk1 size
            putIntLE(20, 1, 2)           // audio format = PCM
            putIntLE(22, 1, 2)           // mono
            putIntLE(24, SAMPLE_RATE, 4) // sample rate
            putIntLE(28, SAMPLE_RATE * 2, 4) // byte rate
            putIntLE(32, 2, 2)           // block align
            putIntLE(34, 16, 2)          // bits per sample
            "data".toByteArray(Charsets.US_ASCII).copyInto(header, 36)
            putIntLE(40, dataSize, 4)
            out.write(header)
            out.write(pcm)
        }
        return file.absolutePath
    }

    // ---- PCM synthesis helpers -----------------------------------------
    //
    // Samples are 16-bit signed PCM mono at SAMPLE_RATE Hz. Each generator
    // returns a raw ByteArray ready for SoundPool.load().

    private companion object {
        const val SAMPLE_RATE = 44100

        /** Convert a float sample (-1..1) to a 16-bit little-endian byte pair. */
        fun floatToPcm(out: ByteArray, offset: Int, sample: Float) {
            val clamped = sample.coerceIn(-1f, 1f)
            val s = (clamped * 32767f).toInt().toShort()
            out[offset] = (s.toInt() and 0xFF).toByte()
            out[offset + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
        }

        /** A short frequency sweep that reads as a "whoosh". */
        fun generateWhoosh(): ByteArray {
            val dur = 0.22
            val n = (SAMPLE_RATE * dur).toInt()
            val out = ByteArray(n * 2)
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / n
                // Sweep 220Hz -> 660Hz.
                val freq = 220.0 + 440.0 * progress
                val env = (1.0 - progress) * 0.6
                val s = (Math.sin(2.0 * Math.PI * freq * t) * env).toFloat()
                floatToPcm(out, i * 2, s)
            }
            return out
        }

        /** A bright two-tone chime (collect). */
        fun generateChime(): ByteArray {
            val dur = 0.18
            val n = (SAMPLE_RATE * dur).toInt()
            val out = ByteArray(n * 2)
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / n
                val env = (1.0 - progress) * 0.7
                val f1 = 880.0
                val f2 = 1320.0
                val mix = Math.sin(2.0 * Math.PI * f1 * t) * 0.6 +
                        Math.sin(2.0 * Math.PI * f2 * t) * 0.4
                floatToPcm(out, i * 2, (mix * env).toFloat())
            }
            return out
        }

        /** A sustained low hum (win). */
        fun generateHum(): ByteArray {
            val dur = 0.6
            val n = (SAMPLE_RATE * dur).toInt()
            val out = ByteArray(n * 2)
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / n
                val env = Math.sin(Math.PI * progress) * 0.8
                val freq = 196.0 // ~G3
                val s = Math.sin(2.0 * Math.PI * freq * t) * env
                floatToPcm(out, i * 2, s.toFloat())
            }
            return out
        }

        /** A short low thud (hazard). */
        fun generateThud(): ByteArray {
            val dur = 0.25
            val n = (SAMPLE_RATE * dur).toInt()
            val out = ByteArray(n * 2)
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / n
                val env = (1.0 - progress) * 0.9
                val freq = 90.0 - 50.0 * progress
                val s = Math.sin(2.0 * Math.PI * freq * t) * env
                floatToPcm(out, i * 2, s.toFloat())
            }
            return out
        }
    }
}
