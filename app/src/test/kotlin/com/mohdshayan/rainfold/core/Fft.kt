package com.mohdshayan.rainfold.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Test helper: averaged power spectrum (Welch, Hann window) of a mono signal. */
object Fft {
    fun powerSpectrum(signal: FloatArray, n: Int): DoubleArray {
        val acc = DoubleArray(n / 2)
        var segments = 0
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
        var start = 0
        while (start + n <= signal.size) {
            val re = DoubleArray(n) { signal[start + it] * window[it] }
            val im = DoubleArray(n)
            fft(re, im)
            for (k in 0 until n / 2) acc[k] += re[k] * re[k] + im[k] * im[k]
            segments++
            start += n / 2
        }
        for (k in acc.indices) acc[k] /= segments
        return acc
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cos(ang * k)
                    val wi = sin(ang * k)
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                    val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                }
            }
            len = len shl 1
        }
    }
}
