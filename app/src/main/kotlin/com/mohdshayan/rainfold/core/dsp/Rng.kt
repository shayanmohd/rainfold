package com.mohdshayan.rainfold.core.dsp

/**
 * xorshift64 generator. Fast, allocation free and reproducible from its seed, so a block rendered
 * twice from the same seed is identical (the JVM tests rely on that).
 */
class Rng(seed: Long) {
    private var state: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()

    /** Uniform in [-1, 1). */
    fun bipolar(): Float = nextFloat() * 2f - 1f

    fun range(lo: Float, hi: Float): Float = lo + (hi - lo) * nextFloat()

    /** A new generator whose sequence does not overlap this one in practice. */
    fun fork(): Rng = Rng(nextLong() xor 0x5DEECE66DL)
}
