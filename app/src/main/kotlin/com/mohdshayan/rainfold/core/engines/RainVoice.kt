package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Biquad
import com.mohdshayan.rainfold.core.dsp.PinkNoise

/**
 * Rain: a filtered pink-noise bed (the wash of distant drops) plus Poisson-scheduled droplets near
 * the listener. Intensity sets the droplet rate and the bed level together.
 */
class RainVoice(sampleRate: Int, seed: Long, private val heavy: Boolean = false) : Voice(sampleRate, seed) {
    private val bedL = PinkNoise(rng.fork())
    private val bedR = PinkNoise(rng.fork())
    private val hpL = Biquad().apply { setHighPass(sampleRate, 420.0) }
    private val hpR = Biquad().apply { setHighPass(sampleRate, 420.0) }
    private val lpL = Biquad().apply { setLowPass(sampleRate, 9000.0) }
    private val lpR = Biquad().apply { setLowPass(sampleRate, 9000.0) }
    val scheduler = DropletScheduler(sampleRate, rng.fork(), minRate = MIN_RATE, maxRate = MAX_RATE)
    private val drops = Droplets(sampleRate, rng.fork(), minHz = 1300f, maxHz = 4800f, level = 0.22f, capacity = 128)

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        var it = 0f
        for (i in 0 until frames) {
            it = intensity.next()
            val bed = 0.35f + 0.9f * it
            left[i] = lpL.process(hpL.process(bedL.next())) * bed
            right[i] = lpR.process(hpR.process(bedR.next())) * bed
        }
        val drive = if (heavy) (0.55f + 0.45f * it) else it
        scheduleInto(scheduler, drops, frames, drive, rng)
        drops.render(left, right, frames)
    }

    companion object {
        const val MIN_RATE = 20.0
        const val MAX_RATE = 320.0
    }
}
