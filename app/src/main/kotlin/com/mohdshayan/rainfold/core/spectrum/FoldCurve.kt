package com.mohdshayan.rainfold.core.spectrum

import com.mohdshayan.rainfold.core.dsp.Biquad
import com.mohdshayan.rainfold.core.dsp.Tilt
import com.mohdshayan.rainfold.core.mix.EngineKind
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * The Fold's band edges: each layer's spectrum shape, lows at left, from the same filter maths the
 * engines use (noise slopes, the engines' biquads, and the tone tilt). [shape] returns [points]
 * values in 0..1, where 1 is the loudest part of that layer's spectrum and 0 is [FLOOR_DB] below it.
 */
object FoldCurve {
    const val LOW_HZ = 40.0
    const val HIGH_HZ = 16_000.0
    const val FLOOR_DB = 48.0
    private const val FS = 48_000

    fun frequencyAt(index: Int, points: Int): Double =
        LOW_HZ * (HIGH_HZ / LOW_HZ).pow(index.toDouble() / (points - 1).coerceAtLeast(1))

    fun shape(kind: EngineKind, tone: Float, points: Int = 48): FloatArray {
        val db = DoubleArray(points) { i ->
            val f = frequencyAt(i, points)
            baseDb(kind, f) + 20 * log10(Tilt.magnitudeAt(tone, f, FS))
        }
        val top = db.max()
        return FloatArray(points) { i -> (((db[i] - top) + FLOOR_DB) / FLOOR_DB).coerceIn(0.0, 1.0).toFloat() }
    }

    private fun lp(f: Double, fc: Double, q: Double = 0.707) = Biquad().apply { setLowPass(FS, fc, q) }.magnitudeAt(f)
    private fun hp(f: Double, fc: Double, q: Double = 0.707) = Biquad().apply { setHighPass(FS, fc, q) }.magnitudeAt(f)
    private fun bp(f: Double, fc: Double, q: Double) = Biquad().apply { setBandPass(FS, fc, q) }.magnitudeAt(f)

    private fun dB(x: Double) = 20 * log10(max(x, 1e-6))

    /** Pink falls 3 dB per octave, brown 6 dB per octave above its 15 Hz corner. */
    private fun pink(f: Double) = -10 * log10(f / LOW_HZ)
    private fun brown(f: Double) = -10 * log10(1 + (f / 15.0).pow(2))

    /** Spectrum level of each engine's main path, in dB, before the tone tilt. */
    fun baseDb(kind: EngineKind, f: Double): Double = when (kind) {
        EngineKind.WHITE -> 0.0
        EngineKind.PINK -> pink(f)
        EngineKind.BROWN -> brown(f)
        EngineKind.GREEN -> dB(bp(f, 500.0, 0.55))
        EngineKind.FAN -> brown(f) + dB(lp(f, 1400.0, 0.6)) + 6
        EngineKind.RAIN -> pink(f) + dB(hp(f, 420.0)) + dB(lp(f, 9000.0)) + 4 * bump(f, 2800.0)
        EngineKind.STORM -> max(pink(f) + dB(hp(f, 420.0)) + dB(lp(f, 9000.0)), brown(f) + dB(lp(f, 400.0, 0.8)) + 8)
        EngineKind.WIND -> pink(f) + dB(bp(f, 600.0, 1.4)) + 3
        EngineKind.SURF -> max(pink(f) + dB(lp(f, 2500.0)), brown(f) + 4)
        EngineKind.STREAM -> pink(f) + dB(bp(f, 1500.0, 0.5)) + 4 * bump(f, 900.0)
        EngineKind.FIRE -> max(brown(f) + dB(lp(f, 320.0)) + 6, dB(hp(f, 1800.0)) - 24 + dB(lp(f, 9000.0)))
        EngineKind.AIR_CON -> pink(f) + dB(lp(f, 2600.0, 0.6)) + dB(hp(f, 90.0))
        EngineKind.TRAIN -> brown(f) + dB(lp(f, 170.0, 0.9)) + 10 + max(0.0, dB(bp(f, 1500.0, 0.8)) + 2)
        EngineKind.CRICKETS -> max(brown(f) - 14, dB(bp(f, 4500.0, 6.0)))
        EngineKind.CAFE -> pink(f) + dB(bp(f, 900.0, 0.9)) + dB(lp(f, 2600.0, 0.6))
    }

    /** A soft resonance hump of up to 1 around [center], one octave wide. */
    private fun bump(f: Double, center: Double): Double {
        val oct = log10(f / center) / log10(2.0)
        return max(0.0, 1 - oct * oct)
    }
}
