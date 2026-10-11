package com.mohdshayan.rainfold.core.mix

import com.mohdshayan.rainfold.core.dsp.Rng
import kotlin.math.exp

/**
 * Evolving night: a slow random walk added to a layer's intensity and tone. Every few minutes a new
 * target is picked inside +-[range], and the offset glides toward it over minutes, so the sound
 * changes without ever jumping. [step] is called once per audio block.
 */
class EvolvingDrift(seed: Long, private val blockSeconds: Double) {
    private val rng = Rng(seed)
    private var range = 0.3f
    private var toneTarget = 0f
    private var intensityTarget = 0f
    private var secondsToNext = 0.0
    private val glide = (1.0 - exp(-blockSeconds / GLIDE_SECONDS)).toFloat()

    var toneOffset = 0f
        private set
    var intensityOffset = 0f
        private set

    fun setRange(value: Float) {
        range = value.coerceIn(0f, 0.5f)
    }

    fun step() {
        secondsToNext -= blockSeconds
        if (secondsToNext <= 0) {
            toneTarget = rng.range(-range, range)
            intensityTarget = rng.range(-range, range)
            secondsToNext = rng.range(90f, 300f).toDouble()
        }
        toneOffset = (toneOffset + (toneTarget - toneOffset) * glide).coerceIn(-range, range)
        intensityOffset = (intensityOffset + (intensityTarget - intensityOffset) * glide).coerceIn(-range, range)
    }

    private companion object {
        const val GLIDE_SECONDS = 60.0
    }
}
