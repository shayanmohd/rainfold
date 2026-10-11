package com.mohdshayan.rainfold.di

import android.content.Context
import com.mohdshayan.rainfold.audio.PreviewPlayer
import com.mohdshayan.rainfold.billing.UnlockRepository
import com.mohdshayan.rainfold.data.MixRepository
import com.mohdshayan.rainfold.data.SessionRepository
import com.mohdshayan.rainfold.data.db.AppDatabase
import com.mohdshayan.rainfold.data.prefs.AppPrefs
import com.mohdshayan.rainfold.data.prefs.EntitlementStore
import com.mohdshayan.rainfold.playback.PlayerLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. No Hilt: a single-module app does not need the method count or the
 * build time. Initialised in App.onCreate, and idempotent so the widget, the tile or the service may
 * call init() again with whatever context it has.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context =
        appContext ?: error("ServiceLocator.init() must be called before use")

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }

    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }

    val mixes: MixRepository by lazy { MixRepository(database) }

    val session: SessionRepository by lazy { SessionRepository(appPrefs, appScope) }

    val player: PlayerLink by lazy { PlayerLink(ctx()) }

    val preview: PreviewPlayer by lazy { PreviewPlayer(ctx()) }

    /** Outlives every screen, so a purchase result that lands mid-rotation is never lost. */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    /** The one BillingClient for the process, with this phone's copy of Google Play's answer. */
    val unlock: UnlockRepository by lazy {
        UnlockRepository(context = ctx(), store = EntitlementStore(ctx()), scope = appScope)
    }
}
