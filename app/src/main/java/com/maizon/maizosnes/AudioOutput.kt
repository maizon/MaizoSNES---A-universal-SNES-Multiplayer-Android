package com.maizon.maizosnes

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import kotlin.math.max

/**
 * Simple streaming PCM output. The native libretro callback fills an SPSC ring buffer;
 * this worker drains it into AudioTrack without allocating per audio callback.
 */
internal class AudioOutput(private val requestedSampleRate: Int, private val targetBufferMs: Int = 40) {
    @Volatile
    private var running = false
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    @Volatile private var enabled = true
    @Volatile private var volume = 1f

    fun setEnabled(value: Boolean) {
        enabled = value
        applyVolume()
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        applyVolume()
    }

    private fun applyVolume() {
        track?.let { output -> runCatching { output.setVolume(if (enabled) volume else 0f) } }
    }

    fun start() {
        if (running) return
        val sampleRate = requestedSampleRate.coerceIn(8_000, 192_000)
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return

        val output = runCatching {
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                // Player-selectable 25 / 40 / 60 ms target. Android device
                // minimum takes precedence to avoid persistent underruns.
                .setBufferSizeInBytes(max(minBuffer * 2,
                    sampleRate * targetBufferMs.coerceIn(25, 60) / 1000 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            builder.build()
        }.getOrNull() ?: return

        if (output.state != AudioTrack.STATE_INITIALIZED) {
            output.release()
            return
        }

        track = output
        applyVolume()
        running = true
        runCatching { output.play() }.onFailure {
            running = false
            output.release()
            track = null
            return
        }

        worker = Thread({
            val buffer = ShortArray(2048)
            while (running) {
                val count = NativeBridge.nativeReadAudio(buffer)
                if (count > 0) {
                    var offset = 0
                    while (running && offset < count) {
                        val written = output.write(buffer, offset, count - offset, AudioTrack.WRITE_BLOCKING)
                        if (written <= 0) break
                        offset += written
                    }
                } else {
                    try {
                        Thread.sleep(2)
                    } catch (_: InterruptedException) {
                        if (!running) break
                    }
                }
            }
        }, "MaizoSNES-Audio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        val output = track
        // Stopping the AudioTrack first helps unblock a worker inside WRITE_BLOCKING.
        if (output != null) runCatching { output.stop() }
        worker?.interrupt()
        runCatching { worker?.join(1_000) }
        worker = null
        if (output != null) {
            runCatching { output.flush() }
            output.release()
        }
        track = null
    }
}
