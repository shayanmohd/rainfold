package com.mohdshayan.rainfold.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// One DataStore per process. The delegate must stay top level so every AppPrefs shares it.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Opt-in, local-only counts shown in Settings. Never sent anywhere. */
data class UsageCounts(val optedIn: Boolean, val nights: Int, val mixesSaved: Int)

/**
 * Settings and small flags (blueprint section 9). The entitlement is not here: it lives only in the
 * template's own `entitlement` file, excluded from backup and transfer.
 */
class AppPrefs(private val context: Context) {

    private object Keys {
        val CURRENT_MIX_JSON = stringPreferencesKey("current_mix_json")
        val LAST_MIX_ID = longPreferencesKey("last_mix_id")
        val TIMER_MINUTES = intPreferencesKey("timer_minutes")
        val FADE_MINUTES = intPreferencesKey("fade_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UNLOCK_OFFER_SHOWN = booleanPreferencesKey("unlock_offer_shown")
        val QUALIFYING_DAYS = intPreferencesKey("qualifying_days")
        val LAST_QUALIFYING_DAY = longPreferencesKey("last_qualifying_day")
        val USAGE_COUNTS_OPT_IN = booleanPreferencesKey("usage_counts_opt_in")
        val COUNT_NIGHTS = intPreferencesKey("count_nights")
        val COUNT_MIXES_SAVED = intPreferencesKey("count_mixes_saved")
        val PLAYING_SINCE = longPreferencesKey("playing_since")
    }

    private val data: Flow<Preferences> =
        context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val currentMixJson: Flow<String?> = data.map { it[Keys.CURRENT_MIX_JSON] }
    val lastMixId: Flow<Long?> = data.map { it[Keys.LAST_MIX_ID] }
    val timerMinutes: Flow<Int> = data.map { it[Keys.TIMER_MINUTES] ?: 240 }
    val fadeMinutes: Flow<Int> = data.map { it[Keys.FADE_MINUTES] ?: 10 }
    val themeMode: Flow<ThemeMode> = data.map { p ->
        p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    val unlockOfferShown: Flow<Boolean> = data.map { it[Keys.UNLOCK_OFFER_SHOWN] ?: false }
    val usage: Flow<UsageCounts> = data.map {
        UsageCounts(it[Keys.USAGE_COUNTS_OPT_IN] ?: false, it[Keys.COUNT_NIGHTS] ?: 0, it[Keys.COUNT_MIXES_SAVED] ?: 0)
    }

    suspend fun setCurrentMix(json: String, savedId: Long?) {
        context.dataStore.edit {
            it[Keys.CURRENT_MIX_JSON] = json
            if (savedId != null) it[Keys.LAST_MIX_ID] = savedId else it.remove(Keys.LAST_MIX_ID)
        }
    }

    suspend fun setTimerDefaults(minutes: Int, fadeMinutes: Int) {
        context.dataStore.edit {
            it[Keys.TIMER_MINUTES] = minutes
            it[Keys.FADE_MINUTES] = fadeMinutes
        }
    }

    suspend fun setFadeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.FADE_MINUTES] = minutes }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** True the first time only: the one moment the unlock is offered on its own. */
    suspend fun claimUnlockOffer(): Boolean {
        var first = false
        context.dataStore.edit {
            if (it[Keys.UNLOCK_OFFER_SHOWN] != true) {
                first = true
                it[Keys.UNLOCK_OFFER_SHOWN] = true
            }
        }
        return first
    }

    /** A qualifying day: a 30-minute session or a completed fade. Counted once per calendar day. */
    suspend fun recordQualifyingDay(epochDay: Long) {
        context.dataStore.edit {
            if (it[Keys.LAST_QUALIFYING_DAY] != epochDay) {
                it[Keys.LAST_QUALIFYING_DAY] = epochDay
                it[Keys.QUALIFYING_DAYS] = (it[Keys.QUALIFYING_DAYS] ?: 0) + 1
                if (it[Keys.USAGE_COUNTS_OPT_IN] == true) it[Keys.COUNT_NIGHTS] = (it[Keys.COUNT_NIGHTS] ?: 0) + 1
            }
        }
    }

    suspend fun recordMixSaved() {
        context.dataStore.edit {
            if (it[Keys.USAGE_COUNTS_OPT_IN] == true) it[Keys.COUNT_MIXES_SAVED] = (it[Keys.COUNT_MIXES_SAVED] ?: 0) + 1
        }
    }

    suspend fun setUsageOptIn(on: Boolean) {
        context.dataStore.edit {
            it[Keys.USAGE_COUNTS_OPT_IN] = on
            if (!on) {
                it.remove(Keys.COUNT_NIGHTS)
                it.remove(Keys.COUNT_MIXES_SAVED)
            }
        }
    }

    /** Set while sound plays, cleared on every deliberate stop, so a later launch can tell the system stopped it. */
    suspend fun setPlayingSince(epochMs: Long?) {
        context.dataStore.edit { if (epochMs != null) it[Keys.PLAYING_SINCE] = epochMs else it.remove(Keys.PLAYING_SINCE) }
    }

    suspend fun playingSince(): Long? = data.first()[Keys.PLAYING_SINCE]
}
