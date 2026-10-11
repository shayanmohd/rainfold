package com.mohdshayan.rainfold.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RBJ cookbook biquad in transposed direct form II, with double state so low corners stay stable.
 * [magnitudeAt] evaluates the same coefficients' response, which is what the Fold draws.
 */
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0
    private var fs = 48_000

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1
        z1 = b1 * xd - a1 * y + z2
        z2 = b2 * xd - a2 * y
        return y.toFloat()
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    fun setLowPass(sampleRate: Int, freq: Double, q: Double = 0.707) = design(sampleRate, freq, q) { c, alpha ->
        doubleArrayOf((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun setHighPass(sampleRate: Int, freq: Double, q: Double = 0.707) = design(sampleRate, freq, q) { c, alpha ->
        doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    /** Band-pass with 0 dB peak gain. */
    fun setBandPass(sampleRate: Int, freq: Double, q: Double) = design(sampleRate, freq, q) { c, alpha ->
        doubleArrayOf(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
    }

    private inline fun design(sampleRate: Int, freq: Double, q: Double, coeffs: (Double, Double) -> DoubleArray) {
        fs = sampleRate
        val f = freq.coerceIn(10.0, sampleRate * 0.45)
        val w0 = 2 * PI * f / sampleRate
        val alpha = sin(w0) / (2 * q)
        val k = coeffs(cos(w0), alpha)
        val a0 = k[3]
        b0 = k[0] / a0
        b1 = k[1] / a0
        b2 = k[2] / a0
        a1 = k[4] / a0
        a2 = k[5] / a0
    }

    /** |H(e^jw)| at [freq] Hz for the current coefficients. */
    fun magnitudeAt(freq: Double): Double {
        val w = 2 * PI * freq / fs
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        return sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
    }
}

/** One-pole low-pass, for cheap smoothing filters inside engines. */
class OnePole(sampleRate: Int, freq: Double) {
    private var a = 0f
    private var y = 0f
    private val fs = sampleRate

    init {
        setFreq(freq)
    }

    fun setFreq(freq: Double) {
        a = kotlin.math.exp(-2.0 * PI * freq.coerceIn(1.0, fs * 0.45) / fs).toFloat()
    }

    fun process(x: Float): Float {
        y = x + a * (y - x)
        return y
    }
}
