package com.mohdshayan.rainfold.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.mohdshayan.rainfold.core.mix.Mixer

/**
 * One streaming float AudioTrack fed by a render thread that pulls 1024-frame blocks from [mixer].
 * [stop] fades the mixer to silence before the track stops, so a pause never clicks. The heartbeat
 * logs frames written and underruns once a minute under the tag RainfoldHeartbeat.
 */
class AudioOut(private val mixer: Mixer, private val onStopped: () -> Unit) {
    @Volatile private var running = false
    @Volatile private var stopping = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running && !stopping

    @Synchronized
    fun start(masterLevel: Float) {
        mixer.setMasterTarget(masterLevel)
        if (running) {
            stopping = false
            return
        }
        running = true
        stopping = false
        thread = Thread({ loop() }, "RainfoldRender").also { it.start() }
    }

    /** Fades out, then stops the track. Safe to call twice. */
    @Synchronized
    fun stop() {
        if (!running) return
        stopping = true
        mixer.setMasterTarget(0f)
    }

    fun setLevel(level: Float) {
        if (!stopping) mixer.setMasterTarget(level)
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = try {
            buildTrack()
        } catch (e: RuntimeException) {
            Log.w(TAG, "AudioTrack could not start", e)
            finish(null)
            return
        }
        val buf = FloatArray(Mixer.BLOCK * 2)
        var frames = 0L
        var lastBeat = SystemClock.elapsedRealtime()
        var stopDeadline = 0L
        try {
            track.play()
            while (true) {
                if (stopping) {
                    val now = SystemClock.elapsedRealtime()
                    if (stopDeadline == 0L) stopDeadline = now + 600
                    if (mixer.isSilent || now > stopDeadline) {
                        synchronized(this) {
                            if (stopping) {
                                running = false
                                stopping = false
                            }
                        }
                        if (!running) break
                    }
                } else {
                    stopDeadline = 0L
                }
                mixer.render(buf, Mixer.BLOCK)
                val written = track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.w(TAG, "AudioTrack write failed: $written")
                    break
                }
                frames += Mixer.BLOCK
                val now = SystemClock.elapsedRealtime()
                if (now - lastBeat >= HEARTBEAT_MS) {
                    lastBeat = now
                    Log.i(HEARTBEAT, "frames=$frames underruns=${track.underrunCount} layers=${mixer.activeLayers}")
                }
            }
        } finally {
            finish(track)
        }
    }

    private fun finish(track: AudioTrack?) {
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        // A quick play after a pause may already have started a new render thread; its state is not ours to clear.
        val mine = synchronized(this) {
            if (thread === Thread.currentThread()) {
                running = false
                stopping = false
                thread = null
                true
            } else {
                false
            }
        }
        if (mine) onStopped()
    }

    private fun buildTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(Mixer.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val min = AudioTrack.getMinBufferSize(Mixer.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            // About 170 ms of float stereo: generous for a busy phone at night, still quick enough for a drag.
            .setBufferSizeInBytes(maxOf(min, Mixer.BLOCK * 8 * 2 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            .build()
    }

    private companion object {
        const val TAG = "Rainfold"
        const val HEARTBEAT = "RainfoldHeartbeat"
        const val HEARTBEAT_MS = 60_000L
    }
}
