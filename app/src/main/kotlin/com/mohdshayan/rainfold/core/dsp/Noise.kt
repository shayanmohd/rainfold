package com.mohdshayan.rainfold.core.dsp

import kotlin.math.PI
import kotlin.math.exp

/** One mono noise generator. Each colour is scaled to a similar loudness so layers sit together. */
interface NoiseSource {
    fun next(): Float
}

/** Flat spectrum. Uniform white has RMS 0.577; scaled to about 0.25. */
class WhiteNoise(private val rng: Rng) : NoiseSource {
    override fun next(): Float = rng.bipolar() * 0.43f
}

/** Paul Kellet's refined filter: white through six one-pole sections, within 0.05 dB of -3 dB per octave. */
class PinkNoise(private val rng: Rng) : NoiseSource {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    override fun next(): Float {
        val w = rng.bipolar()
        b0 = 0.99886f * b0 + w * 0.0555179f
        b1 = 0.99332f * b1 + w * 0.0750759f
        b2 = 0.96900f * b2 + w * 0.1538520f
        b3 = 0.86650f * b3 + w * 0.3104856f
        b4 = 0.55000f * b4 + w * 0.5329522f
        b5 = -0.7616f * b5 - w * 0.0168980f
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
        b6 = w * 0.115926f
        return out * 0.09f
    }
}

/**
 * Brown (red) noise: white through a leaky integrator, -6 dB per octave above its corner (about 15 Hz),
 * then a DC blocker so the output never wanders off centre.
 */
class BrownNoise(private val rng: Rng, sampleRate: Int) : NoiseSource {
    private val leak = exp(-2.0 * PI * 15.0 / sampleRate).toFloat()
    private var integ = 0f
    private val dc = DcBlocker(sampleRate)

    override fun next(): Float {
        integ = leak * integ + rng.bipolar() * 0.025f
        return dc.process(integ) * 1.4f
    }
}

/** Green noise: white through a broad band-pass centred on 500 Hz, the middle of the hearing range. */
class GreenNoise(private val rng: Rng, sampleRate: Int) : NoiseSource {
    private val bp = Biquad().apply { setBandPass(sampleRate, 500.0, 0.55) }

    override fun next(): Float = bp.process(rng.bipolar()) * 0.62f
}

/** First-order DC blocker with its corner near 5 Hz. */
class DcBlocker(sampleRate: Int) {
    private val r = exp(-2.0 * PI * 5.0 / sampleRate).toFloat()
    private var x1 = 0f
    private var y1 = 0f

    fun process(x: Float): Float {
        val y = x - x1 + r * y1
        x1 = x
        y1 = y
        return y
    }
}

object Noise {
    enum class Colour { WHITE, PINK, BROWN, GREEN }

    fun create(colour: Colour, rng: Rng, sampleRate: Int): NoiseSource = when (colour) {
        Colour.WHITE -> WhiteNoise(rng)
        Colour.PINK -> PinkNoise(rng)
        Colour.BROWN -> BrownNoise(rng, sampleRate)
        Colour.GREEN -> GreenNoise(rng, sampleRate)
    }
}
