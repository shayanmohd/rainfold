package com.mohdshayan.rainfold.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tone control. The signal is split at [PIVOT_HZ] by a one-pole low-pass; tone -1 lifts the lows and
 * cuts the highs by [MAX_DB] (darker), +1 does the opposite (brighter), 0 is flat. The tone moves
 * through a smoother, so a drag never clicks.
 */
class Tilt(private val sampleRate: Int) {
    private val a = exp(-2.0 * PI * PIVOT_HZ / sampleRate).toFloat()
    private var lp = 0f
    private val tone = Smoother(sampleRate, 0.06)

    fun setTone(value: Float) {
        tone.target = value.coerceIn(-1f, 1f)
    }

    fun snapTone(value: Float) {
        tone.snap(value.coerceIn(-1f, 1f))
    }

    fun process(x: Float): Float {
        val t = tone.next()
        lp = x + a * (lp - x)
        val (gl, gh) = gains(t)
        return lp * gl + (x - lp) * gh
    }

    companion object {
        const val PIVOT_HZ = 700.0
        const val MAX_DB = 7.0

        fun gains(tone: Float): Pair<Float, Float> {
            val low = 10.0.pow(-tone * MAX_DB / 20.0).toFloat()
            val high = 10.0.pow(tone * MAX_DB / 20.0).toFloat()
            // Keep overall loudness roughly level as the tilt moves.
            val norm = 1f / sqrt((low * low + high * high) / 2f)
            return (low * norm) to (high * norm)
        }

        /** The same filter's magnitude at [freq] for [tone], for the Fold. */
        fun magnitudeAt(tone: Float, freq: Double, sampleRate: Int = 48_000): Double {
            val a = exp(-2.0 * PI * PIVOT_HZ / sampleRate)
            val w = 2 * PI * freq / sampleRate
            // Hlp = (1 - a) / (1 - a e^-jw)
            val dr = 1 - a * cos(w)
            val di = a * sin(w)
            val den = dr * dr + di * di
            val lr = (1 - a) * dr / den
            val li = -(1 - a) * di / den
            val (gl, gh) = gains(tone)
            val r = gl * lr + gh * (1 - lr)
            val i = gl * li + gh * (-li)
            return sqrt(r * r + i * i)
        }
    }
}
