package com.mohdshayan.rainfold.core.engines

import com.mohdshayan.rainfold.core.dsp.Biquad
import com.mohdshayan.rainfold.core.dsp.BrownNoise
import com.mohdshayan.rainfold.core.dsp.PinkNoise
import com.mohdshayan.rainfold.core.dsp.Smoother
import kotlin.math.PI
import kotlin.math.sin

/**
 * A box fan: brown and pink noise under a low-pass, amplitude-modulated at a blade-pass rate that
 * drifts slowly (no two minutes repeat), with a faint motor hum underneath.
 */
class FanVoice(sampleRate: Int, seed: Long) : Voice(sampleRate, seed) {
    private val brownL = BrownNoise(rng.fork(), sampleRate)
    private val brownR = BrownNoise(rng.fork(), sampleRate)
    private val pinkL = PinkNoise(rng.fork())
    private val pinkR = PinkNoise(rng.fork())
    private val lpL = Biquad().apply { setLowPass(sampleRate, LOWPASS_HZ, 0.6) }
    private val lpR = Biquad().apply { setLowPass(sampleRate, LOWPASS_HZ, 0.6) }
    private val bladeHz = Smoother(sampleRate, 6.0, 21f)
    private var bladePhase = 0.0
    private var humPhase = 0.0
    private var driftCountdown = 0

    override fun renderRaw(left: FloatArray, right: FloatArray, frames: Int) {
        if (driftCountdown <= 0) {
            bladeHz.target = rng.range(18.5f, 23.5f)
            driftCountdown = (rng.range(4f, 11f) * sampleRate).toInt()
        }
        driftCountdown -= frames
        for (i in 0 until frames) {
            bladePhase += 2 * PI * bladeHz.next() / sampleRate
            if (bladePhase > 2 * PI) bladePhase -= 2 * PI
            humPhase += 2 * PI * 118.0 / sampleRate
            if (humPhase > 2 * PI) humPhase -= 2 * PI
            val mod = 1f + 0.10f * sin(bladePhase).toFloat()
            val hum = 0.012f * sin(humPhase).toFloat()
            left[i] = lpL.process(brownL.next() * 0.75f + pinkL.next() * 0.45f) * mod + hum
            right[i] = lpR.process(brownR.next() * 0.75f + pinkR.next() * 0.45f) * mod + hum
        }
    }

    companion object {
        const val LOWPASS_HZ = 1400.0
    }
}
