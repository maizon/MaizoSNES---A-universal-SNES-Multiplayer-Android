package com.maizon.maizosnes

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

/**
 * Simple streaming PCM output. The native libretro callback fills an SPSC ring buffer;
 * this worker drains it into AudioTrack without allocating per audio callback.
 */
internal class AudioOutput(private val requestedSampleRate: Int) {
    @Volatile
    private var running = false
    private var track: AudioTrack? = null
    private var worker: Thread? = null

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
            AudioTrack.Builder()
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
                .setBufferSizeInBytes(max(minBuffer * 2, sampleRate / 5 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        if (output.state != AudioTrack.STATE_INITIALIZED) {
            output.release()
            return
        }

        track = output
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
        worker?.interrupt()
        runCatching { worker?.join(500) }
        worker = null
        track?.let { output ->
            runCatching { output.pause() }
            runCatching { output.flush() }
            runCatching { output.stop() }
            output.release()
        }
        track = null
    }
}
