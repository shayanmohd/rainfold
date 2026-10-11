package com.mohdshayan.rainfold.core

import com.mohdshayan.rainfold.core.dsp.Noise
import com.mohdshayan.rainfold.core.dsp.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class NoiseSpectrumTest {
    private val fs = 48_000
    private val n = 4096

    /** Mean power density per octave band from 250 Hz to 8 kHz, then the least-squares slope in dB per octave. */
    private fun slopeDbPerOctave(colour: Noise.Colour): Double {
        val src = Noise.create(colour, Rng(42), fs)
        val signal = FloatArray(fs * 20) { src.next() }
        val p = Fft.powerSpectrum(signal, n)
        val binHz = fs.toDouble() / n
        val edges = listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)
        val levels = edges.zipWithNext().map { (lo, hi) ->
            val bins = (lo / binHz).toInt() until (hi / binHz).toInt()
            10 * log10(bins.map { p[it] }.average())
        }
        val xs = levels.indices.map { it.toDouble() }
        val mx = xs.average()
        val my = levels.average()
        return xs.zip(levels).sumOf { (x, y) -> (x - mx) * (y - my) } / xs.sumOf { (it - mx) * (it - mx) }
    }

    @Test
    fun pinkFallsThreeDbPerOctave() {
        assertEquals(-3.0, slopeDbPerOctave(Noise.Colour.PINK), 1.5)
    }

    @Test
    fun brownFallsSixDbPerOctave() {
        assertEquals(-6.0, slopeDbPerOctave(Noise.Colour.BROWN), 1.5)
    }

    @Test
    fun whiteIsFlat() {
        assertEquals(0.0, slopeDbPerOctave(Noise.Colour.WHITE), 1.0)
    }

    @Test
    fun brownNeverDriftsOffCentre() {
        val src = Noise.create(Noise.Colour.BROWN, Rng(7), fs)
        val signal = FloatArray(fs * 60) { src.next() }
        val mean = signal.average()
        assertTrue("mean $mean", kotlin.math.abs(mean) < 0.02)
    }
}
