package com.mohdshayan.rainfold.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The Media3 face of the noise engine: one endless item (the live mix) that can play, pause and
 * stop. The session, the notification, lock-screen controls and Bluetooth buttons all talk to this;
 * [onPlayChanged] starts and stops the real audio.
 */
@OptIn(UnstableApi::class)
class NoisePlayer(
    looper: Looper,
    private val onPlayChanged: (play: Boolean) -> Unit,
) : SimpleBasePlayer(looper) {

    private var playWhenReady = false
    private var title = "Rainfold"
    private var subtitle = ""

    fun setPlaying(play: Boolean) {
        if (play == playWhenReady) return
        playWhenReady = play
        invalidateState()
    }

    val isPlayingNow: Boolean get() = playWhenReady

    fun setTitle(name: String, detail: String) {
        if (name == title && detail == subtitle) return
        title = name
        subtitle = detail
        invalidateState()
    }

    override fun getState(): State {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setDisplayTitle(title)
            .setIsPlayable(true)
            .build()
        val item = MediaItemData.Builder(ITEM_ID)
            .setMediaItem(MediaItem.Builder().setMediaId(ITEM_ID).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setIsSeekable(false)
            .setIsDynamic(true)
            .setDurationUs(C.TIME_UNSET)
            .build()
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_TIMELINE,
                    )
                    .build(),
            )
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        onPlayChanged(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        playWhenReady = false
        onPlayChanged(false)
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val ITEM_ID = "rainfold-live-mix"
    }
}
