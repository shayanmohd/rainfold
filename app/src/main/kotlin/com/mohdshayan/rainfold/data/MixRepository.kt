package com.mohdshayan.rainfold.data

import androidx.room.withTransaction
import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.mix.MixCodec
import com.mohdshayan.rainfold.core.mix.MixSpec
import com.mohdshayan.rainfold.data.db.AppDatabase
import com.mohdshayan.rainfold.data.db.Mix
import com.mohdshayan.rainfold.data.db.MixLayer
import com.mohdshayan.rainfold.data.db.MixWithLayers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A saved mix as screens see it. */
data class SavedMix(val id: Long, val spec: MixSpec, val lastPlayedAt: Long?)

sealed interface ImportResult {
    data class Imported(val count: Int, val skippedLayers: Int) : ImportResult
    data object NotAMixFile : ImportResult
    data object NewerSchema : ImportResult
}

/** Saved mixes in Room, and their export and import through [MixCodec]. */
class MixRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.mixDao()

    val mixes: Flow<List<SavedMix>> = dao.observeAll().map { list -> list.map { it.toSaved() } }

    suspend fun count(): Int = dao.count()

    suspend fun all(): List<SavedMix> = dao.all().map { it.toSaved() }

    suspend fun get(id: Long): SavedMix? = dao.byId(id)?.toSaved()

    /** Saves [spec] as a new mix at the end of the list and returns its id. */
    suspend fun create(spec: MixSpec): Long = db.withTransaction {
        val now = clock()
        val id = dao.insertMix(
            Mix(name = spec.name, sortOrder = dao.maxSortOrder() + 1, evolving = spec.evolving, driftRange = spec.driftRange, createdAt = now, updatedAt = now),
        )
        dao.insertLayers(layersOf(id, spec.layers))
        id
    }

    /** Overwrites mix [id] with [spec], keeping its place in the list. */
    suspend fun replace(id: Long, spec: MixSpec) = db.withTransaction {
        dao.updateMix(id, spec.name, spec.evolving, spec.driftRange, clock())
        dao.deleteLayers(id)
        dao.insertLayers(layersOf(id, spec.layers))
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name, clock())

    suspend fun duplicate(id: Long): Long? {
        val source = get(id) ?: return null
        val names = all().map { it.spec.name }
        return create(source.spec.copy(name = MixCodec.uniqueName(source.spec.name, names)))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Writes the list order exactly as [ids] gives it. */
    suspend fun reorder(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { index, id -> dao.setOrder(id, index) }
    }

    suspend fun markPlayed(id: Long) = dao.markPlayed(id, clock())

    suspend fun exportJson(): Pair<String, Int> {
        val list = all()
        return MixCodec.encode(list.map { it.spec }) to list.size
    }

    /** Merges every mix in [text] in one transaction. Clashing names become "Desk (2)". Never capped. */
    suspend fun importJson(text: String): ImportResult = when (val decoded = MixCodec.decode(text)) {
        MixCodec.Decoded.NotAMixFile -> ImportResult.NotAMixFile
        MixCodec.Decoded.NewerSchema -> ImportResult.NewerSchema
        is MixCodec.Decoded.Mixes -> db.withTransaction {
            val names = dao.all().map { it.mix.name }.toMutableList()
            for (spec in decoded.mixes) {
                val name = MixCodec.uniqueName(spec.name, names)
                names += name
                create(spec.copy(name = name))
            }
            ImportResult.Imported(decoded.mixes.size, decoded.skippedLayers)
        }
    }

    private fun layersOf(mixId: Long, layers: List<LayerSpec>) = layers.mapIndexed { i, l ->
        MixLayer(mixId = mixId, position = i, engine = l.kind.id, volume = l.volume, tone = l.tone, intensity = l.intensity)
    }

    private fun MixWithLayers.toSaved(): SavedMix {
        val layerSpecs = layers.sortedBy { it.position }.mapNotNull { l ->
            EngineKind.fromId(l.engine)?.let { LayerSpec(it, l.volume, l.tone, l.intensity, key = l.position) }
        }
        return SavedMix(mix.id, MixSpec(mix.name, layerSpecs, mix.evolving, mix.driftRange), mix.lastPlayedAt)
    }
}
