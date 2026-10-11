package com.mohdshayan.rainfold.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.rainfold.data.prefs.ThemeMode
import com.mohdshayan.rainfold.data.prefs.UsageCounts
import com.mohdshayan.rainfold.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUi(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fadeMinutes: Int = 10,
    val usage: UsageCounts = UsageCounts(false, 0, 0),
    val restoring: Boolean = false,
)

enum class RestoreResult { RESTORED, NONE, NO_ANSWER }

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    private val restoring = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val _restore = MutableSharedFlow<RestoreResult>(extraBufferCapacity = 2)
    val restoreResults: SharedFlow<RestoreResult> = _restore.asSharedFlow()

    val ui: StateFlow<SettingsUi> = combine(prefs.themeMode, prefs.fadeMinutes, prefs.usage, restoring) { theme, fade, usage, r ->
        SettingsUi(theme, fade, usage, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setFade(minutes: Int) = viewModelScope.launch { prefs.setFadeMinutes(minutes) }
    fun setUsage(on: Boolean) = viewModelScope.launch { prefs.setUsageOptIn(on) }

    fun restore() = viewModelScope.launch {
        restoring.value = true
        val answered = ServiceLocator.unlock.syncNow()
        restoring.value = false
        _restore.tryEmit(
            when {
                !answered -> RestoreResult.NO_ANSWER
                ServiceLocator.unlock.state.value.unlocked -> RestoreResult.RESTORED
                else -> RestoreResult.NONE
            },
        )
    }
}
