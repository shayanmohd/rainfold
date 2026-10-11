package com.mohdshayan.rainfold.core.mix

import com.mohdshayan.rainfold.core.dsp.Noise
import com.mohdshayan.rainfold.core.engines.AirConVoice
import com.mohdshayan.rainfold.core.engines.CafeVoice
import com.mohdshayan.rainfold.core.engines.CricketsVoice
import com.mohdshayan.rainfold.core.engines.FanVoice
import com.mohdshayan.rainfold.core.engines.FireVoice
import com.mohdshayan.rainfold.core.engines.NoiseVoice
import com.mohdshayan.rainfold.core.engines.RainVoice
import com.mohdshayan.rainfold.core.engines.StormVoice
import com.mohdshayan.rainfold.core.engines.StreamVoice
import com.mohdshayan.rainfold.core.engines.SurfVoice
import com.mohdshayan.rainfold.core.engines.TrainVoice
import com.mohdshayan.rainfold.core.engines.Voice
import com.mohdshayan.rainfold.core.engines.WindVoice

/**
 * Every sound Rainfold can make. [id] is what Room and the export file store, so it never changes.
 * [free] sounds are never locked. [hasIntensity] sounds show an intensity control. [loudness] evens
 * out the engines' raw levels (measured RMS at intensity 0.5) so a new layer sits beside the others.
 */
enum class EngineKind(val id: String, val free: Boolean, val hasIntensity: Boolean, val loudness: Float) {
    WHITE("WHITE", true, false, 0.65f),
    PINK("PINK", true, false, 1.15f),
    BROWN("BROWN", true, false, 0.85f),
    GREEN("GREEN", true, false, 2.0f),
    FAN("FAN", true, false, 0.95f),
    RAIN("RAIN", true, true, 2.2f),
    STORM("STORM", false, true, 1.0f),
    WIND("WIND", false, true, 2.0f),
    SURF("SURF", false, true, 1.15f),
    STREAM("STREAM", false, true, 2.1f),
    FIRE("FIRE", false, true, 0.45f),
    AIR_CON("AIR_CON", false, false, 1.5f),
    TRAIN("TRAIN", false, false, 0.28f),
    CRICKETS("CRICKETS", false, true, 3.0f),
    CAFE("CAFE", false, true, 2.6f),
    ;

    fun createVoice(sampleRate: Int, seed: Long): Voice = when (this) {
        WHITE -> NoiseVoice(Noise.Colour.WHITE, sampleRate, seed)
        PINK -> NoiseVoice(Noise.Colour.PINK, sampleRate, seed)
        BROWN -> NoiseVoice(Noise.Colour.BROWN, sampleRate, seed)
        GREEN -> NoiseVoice(Noise.Colour.GREEN, sampleRate, seed)
        FAN -> FanVoice(sampleRate, seed)
        RAIN -> RainVoice(sampleRate, seed)
        STORM -> StormVoice(sampleRate, seed)
        WIND -> WindVoice(sampleRate, seed)
        SURF -> SurfVoice(sampleRate, seed)
        STREAM -> StreamVoice(sampleRate, seed)
        FIRE -> FireVoice(sampleRate, seed)
        AIR_CON -> AirConVoice(sampleRate, seed)
        TRAIN -> TrainVoice(sampleRate, seed)
        CRICKETS -> CricketsVoice(sampleRate, seed)
        CAFE -> CafeVoice(sampleRate, seed)
    }

    companion object {
        private val byId = entries.associateBy { it.id }

        /** Null for an engine this version does not know, so an import can skip it. */
        fun fromId(id: String): EngineKind? = byId[id]
    }
}

/**
 * One layer of a mix. [key] identifies the layer inside one live mix so the mixer can keep its voice
 * while the list changes; it is never stored. Volume 0..1, tone -1 (darker) to 1 (brighter),
 * intensity 0..1.
 */
data class LayerSpec(
    val kind: EngineKind,
    val volume: Float = 0.6f,
    val tone: Float = 0f,
    val intensity: Float = 0.5f,
    val key: Int = 0,
) {
    fun clamped() = copy(
        volume = volume.coerceIn(0f, 1f),
        tone = tone.coerceIn(-1f, 1f),
        intensity = intensity.coerceIn(0f, 1f),
    )
}

/** A whole mix as the listener hears it. */
data class MixSpec(
    val name: String,
    val layers: List<LayerSpec>,
    val evolving: Boolean = false,
    val driftRange: Float = 0.3f,
)
