package com.mohdshayan.rainfold

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohdshayan.rainfold.data.prefs.ThemeMode
import com.mohdshayan.rainfold.di.ServiceLocator
import com.mohdshayan.rainfold.ui.nav.AppNav
import com.mohdshayan.rainfold.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    /** A reason handed over by the tile, opened once as the unlock screen. */
    private val pendingUnlock = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingUnlock.value = intent.getStringExtra(EXTRA_UNLOCK_REASON)
        // One small read so a chosen theme paints the first Compose frame.
        val firstTheme = runBlocking { ServiceLocator.appPrefs.themeMode.first() }
        setContent {
            val mode by ServiceLocator.appPrefs.themeMode.collectAsStateWithLifecycle(firstTheme)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(darkTheme = dark) {
                AppNav(pendingUnlock = pendingUnlock)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_UNLOCK_REASON)?.let { pendingUnlock.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Google's advice: query purchases on every resume, so a pending payment that completed, a promo
        // code redeemed in the Play Store, a refund or a purchase on another phone shows up without a restart.
        ServiceLocator.unlock.refresh()
    }

    companion object {
        const val EXTRA_UNLOCK_REASON = "com.mohdshayan.rainfold.extra.UNLOCK_REASON"
    }
}
