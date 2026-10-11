package com.mohdshayan.rainfold.ui.resume

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.mohdshayan.rainfold.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A translucent trampoline for the widget and the tile: it loads the last mix, sends play (or pause)
 * through the MediaController while the app is in the foreground, which Android 12 and later require
 * for starting a media service, and closes.
 */
class ResumeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        lifecycleScope.launch {
            ServiceLocator.session.load()
            ServiceLocator.player.toggle {
                lifecycleScope.launch {
                    // Hold the foreground for a moment so the service can promote itself.
                    delay(700)
                    finish()
                }
            }
            delay(4_000)
            if (!isFinishing) finish()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.mohdshayan.rainfold.action.TOGGLE"
    }
}
