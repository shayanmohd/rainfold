package com.mohdshayan.rainfold.core

import com.mohdshayan.rainfold.core.dsp.Limiter
import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.mix.Mixer
import com.mohdshayan.rainfold.core.mix.MixSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class MixerTest {
    private val ceiling = Limiter(48_000).ceiling

    private fun rms(buf: FloatArray) = sqrt(buf.sumOf { (it * it).toDouble() } / buf.size)

    @Test
    fun sixFullLayersNeverPassTheCeiling() {
        val heavy = listOf(EngineKind.STORM, EngineKind.BROWN, EngineKind.WHITE, EngineKind.FIRE, EngineKind.TRAIN, EngineKind.SURF)
        val mix = MixSpec("Loud", heavy.mapIndexed { i, k -> LayerSpec(k, volume = 1f, tone = -1f, intensity = 1f, key = i) })
        val mixer = Mixer(seed = 9)
        mixer.update(mix, List(6) { true }, evolvingAllowed = false)
        val out = FloatArray(Mixer.BLOCK * 2)
        var peak = 0f
        repeat(48_000 * 20 / Mixer.BLOCK) {
            mixer.render(out, Mixer.BLOCK)
            for (v in out) peak = maxOf(peak, abs(v))
        }
        assertTrue("peak $peak", peak <= ceiling)
        assertTrue("the limiter must not silence the mix, peak $peak", peak > 0.3f)
    }

    @Test
    fun aSeedReproducesItsBlock() {
        fun block(seed: Long): FloatArray {
            val mixer = Mixer(seed = seed)
            mixer.update(MixSpec("Desk", listOf(LayerSpec(EngineKind.RAIN, key = 0), LayerSpec(EngineKind.FAN, key = 1))), listOf(true, true), false)
            val out = FloatArray(Mixer.BLOCK * 2)
            repeat(10) { mixer.render(out, Mixer.BLOCK) }
            return out
        }
        assertArrayEquals(block(11), block(11), 0f)
        assertFalse(block(11).contentEquals(block(12)))
    }

    @Test
    fun aMutedLayerMakesNoSound() {
        val mixer = Mixer(seed = 3)
        mixer.update(MixSpec("Locked", listOf(LayerSpec(EngineKind.SURF, volume = 1f, key = 0))), listOf(false), false)
        val out = FloatArray(Mixer.BLOCK * 2)
        repeat(20) { mixer.render(out, Mixer.BLOCK) }
        assertEquals(0.0, rms(out), 0.0)
    }

    @Test
    fun aRemovedLayerFadesOutAndIsDropped() {
        val mixer = Mixer(seed = 5)
        val two = MixSpec("Two", listOf(LayerSpec(EngineKind.BROWN, key = 0), LayerSpec(EngineKind.PINK, key = 1)))
        mixer.update(two, listOf(true, true), false)
        val out = FloatArray(Mixer.BLOCK * 2)
        repeat(30) { mixer.render(out, Mixer.BLOCK) }
        assertEquals(2, mixer.activeLayers)
        mixer.update(two.copy(layers = two.layers.take(1)), listOf(true), false)
        mixer.render(out, Mixer.BLOCK)
        // The first block after removal still carries the fading layer: no click.
        assertEquals(2, mixer.activeLayers)
        repeat(60) { mixer.render(out, Mixer.BLOCK) }
        assertEquals(1, mixer.activeLayers)
    }

    @Test
    fun masterTargetZeroFadesToSilence() {
        val mixer = Mixer(seed = 8)
        mixer.update(MixSpec("Brown", listOf(LayerSpec(EngineKind.BROWN, volume = 1f, key = 0))), listOf(true), false)
        val out = FloatArray(Mixer.BLOCK * 2)
        repeat(30) { mixer.render(out, Mixer.BLOCK) }
        assertTrue(rms(out) > 0.05)
        mixer.setMasterTarget(0f)
        repeat(60) { mixer.render(out, Mixer.BLOCK) }
        assertTrue(mixer.isSilent)
        assertTrue(rms(out) < 1e-3)
    }

    @Test
    fun everyEngineRendersFiniteAudibleSound() {
        for (kind in EngineKind.entries) {
            val voice = kind.createVoice(48_000, 21)
            voice.snap(0f, 0.8f)
            val l = FloatArray(Mixer.BLOCK)
            val r = FloatArray(Mixer.BLOCK)
            var sum = 0.0
            repeat(48_000 * 12 / Mixer.BLOCK) {
                voice.render(l, r, Mixer.BLOCK)
                for (i in l.indices) {
                    assertTrue("$kind produced a non-finite sample", l[i].isFinite() && r[i].isFinite())
                    sum += l[i] * l[i] + r[i] * r[i]
                }
            }
            val level = sqrt(sum / (2.0 * 48_000 * 12))
            assertTrue("$kind level $level", level in 0.01..0.8)
        }
    }
}
