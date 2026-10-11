package com.mohdshayan.rainfold.core.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.tanh

/**
 * Stereo-linked soft limiter for the master bus. A peak follower pulls the gain down above
 * [threshold], then a tanh stage keeps every sample strictly inside [ceiling], so six full layers
 * can never clip the output.
 */
class Limiter(sampleRate: Int, private val threshold: Float = 0.6f, val ceiling: Float = 0.89f) {
    private val release = exp(-1.0 / (0.25 * sampleRate)).toFloat()
    private var env = 0f

    /** Processes interleaved stereo [buf] in place, [frames] frames. */
    fun process(buf: FloatArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            // A non-finite sample is dropped here, so it can neither reach the output nor stick in the envelope.
            val l = buf[i].let { if (it.isFinite()) it else 0f }
            val r = buf[i + 1].let { if (it.isFinite()) it else 0f }
            val peak = max(abs(l), abs(r))
            env = if (peak > env) peak else env * release + peak * (1f - release)
            val gain = if (env > threshold) threshold / env else 1f
            buf[i] = soft(l * gain)
            buf[i + 1] = soft(r * gain)
            i += 2
        }
    }

    private fun soft(x: Float): Float {
        if (!x.isFinite()) return 0f
        return (ceiling * tanh((x / ceiling).toDouble())).toFloat().coerceIn(-ceiling, ceiling)
    }
}
