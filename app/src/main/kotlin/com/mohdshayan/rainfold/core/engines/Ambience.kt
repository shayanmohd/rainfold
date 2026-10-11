package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Biquad
import com.mohdshayan.rainfold.core.dsp.BrownNoise
import com.mohdshayan.rainfold.core.dsp.PinkNoise
import com.mohdshayan.rainfold.core.dsp.Rng
import com.mohdshayan.rainfold.core.dsp.Smoother
import com.mohdshayan.rainfold.core.dsp.WhiteNoise
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Short filtered-noise bursts with a random level and position: crackles, clacks and clinks. */
private class Bursts(
    private val sampleRate: Int,
    private val rng: Rng,
    lowHz: Double,
    highHz: Double,
    capacity: Int = 24,
) {
    private val src = WhiteNoise(rng.fork())
    private val hp = Biquad().apply { setHighPass(sampleRate, lowHz) }
    private val lp = Biquad().apply { setLowPass(sampleRate, highHz) }
    private val amp = FloatArray(capacity)
    private val decay = FloatArray(capacity)
    private val panL = FloatArray(capacity)
    private val panR = FloatArray(capacity)
    private val start = IntArray(capacity)
    private val active = BooleanArray(capacity)
    private val scratch = FloatArray(4096)

    fun trigger(offset: Int, level: Float, decaySeconds: Float, pan: Float = rng.nextFloat()) {
        val s = active.indexOfFirst { !it }
        if (s < 0) return
        amp[s] = level
        decay[s] = exp(-1.0 / (decaySeconds * sampleRate)).toFloat()
        panL[s] = sqrt(1f - pan)
        panR[s] = sqrt(pan)
        start[s] = offset
        active[s] = true
    }

    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        // One noise stream for all bursts, filtered once: cheaper, and bursts rarely overlap.
        for (i in 0 until frames) scratch[i] = lp.process(hp.process(src.next()))
        for (s in active.indices) {
            if (!active[s]) continue
            var i = start[s]
            start[s] = 0
            var a = amp[s]
            while (i < frames && a > 1e-4f) {
                val v = scratch[i] * a
                left[i] += v * panL[s]
                right[i] += v * panR[s]
                a *= decay[s]
                i++
            }
            amp[s] = a
            if (a <= 1e-4f) active[s] = false
        }
    }
}

/** Fire: a low, flickering bed and Poisson crackles, sometimes in quick clusters. */
class FireVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val bedL = BrownNoise(rng.fork(), sampleRate)
    private val bedR = BrownNoise(rng.fork(), sampleRate)
    private val lpL = Biquad().apply { setLowPass(sampleRate, 320.0) }
    private val lpR = Biquad().apply { setLowPass(sampleRate, 320.0) }
    private val flicker = Smoother(sampleRate, 0.35, 0.7f)
    private val crackles = Bursts(sampleRate, rng.fork(), 1800.0, 9000.0, capacity = 32)
    private val scheduler = DropletScheduler(sampleRate, rng.fork(), minRate = 2.0, maxRate = 22.0)
    private var flickerCountdown = 0

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        val it = intensity.next()
        if (flickerCountdown <= 0) {
            flicker.target = rng.range(0.45f, 1f)
            flickerCountdown = (rng.range(0.2f, 0.9f) * sampleRate).toInt()
        }
        flickerCountdown -= frames
        for (i in 0 until frames) {
            val f = flicker.next()
            left[i] = lpL.process(bedL.next()) * 2.2f * f
            right[i] = lpR.process(bedR.next()) * 2.2f * f
        }
        scheduler.advance(frames, it) { offset ->
            val big = rng.nextFloat() < 0.12f
            crackles.trigger(offset, if (big) rng.range(0.7f, 1.2f) else rng.range(0.15f, 0.6f), rng.range(0.002f, 0.012f))
            if (big) {
                // A cluster: a few smaller pops right after the big one.
                repeat(3) { k -> crackles.trigger((offset + (k + 1) * rng.range(300f, 1400f)).toInt().coerceAtMost(frames - 1), rng.range(0.1f, 0.4f), 0.004f) }
            }
        }
        crackles.render(left, right, frames)
    }
}

/** Air conditioner: a steady, slightly hissy pink rush with a faint compressor hum. */
class AirConVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val srcL = PinkNoise(rng.fork())
    private val srcR = PinkNoise(rng.fork())
    private val lpL = Biquad().apply { setLowPass(sampleRate, 2600.0, 0.6) }
    private val lpR = Biquad().apply { setLowPass(sampleRate, 2600.0, 0.6) }
    private val hpL = Biquad().apply { setHighPass(sampleRate, 90.0) }
    private val hpR = Biquad().apply { setHighPass(sampleRate, 90.0) }
    private var hum = 0.0

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            hum += 2 * PI * 99.0 / sampleRate
            if (hum > 2 * PI) hum -= 2 * PI
            val h = 0.018f * sin(hum).toFloat() + 0.008f * sin(hum * 2).toFloat()
            left[i] = hpL.process(lpL.process(srcL.next())) * 1.3f + h
            right[i] = hpR.process(lpR.process(srcR.next())) * 1.3f + h
        }
    }
}

/**
 * Train: a low swaying rumble and paired rail clacks (front and rear bogie over each rail joint),
 * with the joint spacing drifting as the train's speed wanders.
 */
class TrainVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val rumbleL = BrownNoise(rng.fork(), sampleRate)
    private val rumbleR = BrownNoise(rng.fork(), sampleRate)
    private val lpL = Biquad().apply { setLowPass(sampleRate, 170.0, 0.9) }
    private val lpR = Biquad().apply { setLowPass(sampleRate, 170.0, 0.9) }
    private val sway = Smoother(sampleRate, 2.0, 0.8f)
    private val clacks = Bursts(sampleRate, rng.fork(), 700.0, 3200.0, capacity = 8)
    private val speed = Smoother(sampleRate, 8.0, 1.25f)
    private var toClack = sampleRate
    private var swayCountdown = 0

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        if (swayCountdown <= 0) {
            sway.target = rng.range(0.6f, 1f)
            speed.target = rng.range(1.0f, 1.5f)
            swayCountdown = (rng.range(1.5f, 5f) * sampleRate).toInt()
        }
        swayCountdown -= frames
        for (i in 0 until frames) {
            val s = sway.next()
            left[i] = lpL.process(rumbleL.next()) * 3.2f * s
            right[i] = lpR.process(rumbleR.next()) * 3.2f * s
        }
        toClack -= frames
        while (toClack < 0) {
            val at = (frames + toClack).coerceIn(0, frames - 1)
            val gap = (0.11f * sampleRate).toInt()
            clacks.trigger(at, 0.55f, 0.03f, 0.45f)
            clacks.trigger((at + gap).coerceAtMost(frames - 1), 0.42f, 0.03f, 0.55f)
            toClack += (speed.next() * sampleRate).toInt()
        }
        clacks.render(left, right, frames)
    }
}

/**
 * Crickets: four insects, each a narrow tone near 4.5 kHz pulsed in short chirps of three, with
 * its own rate, pitch, place in the stereo field and random rests, over a faint night bed.
 */
class CricketsVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val count = 4
    private val pitch = DoubleArray(count) { rng.range(4100f, 5000f).toDouble() }
    private val period = IntArray(count) { (rng.range(0.45f, 0.95f) * sampleRate).toInt() }
    private val clock = IntArray(count) { (rng.nextFloat() * sampleRate).toInt() }
    private val level = FloatArray(count) { rng.range(0.05f, 0.12f) }
    private val panL = FloatArray(count)
    private val panR = FloatArray(count)
    private val resting = IntArray(count)
    private val phase = DoubleArray(count)
    private val bedL = BrownNoise(rng.fork(), sampleRate)
    private val bedR = BrownNoise(rng.fork(), sampleRate)
    private val pulse = (0.016f * sampleRate).toInt()

    init {
        for (c in 0 until count) {
            val p = rng.nextFloat()
            panL[c] = sqrt(1f - p)
            panR[c] = sqrt(p)
        }
    }

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        val it = intensity.next()
        for (i in 0 until frames) {
            left[i] = bedL.next() * 0.12f
            right[i] = bedR.next() * 0.12f
        }
        val active = 2 + (it * (count - 2) + 0.5f).toInt()
        for (c in 0 until active) {
            for (i in 0 until frames) {
                if (resting[c] > 0) {
                    resting[c]--
                    continue
                }
                val t = clock[c]
                clock[c] = t + 1
                if (t >= period[c]) {
                    clock[c] = 0
                    if (rng.nextFloat() < 0.04f) resting[c] = (rng.range(1.5f, 6f) * sampleRate).toInt()
                }
                // Three pulses, then silence until the period ends.
                val slot = t / pulse
                if (slot < 6 && slot % 2 == 0) {
                    val within = (t % pulse).toFloat() / pulse
                    val env = sin(PI * within).toFloat()
                    phase[c] += 2 * PI * pitch[c] / sampleRate
                    val v = level[c] * env * sin(phase[c]).toFloat()
                    left[i] += v * panL[c]
                    right[i] += v * panR[c]
                }
            }
            phase[c] %= 2 * PI
        }
    }
}

/**
 * Cafe murmur: several distant talkers, each pink noise through a moving voice-band filter with a
 * syllable-rate envelope and pauses between phrases, so no words can be made out; now and then a cup
 * or spoon clinks.
 */
class CafeVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val talkers = 6
    private val src = Array(talkers) { PinkNoise(rng.fork()) }
    private val bp = Array(talkers) { Biquad() }
    private val env = Array(talkers) { Smoother(sampleRate, 0.05, 0f) }
    private val syllable = IntArray(talkers)
    private val phrase = IntArray(talkers) { (rng.range(0.5f, 3f) * sampleRate).toInt() }
    private val talking = BooleanArray(talkers) { rng.nextFloat() < 0.6f }
    private val panL = FloatArray(talkers)
    private val panR = FloatArray(talkers)
    private val room = Biquad().apply { setLowPass(sampleRate, 2600.0, 0.6) }
    private val roomR = Biquad().apply { setLowPass(sampleRate, 2600.0, 0.6) }
    private val bed = BrownNoise(rng.fork(), sampleRate)
    private val clinkScheduler = DropletScheduler(sampleRate, rng.fork(), minRate = 0.08, maxRate = 0.4)
    private val clinks = Droplets(sampleRate, rng.fork(), minHz = 2600f, maxHz = 5200f, level = 0.05f, capacity = 8)

    init {
        for (k in 0 until talkers) {
            val p = rng.range(0.15f, 0.85f)
            panL[k] = sqrt(1f - p)
            panR[k] = sqrt(p)
            bp[k].setBandPass(sampleRate, rng.range(350f, 1600f).toDouble(), 2.0)
        }
    }

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        val it = intensity.next()
        for (i in 0 until frames) {
            val b = bed.next() * 0.2f
            left[i] = b
            right[i] = b
        }
        for (k in 0 until talkers) {
            phrase[k] -= frames
            if (phrase[k] <= 0) {
                talking[k] = !talking[k] || rng.nextFloat() < 0.3f
                phrase[k] = (rng.range(if (talking[k]) 1.5f else 0.6f, if (talking[k]) 5f else 3f) * sampleRate).toInt()
            }
            syllable[k] -= frames
            if (syllable[k] <= 0) {
                env[k].target = if (talking[k]) rng.range(0.2f, 1f) else 0f
                bp[k].setBandPass(sampleRate, rng.range(350f, 1700f).toDouble(), 2.2)
                syllable[k] = (rng.range(0.12f, 0.3f) * sampleRate).toInt()
            }
            for (i in 0 until frames) {
                val v = bp[k].process(src[k].next()) * env[k].next() * 0.95f
                left[i] += v * panL[k]
                right[i] += v * panR[k]
            }
        }
        for (i in 0 until frames) {
            left[i] = room.process(left[i])
            right[i] = roomR.process(right[i])
        }
        clinkScheduler.advance(frames, it) { offset -> clinks.trigger(offset) }
        clinks.render(left, right, frames)
    }
}
