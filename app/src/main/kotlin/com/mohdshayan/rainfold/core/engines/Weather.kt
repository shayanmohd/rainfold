package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Biquad
import com.mohdshayan.rainfold.core.dsp.BrownNoise
import com.mohdshayan.rainfold.core.dsp.OnePole
import com.mohdshayan.rainfold.core.dsp.PinkNoise
import com.mohdshayan.rainfold.core.dsp.Rng
import com.mohdshayan.rainfold.core.dsp.Smoother
import com.mohdshayan.rainfold.core.dsp.WhiteNoise
import kotlin.math.exp

/**
 * Thunder rolls, synthesised: a short bright crack, then several overlapping low rumbles that decay
 * over seconds, all from brown noise under a falling low-pass. Rolls arrive at random, every 14 to 55
 * seconds, sooner when the storm is stronger.
 */
class Thunder(private val sampleRate: Int, private val rng: Rng) {
    private val src = BrownNoise(rng.fork(), sampleRate)
    private val crackSrc = WhiteNoise(rng.fork())
    private val lp = Biquad()
    private val crackLp = Biquad().apply { setLowPass(sampleRate, 2400.0) }
    private var framesToNext = (rng.range(4f, 12f) * sampleRate).toInt()
    private var t = -1
    private var length = 0
    private val bumpAt = IntArray(5)
    private val bumpGain = FloatArray(5)
    private var level = 1f
    private var crack = 0f

    fun render(left: FloatArray, right: FloatArray, frames: Int, intensity: Float) {
        if (t < 0) {
            framesToNext -= frames
            if (framesToNext > 0) return
            start(intensity)
        }
        // A new cutoff per block: the roll darkens as it fades.
        val p = t.toFloat() / length
        lp.setLowPass(sampleRate, 520.0 - 380.0 * p, 0.8)
        for (i in 0 until frames) {
            if (t >= length) {
                t = -1
                framesToNext = (rng.range(14f, 55f) * (1.25f - 0.6f * intensity) * sampleRate).toInt()
                return
            }
            var env = 0f
            for (b in bumpAt.indices) {
                val d = t - bumpAt[b]
                if (d >= 0) {
                    val sec = d.toFloat() / sampleRate
                    env += bumpGain[b] * (1f - exp(-sec * 9f)) * exp(-sec * 0.9f)
                }
            }
            val crackOut = crackLp.process(crackSrc.next()) * crack
            crack *= CRACK_DECAY
            val v = lp.process(src.next()) * env * level * 2.4f + crackOut
            left[i] += v
            right[i] += v * 0.92f
            t++
        }
    }

    private fun start(intensity: Float) {
        t = 0
        length = (rng.range(6f, 11f) * sampleRate).toInt()
        level = 0.45f + 0.55f * intensity * rng.range(0.6f, 1f)
        crack = if (rng.nextFloat() < 0.5f) rng.range(0.1f, 0.35f) * level else 0f
        for (b in bumpAt.indices) {
            bumpAt[b] = (rng.range(0f, 3.5f) * sampleRate).toInt()
            bumpGain[b] = rng.range(0.25f, 1f)
        }
    }

    private companion object {
        const val CRACK_DECAY = 0.99985f
    }
}

/** Storm: heavy rain with thunder rolling in at random. */
class StormVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val rain = RainVoice(sampleRate, rng.nextLong(), heavy = true)
    private val thunder = Thunder(sampleRate, rng.fork())

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        val it = intensity.next()
        rain.setIntensity(0.55f + 0.45f * it)
        rain.render(left, right, frames)
        thunder.render(left, right, frames, it)
    }
}

/**
 * Wind: pink noise through a resonant band-pass whose centre and level follow a gust envelope.
 * Gust targets change every 2 to 7 seconds and glide slowly; left and right gust apart.
 */
class WindVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val srcL = PinkNoise(rng.fork())
    private val srcR = PinkNoise(rng.fork())
    private val lowL = BrownNoise(rng.fork(), sampleRate)
    private val lowR = BrownNoise(rng.fork(), sampleRate)
    private val bpL = Biquad()
    private val bpR = Biquad()
    private val gustL = Smoother(sampleRate, 1.6, 0.5f)
    private val gustR = Smoother(sampleRate, 1.9, 0.5f)
    private var countdown = 0

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        val it = intensity.next()
        if (countdown <= 0) {
            gustL.target = rng.range(0.15f, 0.55f + 0.45f * it)
            gustR.target = (gustL.target + rng.range(-0.15f, 0.15f)).coerceIn(0.1f, 1f)
            countdown = (rng.range(2f, 7f) * sampleRate).toInt()
        }
        countdown -= frames
        val gl = gustL.value
        val gr = gustR.value
        bpL.setBandPass(sampleRate, 260.0 + 700.0 * gl, 1.4)
        bpR.setBandPass(sampleRate, 260.0 + 700.0 * gr, 1.4)
        for (i in 0 until frames) {
            val a = gustL.next()
            val b = gustR.next()
            left[i] = bpL.process(srcL.next()) * (0.6f + 2.2f * a) + lowL.next() * 0.35f * a
            right[i] = bpR.process(srcR.next()) * (0.6f + 2.2f * b) + lowR.next() * 0.35f * b
        }
    }
}

/**
 * Surf: swells on random 8 to 14 second envelopes. Each wave rises over a few seconds, breaks
 * (brighter and louder at the crest) and drains away; a low wash never stops.
 */
class SurfVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val srcL = PinkNoise(rng.fork())
    private val srcR = PinkNoise(rng.fork())
    private val washL = BrownNoise(rng.fork(), sampleRate)
    private val washR = BrownNoise(rng.fork(), sampleRate)
    private val lpL = OnePole(sampleRate, 600.0)
    private val lpR = OnePole(sampleRate, 600.0)
    private var t = 0
    private var period = 1
    private var rise = 1
    private var peak = 1f
    private var lag = 0

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        val it = intensity.next()
        if (t >= period) newWave(it)
        val envL = envelope(t)
        val envR = envelope(t - lag)
        lpL.setFreq(350.0 + 4200.0 * envL)
        lpR.setFreq(350.0 + 4200.0 * envR)
        for (i in 0 until frames) {
            val el = envelope(t)
            val er = envelope(t - lag)
            left[i] = lpL.process(srcL.next()) * (0.25f + 2.1f * el) + washL.next() * 0.45f
            right[i] = lpR.process(srcR.next()) * (0.25f + 2.1f * er) + washR.next() * 0.45f
            t++
        }
    }

    private fun newWave(intensity: Float) {
        t = 0
        period = (rng.range(8f, 14f) * sampleRate).toInt()
        rise = (period * rng.range(0.28f, 0.42f)).toInt()
        peak = rng.range(0.55f, 0.8f) + 0.2f * intensity
        lag = (rng.range(0.05f, 0.4f) * sampleRate).toInt()
    }

    private fun envelope(frame: Int): Float {
        if (frame < 0) return 0f
        return if (frame < rise) {
            val p = frame.toFloat() / rise
            peak * p * p
        } else {
            val p = (frame - rise).toFloat() / (period - rise).coerceAtLeast(1)
            peak * exp(-p * 4.2f)
        }
    }
}

/**
 * Stream: water over stones, as a dense spray of small low bubbles over a thin pink wash.
 */
class StreamVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val bedL = PinkNoise(rng.fork())
    private val bedR = PinkNoise(rng.fork())
    private val bpL = Biquad().apply { setBandPass(sampleRate, 1500.0, 0.5) }
    private val bpR = Biquad().apply { setBandPass(sampleRate, 1500.0, 0.5) }
    private val scheduler = DropletScheduler(sampleRate, rng.fork(), minRate = 90.0, maxRate = 520.0)
    private val drops = Droplets(sampleRate, rng.fork(), minHz = 380f, maxHz = 1700f, level = 0.16f, capacity = 160)

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        var it = 0f
        for (i in 0 until frames) {
            it = intensity.next()
            left[i] = bpL.process(bedL.next()) * (0.5f + 0.5f * it)
            right[i] = bpR.process(bedR.next()) * (0.5f + 0.5f * it)
        }
        scheduleInto(scheduler, drops, frames, it, rng)
        drops.render(left, right, frames)
    }
}
