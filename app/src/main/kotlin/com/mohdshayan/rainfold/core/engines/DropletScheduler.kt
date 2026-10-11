package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Rng
import kotlin.math.ln

/**
 * Poisson event timing: the gap to the next event is exponential with mean 1 / rate, so events
 * never fall into a pattern. [rateFor] maps intensity 0..1 to events per second.
 */
class DropletScheduler(
    private val sampleRate: Int,
    private val rng: Rng,
    private val minRate: Double,
    private val maxRate: Double,
) {
    private var framesToNext = 0.0

    fun rateFor(intensity: Float): Double = minRate + (maxRate - minRate) * intensity.coerceIn(0f, 1f).toDouble()

    /**
     * Calls [emit] with the frame offset of every event that falls inside the next [frames] frames
     * at [intensity], and advances the clock.
     */
    inline fun advance(frames: Int, intensity: Float, emit: (offset: Int) -> Unit) {
        val rate = rateFor(intensity)
        var at = takeFirst()
        while (at < frames) {
            emit(at.toInt())
            at += gap(rate)
        }
        store(at - frames)
    }

    fun takeFirst(): Double = framesToNext

    fun store(remaining: Double) {
        framesToNext = remaining
    }

    fun gap(rate: Double): Double {
        if (rate <= 0.0) return Double.MAX_VALUE / 4
        val u = (1.0 - rng.nextFloat().toDouble()).coerceAtLeast(1e-9)
        return -ln(u) / rate * sampleRate
    }
}
