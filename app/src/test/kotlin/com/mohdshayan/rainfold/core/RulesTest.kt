package com.mohdshayan.rainfold.core

import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.FreeTier
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.mix.MixCodec
import com.mohdshayan.rainfold.core.mix.MixSpec
import com.mohdshayan.rainfold.core.spectrum.FoldCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test
    fun freeTierAllowsTwoLayersAndThreeMixes() {
        assertTrue(FreeTier.canAddLayer(entitled = false, layerCount = 1))
        assertFalse(FreeTier.canAddLayer(entitled = false, layerCount = 2))
        assertTrue(FreeTier.canAddLayer(entitled = true, layerCount = 5))
        assertFalse(FreeTier.canAddLayer(entitled = true, layerCount = 6))
        assertTrue(FreeTier.canSaveMix(entitled = false, savedCount = 2))
        assertFalse(FreeTier.canSaveMix(entitled = false, savedCount = 3))
        assertTrue(FreeTier.canSaveMix(entitled = true, savedCount = 300))
        assertFalse(FreeTier.evolvingAllowed(false))
    }

    @Test
    fun aSixLayerMixAfterARelockKeepsEveryLayerAndPlaysTheFirstTwoFreeOnes() {
        val layers = listOf(EngineKind.SURF, EngineKind.BROWN, EngineKind.STORM, EngineKind.RAIN, EngineKind.FAN, EngineKind.CAFE)
            .mapIndexed { i, k -> LayerSpec(k, key = i) }
        assertEquals(listOf(false, true, false, true, false, false), FreeTier.playableLayers(false, layers))
        assertEquals(List(6) { true }, FreeTier.playableLayers(true, layers))
        // Free sounds are never locked.
        EngineKind.entries.filter { it.free }.forEach { assertTrue(FreeTier.canUseEngine(false, it)) }
        assertEquals(
            setOf(EngineKind.WHITE, EngineKind.PINK, EngineKind.BROWN, EngineKind.GREEN, EngineKind.FAN, EngineKind.RAIN),
            EngineKind.entries.filter { it.free }.toSet(),
        )
    }

    @Test
    fun codecRoundTripsMixes() {
        val mixes = listOf(
            MixSpec("Desk", listOf(LayerSpec(EngineKind.BROWN, 0.62f, -0.35f, 0.5f), LayerSpec(EngineKind.RAIN, 0.4f, 0.1f, 0.7f, key = 1))),
            MixSpec("Night shift", listOf(LayerSpec(EngineKind.STORM, 0.8f, 0f, 0.9f)), evolving = true, driftRange = 0.2f),
        )
        val decoded = MixCodec.decode(MixCodec.encode(mixes)) as MixCodec.Decoded.Mixes
        assertEquals(0, decoded.skippedLayers)
        assertEquals(mixes.map { it.name }, decoded.mixes.map { it.name })
        assertEquals(mixes[0].layers.map { it.kind to it.volume }, decoded.mixes[0].layers.map { it.kind to it.volume })
        assertEquals(-0.35f, decoded.mixes[0].layers[0].tone, 1e-6f)
        assertTrue(decoded.mixes[1].evolving)
        assertEquals(0.2f, decoded.mixes[1].driftRange, 1e-6f)
    }

    @Test
    fun codecRejectsOtherFilesAndNeverWritesTheUnlock() {
        assertEquals(MixCodec.Decoded.NotAMixFile, MixCodec.decode("""{"format":"other","schema":1,"mixes":[]}"""))
        assertEquals(MixCodec.Decoded.NotAMixFile, MixCodec.decode("not json at all"))
        assertEquals(MixCodec.Decoded.NotAMixFile, MixCodec.decode("[1,2,3]"))
        assertEquals(MixCodec.Decoded.NewerSchema, MixCodec.decode("""{"format":"rainfold-mixes","schema":9,"mixes":[]}"""))
        val text = MixCodec.encode(listOf(MixSpec("Desk", listOf(LayerSpec(EngineKind.BROWN)))))
        assertFalse(text.contains("unlock", ignoreCase = true))
        assertFalse(text.contains("entitle", ignoreCase = true))
        // Unknown keys are ignored, unknown engines skipped, a smuggled unlock key does nothing.
        val foreign = """{"format":"rainfold-mixes","schema":1,"unlocked":true,"mixes":[{"name":"Desk","layers":[
            {"engine":"BROWN","volume":0.5,"tone":0,"intensity":0.5},{"engine":"WHALES","volume":1}]}]}"""
        val decoded = MixCodec.decode(foreign) as MixCodec.Decoded.Mixes
        assertEquals(1, decoded.skippedLayers)
        assertEquals(listOf(EngineKind.BROWN), decoded.mixes[0].layers.map { it.kind })
    }

    @Test
    fun clashingNamesGetANumber() {
        assertEquals("Desk", MixCodec.uniqueName("Desk", listOf("Bus route")))
        assertEquals("Desk (2)", MixCodec.uniqueName("Desk", listOf("Desk")))
        assertEquals("Desk (3)", MixCodec.uniqueName("Desk", listOf("desk", "Desk (2)")))
    }

    @Test
    fun foldCurvesFollowTheSpectrum() {
        val white = FoldCurve.shape(EngineKind.WHITE, tone = 0f)
        assertTrue(white.max() - white.min() < 0.2f)
        val brown = FoldCurve.shape(EngineKind.BROWN, tone = 0f)
        assertTrue(brown.first() > 0.95f && brown.last() < 0.05f)
        val green = FoldCurve.shape(EngineKind.GREEN, tone = 0f)
        val peak = green.indices.maxBy { green[it] }
        assertTrue("green peaks mid-band", peak in 10..30)
        val dark = FoldCurve.shape(EngineKind.PINK, tone = -1f)
        val bright = FoldCurve.shape(EngineKind.PINK, tone = 1f)
        assertTrue(dark.last() < bright.last())
    }
}
