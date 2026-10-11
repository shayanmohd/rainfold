package com.mohdshayan.rainfold.core.mix

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The export file: `{"format":"rainfold-mixes","schema":1,"mixes":[...]}`. It carries mixes and
 * nothing else (never the unlock), and reading ignores keys it does not know.
 */
object MixCodec {
    const val FORMAT = "rainfold-mixes"
    const val SCHEMA = 1

    @Serializable
    data class LayerDto(val engine: String, val volume: Float = 0.6f, val tone: Float = 0f, val intensity: Float = 0.5f)

    @Serializable
    data class MixDto(
        val name: String,
        val evolving: Boolean = false,
        val driftRange: Float = 0.3f,
        val layers: List<LayerDto> = emptyList(),
    )

    @Serializable
    data class FileDto(val format: String, val schema: Int, val mixes: List<MixDto>)

    sealed interface Decoded {
        /** [skippedLayers] counts layers whose sound this version does not know. */
        data class Mixes(val mixes: List<MixSpec>, val skippedLayers: Int) : Decoded
        data object NotAMixFile : Decoded
        data object NewerSchema : Decoded
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(mixes: List<MixSpec>): String = json.encodeToString(
        FileDto.serializer(),
        FileDto(
            format = FORMAT,
            schema = SCHEMA,
            mixes = mixes.map { mix ->
                MixDto(
                    name = mix.name,
                    evolving = mix.evolving,
                    driftRange = mix.driftRange,
                    layers = mix.layers.map { LayerDto(it.kind.id, it.volume, it.tone, it.intensity) },
                )
            },
        ),
    )

    fun decode(text: String): Decoded {
        // The JSON tree reader recurses per bracket, so a file of deeply nested brackets would overflow the
        // stack. A real mix file nests four levels; anything past MAX_DEPTH is not one.
        if (nestingDepth(text) > MAX_DEPTH) return Decoded.NotAMixFile
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return Decoded.NotAMixFile
        // A field of the wrong shape (an object where a string belongs) is simply not a mix file.
        if ((root["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) return Decoded.NotAMixFile
        val schema = (root["schema"] as? JsonPrimitive)?.intOrNull ?: return Decoded.NotAMixFile
        if (schema > SCHEMA) return Decoded.NewerSchema
        val file = try {
            json.decodeFromJsonElement(FileDto.serializer(), root)
        } catch (e: SerializationException) {
            return Decoded.NotAMixFile
        } catch (e: IllegalArgumentException) {
            return Decoded.NotAMixFile
        }
        var skipped = 0
        val mixes = file.mixes.map { dto ->
            var key = 0
            val layers = dto.layers.mapNotNull { l ->
                val kind = EngineKind.fromId(l.engine)
                if (kind == null) {
                    skipped++
                    null
                } else {
                    LayerSpec(kind, l.volume, l.tone, l.intensity, key++).clamped()
                }
            }.take(FreeTier.MAX_LAYERS)
            MixSpec(
                name = dto.name.trim().take(MAX_NAME).ifEmpty { "Mix" },
                layers = layers,
                evolving = dto.evolving,
                driftRange = dto.driftRange.coerceIn(0f, 0.5f),
            )
        }
        return Decoded.Mixes(mixes, skipped)
    }

    /** "Desk" stays "Desk" unless taken; then "Desk (2)", "Desk (3)" and so on. */
    fun uniqueName(name: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        var n = 2
        while ("$name ($n)".lowercase() in lower) n++
        return "$name ($n)"
    }

    /** File name for an export made on [isoDate] (YYYY-MM-DD). */
    fun fileName(isoDate: String): String = "rainfold-mixes-$isoDate.json"

    const val MAX_NAME = 40
    private const val MAX_DEPTH = 32

    /** The deepest bracket nesting in [text], ignoring brackets inside strings. Stops counting past the limit. */
    private fun nestingDepth(text: String): Int {
        var depth = 0
        var deepest = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (depth > deepest) deepest = depth
                    if (deepest > MAX_DEPTH) return deepest
                }
                '}', ']' -> depth--
            }
        }
        return deepest
    }
}
