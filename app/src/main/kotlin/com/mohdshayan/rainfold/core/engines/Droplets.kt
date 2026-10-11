package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Rng
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A pool of bubble-resonance droplets (van den Doel's model): each is a short sine whose pitch rises
 * slightly while it decays, with a random size, level and stereo position. Rain, storm and stream
 * use it with different size ranges.
 */
class Droplets(
    private val sampleRate: Int,
    private val rng: Rng,
    private val minHz: Float,
    private val maxHz: Float,
    private val level: Float,
    capacity: Int = 96,
) {
    private val phase = DoubleArray(capacity)
    private val freq = DoubleArray(capacity)
    private val rise = DoubleArray(capacity)
    private val amp = FloatArray(capacity)
    private val decay = FloatArray(capacity)
    private val panL = FloatArray(capacity)
    private val panR = FloatArray(capacity)
    private val start = IntArray(capacity)
    private val active = BooleanArray(capacity)

    /** Starts one droplet [offset] frames into the next [render] call. Dropped when the pool is full. */
    fun trigger(offset: Int, loudness: Float = 1f) {
        val slot = active.indexOfFirst { !it }
        if (slot < 0) return
        // Smaller bubbles ring higher and die faster.
        val f0 = minHz * (maxHz / minHz).toDouble().pow(rng.nextFloat().toDouble())
        val d = 0.043 * f0 + 0.0014 * f0.pow(1.5)
        phase[slot] = 0.0
        freq[slot] = f0
        rise[slot] = 1.0 + 0.1 * d / sampleRate
        decay[slot] = exp(-d / sampleRate).toFloat()
        val a = rng.nextFloat()
        amp[slot] = level * loudness * (0.15f + a * a)
        val pan = rng.nextFloat()
        panL[slot] = sqrt(1f - pan)
        panR[slot] = sqrt(pan)
        start[slot] = offset
        active[slot] = true
    }

    /** Adds the active droplets into [left] and [right]. */
    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (s in active.indices) {
            if (!active[s]) continue
            var i = start[s].coerceAtLeast(0)
            start[s] = 0
            var a = amp[s]
            var ph = phase[s]
            var f = freq[s]
            while (i < frames && a > 1e-4f) {
                val v = a * sin(ph).toFloat()
                left[i] += v * panL[s]
                right[i] += v * panR[s]
                ph += 2 * PI * f / sampleRate
                f *= rise[s]
                a *= decay[s]
                i++
            }
            if (ph > 2 * PI * 1000) ph %= 2 * PI
            phase[s] = ph
            freq[s] = f
            amp[s] = a
            if (a <= 1e-4f || f > sampleRate * 0.45) active[s] = false
        }
    }
}

/** Shared helper: renders a block of events from [scheduler] into [drops] at [intensity]. */
internal fun scheduleInto(scheduler: DropletScheduler, drops: Droplets, frames: Int, intensity: Float, rng: Rng) {
    scheduler.advance(frames, intensity) { offset -> drops.trigger(offset, 0.6f + 0.4f * rng.nextFloat()) }
}
