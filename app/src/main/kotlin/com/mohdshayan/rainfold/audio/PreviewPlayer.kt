package com.mohdshayan.rainfold.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.mix.Mixer
import com.mohdshayan.rainfold.core.mix.MixSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sound picker's 8-second preview: one sound through its own mixer and track. It takes transient
 * audio focus, so a playing mix pauses for the preview and comes back after it.
 */
class PreviewPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val mixer = Mixer(seed = System.nanoTime())
    private val out = AudioOut(mixer) { onEnded() }
    private val _playing = MutableStateFlow<EngineKind?>(null)
    val playing: StateFlow<EngineKind?> = _playing.asStateFlow()
    private var focus: AudioFocusRequest? = null
    private var stopAt = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopper = Runnable { stop() }

    /** False during a phone call, when previews wait. */
    fun inCall(): Boolean = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION

    fun play(kind: EngineKind): Boolean {
        if (inCall()) return false
        handler.removeCallbacks(stopper)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }
            .build()
        if (focus == null && audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        if (focus == null) focus = request
        mixer.update(MixSpec("Preview", listOf(LayerSpec(kind, volume = 0.75f, key = kind.ordinal + keyBase++ * 100))), listOf(true), false)
        out.start(1f)
        _playing.value = kind
        stopAt = PREVIEW_MS
        handler.postDelayed(stopper, PREVIEW_MS)
        return true
    }

    fun stop() {
        handler.removeCallbacks(stopper)
        out.stop()
        _playing.value = null
    }

    private fun onEnded() {
        handler.post {
            if (!out.isRunning) {
                focus?.let { audio.abandonAudioFocusRequest(it) }
                focus = null
                _playing.value = null
            }
        }
    }

    private var keyBase = 0

    companion object {
        const val PREVIEW_MS = 8_000L
    }
}
