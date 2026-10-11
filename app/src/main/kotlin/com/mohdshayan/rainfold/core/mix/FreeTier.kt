package com.mohdshayan.rainfold.core.mix

/**
 * What the free tier allows and what the unlock adds. Every rule takes [entitled] as a plain value
 * so it is testable on the JVM and the service, tile and widget can apply it without a screen.
 * Nothing here ever deletes or hides a mix: over-limit layers are muted, not removed.
 */
object FreeTier {
    const val FREE_LAYERS = 2
    const val MAX_LAYERS = 6
    const val FREE_MIXES = 3

    fun maxLayers(entitled: Boolean): Int = if (entitled) MAX_LAYERS else FREE_LAYERS

    fun canAddLayer(entitled: Boolean, layerCount: Int): Boolean = layerCount < maxLayers(entitled)

    /** True while saving one more mix stays inside the limit. The cap limits saving, never import. */
    fun canSaveMix(entitled: Boolean, savedCount: Int): Boolean = entitled || savedCount < FREE_MIXES

    fun canUseEngine(entitled: Boolean, kind: EngineKind): Boolean = entitled || kind.free

    /**
     * Which layers sound. Unlocked: all of them, up to [MAX_LAYERS]. Free: the first [FREE_LAYERS]
     * layers whose sound is free; every other layer stays in the mix, muted.
     */
    fun playableLayers(entitled: Boolean, layers: List<LayerSpec>): List<Boolean> {
        var granted = 0
        val limit = maxLayers(entitled)
        return layers.map { layer ->
            val ok = canUseEngine(entitled, layer.kind) && granted < limit
            if (ok) granted++
            ok
        }
    }

    /** Evolving night is part of the unlock. */
    fun evolvingAllowed(entitled: Boolean): Boolean = entitled
}
