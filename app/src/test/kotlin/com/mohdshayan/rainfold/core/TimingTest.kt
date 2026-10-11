package com.mohdshayan.rainfold.core

import com.mohdshayan.rainfold.core.dsp.Rng
import com.mohdshayan.rainfold.core.engines.DropletScheduler
import com.mohdshayan.rainfold.core.mix.EvolvingDrift
import com.mohdshayan.rainfold.core.mix.FadeCurve
import com.mohdshayan.rainfold.core.mix.SleepPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingTest {
    @Test
    fun fadeCurveIsMonotonicAndEndsAtZero() {
        val fade = 15 * 60_000L
        var last = 1f
        for (t in 0..fade step 1_000L) {
            val g = FadeCurve.gain(t, fade)
            assertTrue("gain rose at $t", g <= last)
            last = g
        }
        assertEquals(1f, FadeCurve.gain(0, fade), 0f)
        assertEquals(0f, FadeCurve.gain(fade, fade), 0f)
        assertEquals(0f, FadeCurve.gain(fade + 1, fade), 0f)
    }

    @Test
    fun sleepPlanFadesInItsLastMinutesAndStopsOnTime() {
        val plan = SleepPlan.of(startedAt = 0L, minutes = 240, fadeMinutes = 15)
        assertEquals(240 * 60_000L, plan.endsAt)
        assertEquals(225 * 60_000L, plan.fadeStartsAt)
        assertEquals(1f, plan.gainAt(200 * 60_000L), 0f)
        assertTrue(plan.gainAt(232 * 60_000L) in 0.01f..0.99f)
        assertEquals(0f, plan.gainAt(plan.endsAt), 0f)
        assertTrue(plan.finishedAt(plan.endsAt))
        // A fade longer than the timer is cut to the timer.
        val short = SleepPlan.of(0L, minutes = 15, fadeMinutes = 30)
        assertEquals(0L, short.fadeStartsAt)
        // Out-of-range input is clamped to the 15 minute to 10 hour range.
        assertEquals(600 * 60_000L, SleepPlan.of(0L, 5000, 10).durationMs)
    }

    @Test
    fun dropletRateScalesWithIntensity() {
        val fs = 48_000
        fun count(intensity: Float): Int {
            val s = DropletScheduler(fs, Rng(99), minRate = 20.0, maxRate = 320.0)
            var n = 0
            repeat(fs * 120 / 1024) { s.advance(1024, intensity) { n++ } }
            return n
        }
        val seconds = 120 * 1024.0 * (fs / 1024) / fs
        val low = count(0.2f) / seconds
        val high = count(0.8f) / seconds
        assertEquals(80.0, low, 8.0)
        assertEquals(260.0, high, 20.0)
    }

    @Test
    fun evolvingDriftStaysInItsBandForEightHours() {
        val block = 1024.0 / 48_000
        val drift = EvolvingDrift(seed = 4, blockSeconds = block)
        drift.setRange(0.3f)
        var minTone = 0f
        var maxTone = 0f
        repeat((8 * 3600 / block).toInt()) {
            drift.step()
            assertTrue(drift.toneOffset in -0.3f..0.3f)
            assertTrue(drift.intensityOffset in -0.3f..0.3f)
            minTone = minOf(minTone, drift.toneOffset)
            maxTone = maxOf(maxTone, drift.toneOffset)
        }
        assertTrue("drift should actually move: $minTone..$maxTone", maxTone - minTone > 0.2f)
    }
}
