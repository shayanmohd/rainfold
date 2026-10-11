package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Noise

/** White, pink, brown or green noise, with independent left and right generators. */
class NoiseVoice(colour: Noise.Colour, sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val l = Noise.create(colour, rng.fork(), sampleRate)
    private val r = Noise.create(colour, rng.fork(), sampleRate)

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            left[i] = l.next()
            right[i] = r.next()
        }
    }
}
