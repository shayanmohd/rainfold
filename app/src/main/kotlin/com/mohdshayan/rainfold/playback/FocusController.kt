package com.mohdshayan.rainfold.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Audio focus for the night: a call pauses the mix and it resumes when the call ends; a navigation
 * prompt ducks it; another app taking over for good pauses it.
 */
class FocusController(
    context: Context,
    private val onPause: (transient: Boolean) -> Unit,
    private val onResume: () -> Unit,
    private val onDuck: (ducked: Boolean) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var resumeOnGain = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    onDuck(false)
                    if (resumeOnGain) {
                        resumeOnGain = false
                        onResume()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeOnGain = true
                    onPause(true)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnGain = false
                    onPause(false)
                }
            }
        }
        .build()

    fun request(): Boolean {
        resumeOnGain = false
        return audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (!resumeOnGain) audio.abandonAudioFocusRequest(request)
    }

    fun abandonForGood() {
        resumeOnGain = false
        audio.abandonAudioFocusRequest(request)
    }
}
