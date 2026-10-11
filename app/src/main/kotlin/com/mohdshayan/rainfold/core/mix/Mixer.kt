package com.mohdshayan.rainfold.core.mix

import com.mohdshayan.rainfold.core.dsp.Limiter
import com.mohdshayan.rainfold.core.dsp.Smoother
import com.mohdshayan.rainfold.core.engines.Voice

/**
 * The master bus. [update] may be called from any thread; the render thread picks the newest mix up
 * at the start of its next block. Each layer keeps its voice while the mix changes (matched by
 * [LayerSpec.key]); volume moves through a smoother, a new layer fades in and a removed one fades out
 * before its voice is dropped. A soft limiter guards the sum.
 */
class Mixer(private val sampleRate: Int = SAMPLE_RATE, seed: Long = 0x5EED) {

    private class Slot(val key: Int, val kind: EngineKind, val voice: Voice, val gain: Smoother, val drift: EvolvingDrift) {
        var spec: LayerSpec? = null
        var playable = false
    }

    private data class Pending(val mix: MixSpec, val playable: List<Boolean>, val evolving: Boolean)

    @Volatile
    private var pending: Pending? = null

    @Volatile
    private var masterTarget = 1f

    private var seedCounter = seed
    private val slots = ArrayList<Slot>(8)
    private val master = Smoother(sampleRate, 0.12, 0f)
    private val limiter = Limiter(sampleRate)
    private val tmpL = FloatArray(BLOCK)
    private val tmpR = FloatArray(BLOCK)
    private var evolving = false
    private val blockSeconds = BLOCK.toDouble() / sampleRate

    /** Sets the mix to play. [playable] comes from [FreeTier.playableLayers]; muted layers stay silent. */
    fun update(mix: MixSpec, playable: List<Boolean>, evolvingAllowed: Boolean) {
        pending = Pending(mix, playable, mix.evolving && evolvingAllowed)
    }

    /** The master level target, 0..1: the sleep fade times any ducking. The output glides to it. */
    fun setMasterTarget(value: Float) {
        masterTarget = value.coerceIn(0f, 1f)
    }

    /** True once the output has faded to silence after the master target went to zero. */
    val isSilent: Boolean get() = masterTarget == 0f && master.value < 1e-3f

    val activeLayers: Int get() = slots.size

    /** Fills interleaved stereo [out] with [frames] frames. */
    fun render(out: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(BLOCK, frames - done)
            renderBlock(out, done * 2, n)
            done += n
        }
    }

    private fun renderBlock(out: FloatArray, offset: Int, frames: Int) {
        pending?.let {
            pending = null
            apply(it)
        }
        java.util.Arrays.fill(out, offset, offset + frames * 2, 0f)
        master.target = masterTarget
        val iter = slots.iterator()
        while (iter.hasNext()) {
            val slot = iter.next()
            val spec = slot.spec
            if (spec == null && slot.gain.value < 1e-4f) {
                iter.remove()
                continue
            }
            if (slot.gain.target == 0f && slot.gain.value < 1e-5f) continue
            if (spec != null) {
                var tone = spec.tone
                var intensity = spec.intensity
                if (evolving) {
                    slot.drift.step()
                    tone += slot.drift.toneOffset
                    intensity += slot.drift.intensityOffset
                }
                slot.voice.setTone(tone.coerceIn(-1f, 1f))
                slot.voice.setIntensity(intensity.coerceIn(0f, 1f))
            }
            slot.voice.render(tmpL, tmpR, frames)
            var j = offset
            for (i in 0 until frames) {
                val g = slot.gain.next()
                out[j] += tmpL[i] * g
                out[j + 1] += tmpR[i] * g
                j += 2
            }
        }
        var j = offset
        for (i in 0 until frames) {
            val m = master.next()
            out[j] *= m
            out[j + 1] *= m
            j += 2
        }
        if (offset == 0) {
            limiter.process(out, frames)
        } else {
            val view = out.copyOfRange(offset, offset + frames * 2)
            limiter.process(view, frames)
            System.arraycopy(view, 0, out, offset, view.size)
        }
    }

    private fun apply(p: Pending) {
        evolving = p.evolving
        val seen = HashSet<Int>()
        p.mix.layers.forEachIndexed { index, raw ->
            val layer = raw.clamped()
            seen += layer.key
            var slot = slots.firstOrNull { it.key == layer.key && it.kind == layer.kind && it.spec != null }
            if (slot == null) {
                val voice = layer.kind.createVoice(sampleRate, seedCounter++)
                voice.snap(layer.tone, layer.intensity)
                slot = Slot(layer.key, layer.kind, voice, Smoother(sampleRate, 0.08, 0f), EvolvingDrift(seedCounter++, blockSeconds))
                slots += slot
            }
            slot.drift.setRange(p.mix.driftRange)
            slot.spec = layer
            slot.playable = p.playable.getOrElse(index) { false }
            slot.gain.target = if (slot.playable) layerGain(layer) else 0f
        }
        for (slot in slots) {
            if (slot.key !in seen || p.mix.layers.none { it.key == slot.key && it.kind == slot.kind }) {
                slot.spec = null
                slot.gain.target = 0f
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val BLOCK = 1024

        /** Volume is a fader position; the ear hears it closer to squared. */
        fun layerGain(layer: LayerSpec): Float = layer.volume * layer.volume * layer.kind.loudness
    }
}
