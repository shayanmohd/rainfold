package com.mohdshayan.rainfold.ui.listen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.FreeTier
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.mix.MixCodec
import com.mohdshayan.rainfold.core.mix.SleepPlan
import com.mohdshayan.rainfold.data.LiveMix
import com.mohdshayan.rainfold.data.PlaybackStatus
import com.mohdshayan.rainfold.data.SavedMix
import com.mohdshayan.rainfold.di.ServiceLocator
import com.mohdshayan.rainfold.playback.PlaybackService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListenUi(
    val loading: Boolean = true,
    val live: LiveMix? = null,
    val status: PlaybackStatus = PlaybackStatus.STOPPED,
    val plan: SleepPlan? = null,
    val stoppedBySystem: Boolean = false,
    val saved: List<SavedMix> = emptyList(),
    val timerMinutes: Int = 240,
    val fadeMinutes: Int = 10,
    val previewing: EngineKind? = null,
)

/** One-off lines for the snackbar, in the words of the button that caused them. */
sealed interface ListenMessage {
    data class Added(val kind: EngineKind) : ListenMessage
    data class Saved(val name: String, val offerUnlock: Boolean) : ListenMessage
    data class Replaced(val old: String, val name: String) : ListenMessage
    data class TimerSet(val fadesAt: Long) : ListenMessage
    data object TimerOff : ListenMessage
    data object InCall : ListenMessage
}

enum class SaveOutcome { SAVED, AT_CAP }

class ListenViewModel(app: Application) : AndroidViewModel(app) {
    private val session = ServiceLocator.session
    private val prefs = ServiceLocator.appPrefs
    private val mixes = ServiceLocator.mixes
    private val preview = ServiceLocator.preview
    private val stopped = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<ListenMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<ListenMessage> = _messages.asSharedFlow()

    val ui: StateFlow<ListenUi> = combine(
        combine(session.mix, session.status, session.plan) { live, status, plan -> Triple(live, status, plan) },
        stopped,
        mixes.mixes,
        combine(prefs.timerMinutes, prefs.fadeMinutes) { t, f -> t to f },
        preview.playing,
    ) { (live, status, plan), stoppedBySystem, saved, (timer, fade), previewing ->
        ListenUi(
            loading = live == null,
            live = live,
            status = status,
            plan = plan,
            stoppedBySystem = stoppedBySystem && status != PlaybackStatus.PLAYING,
            saved = saved,
            timerMinutes = timer,
            fadeMinutes = fade,
            previewing = previewing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListenUi())

    init {
        viewModelScope.launch {
            session.load()
            // Sound was on and nothing in this process stopped it: the system did.
            if (prefs.playingSince() != null && !PlaybackService.running) {
                stopped.value = true
                prefs.setPlayingSince(null)
            }
        }
    }

    private val entitled get() = ServiceLocator.unlock.state.value.unlocked

    private fun edit(transform: (LiveMix) -> LiveMix) {
        val live = session.mix.value ?: return
        session.setMix(transform(live))
    }

    private fun editLayer(key: Int, transform: (LayerSpec) -> LayerSpec) = edit { live ->
        live.copy(spec = live.spec.copy(layers = live.spec.layers.map { if (it.key == key) transform(it).clamped() else it }))
    }

    fun togglePlay() {
        if (session.status.value == PlaybackStatus.PLAYING) {
            ServiceLocator.player.pause()
        } else {
            stopped.value = false
            preview.stop()
            ServiceLocator.player.play()
        }
    }

    fun dismissStopped() {
        stopped.value = false
    }

    fun setVolume(key: Int, value: Float) = editLayer(key) { it.copy(volume = value) }
    fun setTone(key: Int, value: Float) = editLayer(key) { it.copy(tone = value) }
    fun setIntensity(key: Int, value: Float) = editLayer(key) { it.copy(intensity = value) }

    fun remove(key: Int) = edit { live -> live.copy(spec = live.spec.copy(layers = live.spec.layers.filterNot { it.key == key })) }

    /** Adds [kind] if the rules allow it. The picker opens the unlock screen itself otherwise. */
    fun add(kind: EngineKind) {
        val live = session.mix.value ?: return
        if (!FreeTier.canAddLayer(entitled, live.spec.layers.size) || !FreeTier.canUseEngine(entitled, kind)) return
        preview.stop()
        val layer = LayerSpec(kind, volume = 0.5f, tone = 0f, intensity = 0.5f, key = session.newKey())
        session.setMix(live.copy(spec = live.spec.copy(layers = live.spec.layers + layer)))
        _messages.tryEmit(ListenMessage.Added(kind))
    }

    fun togglePreview(kind: EngineKind) {
        if (preview.playing.value == kind) {
            preview.stop()
        } else if (!preview.play(kind)) {
            if (preview.inCall()) _messages.tryEmit(ListenMessage.InCall)
        }
    }

    fun stopPreview() = preview.stop()

    fun setEvolving(on: Boolean, range: Float) = edit { it.copy(spec = it.spec.copy(evolving = on, driftRange = range)) }

    fun startTimer(minutes: Int, fadeMinutes: Int) {
        val plan = SleepPlan.of(System.currentTimeMillis(), minutes, fadeMinutes)
        session.setPlan(plan)
        viewModelScope.launch { prefs.setTimerDefaults(minutes, fadeMinutes) }
        _messages.tryEmit(ListenMessage.TimerSet(plan.fadeStartsAt))
        if (session.status.value != PlaybackStatus.PLAYING) togglePlay()
    }

    fun cancelTimer() {
        session.setPlan(null)
        _messages.tryEmit(ListenMessage.TimerOff)
    }

    /**
     * Saves the live mix. A mix loaded from Mixes and saved under the same name is updated in place;
     * anything else is a new mix, which the free version allows three of.
     */
    suspend fun save(name: String, asNew: Boolean): SaveOutcome {
        val live = session.mix.value ?: return SaveOutcome.SAVED
        val clean = name.trim().take(MixCodec.MAX_NAME)
        val existing = live.savedId?.let { mixes.get(it) }
        if (existing != null && !asNew) {
            val spec = live.spec.copy(name = clean)
            mixes.replace(existing.id, spec)
            session.setMix(live.copy(spec = spec))
            _messages.tryEmit(ListenMessage.Saved(clean, offerUnlock = false))
            return SaveOutcome.SAVED
        }
        if (!FreeTier.canSaveMix(entitled, mixes.count())) return SaveOutcome.AT_CAP
        val taken = mixes.all().map { it.spec.name }
        val unique = MixCodec.uniqueName(clean, taken)
        val spec = live.spec.copy(name = unique)
        val id = mixes.create(spec)
        session.setMix(LiveMix(spec, id))
        prefs.recordMixSaved()
        val offer = !entitled && prefs.claimUnlockOffer()
        _messages.tryEmit(ListenMessage.Saved(unique, offer))
        return SaveOutcome.SAVED
    }

    /** At the free cap: overwrite [target] with the live mix under [name]. */
    suspend fun replace(target: SavedMix, name: String) {
        val live = session.mix.value ?: return
        val clean = name.trim().take(MixCodec.MAX_NAME)
        val others = mixes.all().filter { it.id != target.id }.map { it.spec.name }
        val spec = live.spec.copy(name = MixCodec.uniqueName(clean, others))
        mixes.replace(target.id, spec)
        session.setMix(LiveMix(spec, target.id))
        prefs.recordMixSaved()
        _messages.tryEmit(ListenMessage.Replaced(target.spec.name, spec.name))
    }

    suspend fun currentFadeDefault(): Int = prefs.fadeMinutes.first()

    override fun onCleared() {
        preview.stop()
    }
}
