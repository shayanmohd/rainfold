package com.mohdshayan.rainfold.data

import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.mix.MixCodec
import com.mohdshayan.rainfold.core.mix.MixSpec
import com.mohdshayan.rainfold.core.mix.SleepPlan
import com.mohdshayan.rainfold.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The mix on the Listen screen. [savedId] is the saved mix it came from, if any. */
data class LiveMix(val spec: MixSpec, val savedId: Long? = null)

enum class PlaybackStatus { STOPPED, PLAYING, PAUSED_FOR_CALL }

/**
 * What the Listen screen, the playback service, the widget and the tile share, in one process: the
 * live mix (kept in `current_mix_json`), the running sleep timer and the playback status.
 */
class SessionRepository(private val prefs: AppPrefs, private val scope: CoroutineScope) {
    private val _mix = MutableStateFlow<LiveMix?>(null)
    val mix: StateFlow<LiveMix?> = _mix.asStateFlow()

    private val _plan = MutableStateFlow<SleepPlan?>(null)
    val plan: StateFlow<SleepPlan?> = _plan.asStateFlow()

    private val _status = MutableStateFlow(PlaybackStatus.STOPPED)
    val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    private val loadLock = Mutex()
    private var persistJob: Job? = null
    private var nextKey = 0

    /** Reads the live mix once per process; the first launch seeds one brown layer. */
    suspend fun load(): LiveMix = loadLock.withLock {
        _mix.value ?: run {
            val json = prefs.currentMixJson.first()
            val savedId = prefs.lastMixId.first()
            val decoded = json?.let { MixCodec.decode(it) as? MixCodec.Decoded.Mixes }?.mixes?.firstOrNull()
            val live = if (decoded != null) LiveMix(decoded, savedId) else LiveMix(SEED)
            nextKey = live.spec.layers.maxOfOrNull { it.key + 1 } ?: 0
            _mix.value = live
            live
        }
    }

    fun newKey(): Int = nextKey++

    /** Replaces the live mix now and writes it to disk shortly after, so a drag is one write. */
    fun setMix(live: LiveMix) {
        _mix.value = live
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(400)
            prefs.setCurrentMix(MixCodec.encode(listOf(live.spec)), live.savedId)
        }
    }

    /** Loads a saved mix into Listen, with fresh layer keys so the mixer starts its voices cleanly. */
    fun loadSaved(saved: SavedMix) {
        val layers = saved.spec.layers.map { it.copy(key = newKey()) }
        setMix(LiveMix(saved.spec.copy(layers = layers), saved.id))
    }

    fun setPlan(plan: SleepPlan?) {
        _plan.value = plan
    }

    fun setStatus(status: PlaybackStatus) {
        _status.value = status
    }

    companion object {
        val SEED = MixSpec("Brown noise", listOf(LayerSpec(EngineKind.BROWN, volume = 0.62f, tone = -0.2f, intensity = 0.5f, key = 0)))
    }
}
