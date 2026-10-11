package com.mohdshayan.rainfold.core.dsp

import kotlin.math.exp

/** One-pole parameter smoother: moves toward [target] with time constant [seconds], per sample. */
class Smoother(sampleRate: Int, seconds: Double, initial: Float = 0f) {
    private val k = (1.0 - exp(-1.0 / (seconds * sampleRate))).toFloat()
    var value: Float = initial
        private set
    var target: Float = initial

    fun next(): Float {
        value += (target - value) * k
        return value
    }

    fun snap(v: Float) {
        value = v
        target = v
    }
}
