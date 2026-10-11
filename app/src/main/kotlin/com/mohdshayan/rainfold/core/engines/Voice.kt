package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Rng
import com.mohdshayan.rainfold.core.dsp.Smoother
import com.mohdshayan.rainfold.core.dsp.Tilt

/**
 * One layer's sound generator. [render] overwrites [left] and [right] with [frames] samples of
 * stereo audio at about -12 dBFS RMS; the mixer applies volume. Tone and intensity are targets the
 * voice glides toward, so a drag never clicks. Everything here is plain Kotlin with no Android
 * imports, and a voice built from the same seed renders the same samples.
 */
abstract class Voice(protected val sampleRate: Int, seed: Long) {
    protected val rng = Rng(seed)
    private val tiltL = Tilt(sampleRate)
    private val tiltR = Tilt(sampleRate)
    protected val intensity = Smoother(sampleRate, 0.4, 0.5f)

    fun setTone(tone: Float) {
        tiltL.setTone(tone)
        tiltR.setTone(tone)
    }

    fun setIntensity(value: Float) {
        intensity.target = value.coerceIn(0f, 1f)
    }

    /** Jumps straight to these settings, for a voice that is just starting. */
    fun snap(tone: Float, intensityValue: Float) {
        tiltL.snapTone(tone)
        tiltR.snapTone(tone)
        intensity.snap(intensityValue.coerceIn(0f, 1f))
    }

    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        renderRaw(left, right, frames)
        for (i in 0 until frames) {
            left[i] = tiltL.process(left[i])
            right[i] = tiltR.process(right[i])
        }
    }

    /** Writes the untilted signal. Implementations read [intensity] once per sample or per block. */
    protected abstract fun renderRaw(left: FloatArray, right: FloatArray, frames: Int)
}
