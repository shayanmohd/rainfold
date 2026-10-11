package com.mohdshayan.rainfold.ui.mixes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.rainfold.data.ImportResult
import com.mohdshayan.rainfold.data.MixFiles
import com.mohdshayan.rainfold.data.PlaybackStatus
import com.mohdshayan.rainfold.data.SavedMix
import com.mohdshayan.rainfold.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MixesUi(val loading: Boolean = true, val mixes: List<SavedMix> = emptyList(), val playingId: Long? = null)

sealed interface MixesMessage {
    data class Exported(val count: Int) : MixesMessage
    data class Imported(val count: Int, val skipped: Int) : MixesMessage
    data object NotAMixFile : MixesMessage
    data object NewerSchema : MixesMessage
    data object ReadFailed : MixesMessage
    data object WriteFailed : MixesMessage
    data object NothingToExport : MixesMessage
    data class Deleted(val mix: SavedMix, val position: Int) : MixesMessage
    data class Duplicated(val name: String) : MixesMessage
    data class Renamed(val name: String) : MixesMessage
}

class MixesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.mixes
    private val session = ServiceLocator.session

    private val _messages = MutableSharedFlow<MixesMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<MixesMessage> = _messages.asSharedFlow()

    val ui: StateFlow<MixesUi> = combine(repo.mixes, session.mix, session.status) { list, live, status ->
        MixesUi(loading = false, mixes = list, playingId = if (status == PlaybackStatus.PLAYING) live?.savedId else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MixesUi())

    fun exportFileName(): String = com.mohdshayan.rainfold.core.mix.MixCodec.fileName(LocalDate.now().toString())

    fun play(mix: SavedMix) {
        if (ui.value.playingId == mix.id) {
            ServiceLocator.player.pause()
            return
        }
        viewModelScope.launch {
            session.load()
            session.loadSaved(mix)
            repo.markPlayed(mix.id)
            ServiceLocator.player.play()
        }
    }

    fun rename(mix: SavedMix, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repo.rename(mix.id, clean)
            session.mix.value?.takeIf { it.savedId == mix.id }?.let { session.setMix(it.copy(spec = it.spec.copy(name = clean))) }
            _messages.tryEmit(MixesMessage.Renamed(clean))
        }
    }

    fun duplicate(mix: SavedMix) = viewModelScope.launch {
        repo.duplicate(mix.id)?.let { id -> repo.get(id)?.let { _messages.tryEmit(MixesMessage.Duplicated(it.spec.name)) } }
    }

    fun delete(mix: SavedMix) = viewModelScope.launch {
        val position = ui.value.mixes.indexOfFirst { it.id == mix.id }
        repo.delete(mix.id)
        session.mix.value?.takeIf { it.savedId == mix.id }?.let { session.setMix(it.copy(savedId = null)) }
        _messages.tryEmit(MixesMessage.Deleted(mix, position))
    }

    /** Puts a deleted mix back where it was. */
    fun undoDelete(mix: SavedMix, position: Int) = viewModelScope.launch {
        val id = repo.create(mix.spec)
        val ids = repo.all().map { it.id }.filter { it != id }.toMutableList()
        ids.add(position.coerceIn(0, ids.size), id)
        repo.reorder(ids)
    }

    fun move(mix: SavedMix, delta: Int) = viewModelScope.launch {
        val ids = ui.value.mixes.map { it.id }.toMutableList()
        val from = ids.indexOf(mix.id)
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return@launch
        ids.add(to, ids.removeAt(from))
        repo.reorder(ids)
    }

    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorder(ids) }

    fun export(uri: Uri) = viewModelScope.launch {
        val (json, count) = repo.exportJson()
        val ok = MixFiles.write(getApplication<Application>().contentResolver, uri, json)
        _messages.tryEmit(if (ok) MixesMessage.Exported(count) else MixesMessage.WriteFailed)
    }

    fun canExport(): Boolean = ui.value.mixes.isNotEmpty().also { if (!it) _messages.tryEmit(MixesMessage.NothingToExport) }

    fun import(uri: Uri) = viewModelScope.launch {
        val text = MixFiles.read(getApplication<Application>().contentResolver, uri)
        if (text == null) {
            _messages.tryEmit(MixesMessage.ReadFailed)
            return@launch
        }
        _messages.tryEmit(
            when (val result = repo.importJson(text)) {
                is ImportResult.Imported -> MixesMessage.Imported(result.count, result.skippedLayers)
                ImportResult.NotAMixFile -> MixesMessage.NotAMixFile
                ImportResult.NewerSchema -> MixesMessage.NewerSchema
            },
        )
    }
}
