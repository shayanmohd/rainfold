package com.mohdshayan.rainfold.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * The app's one MediaController, connected on first use. Screens, the widget trampoline and the
 * tile all start and stop sound through it, so playback always goes through the session.
 */
class PlayerLink(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null

    private fun withController(block: (MediaController) -> Unit) {
        // A controller whose service has gone away stays disconnected for good: build a new one.
        future?.let { old -> if (old.isDone && runCatching { old.get().isConnected }.getOrDefault(false).not()) future = null }
        val f = future ?: MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync().also { future = it }
        f.addListener(
            {
                val controller = runCatching { f.get() }.getOrNull()
                if (controller == null || !controller.isConnected) {
                    future = null
                } else {
                    block(controller)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(then: () -> Unit = {}) = withController {
        it.prepare()
        it.play()
        then()
    }

    fun pause() = withController { it.pause() }

    fun toggle(then: () -> Unit = {}) = withController {
        if (it.playWhenReady) it.pause() else {
            it.prepare()
            it.play()
        }
        then()
    }
}
