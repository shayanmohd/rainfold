package com.mohdshayan.rainfold.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mohdshayan.rainfold.MainActivity
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.data.PlaybackStatus
import com.mohdshayan.rainfold.di.ServiceLocator
import com.mohdshayan.rainfold.ui.resume.ResumeActivity

/**
 * The Quick Settings tile, part of the unlock: it starts or pauses the last mix. Locked, its subtitle
 * says so and a tap opens the unlock screen with the reason "tile".
 */
class RainfoldTileService : TileService() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val unlocked = ServiceLocator.unlock.state.value.unlocked
        val playing = ServiceLocator.session.status.value == PlaybackStatus.PLAYING
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !unlocked -> getString(R.string.unlock_part_of)
                playing -> getString(R.string.widget_playing)
                else -> ServiceLocator.session.mix.value?.spec?.name ?: getString(R.string.widget_resume)
            }
        }
        tile.state = if (unlocked && playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val unlocked = ServiceLocator.unlock.state.value.unlocked
        val intent = if (unlocked) {
            Intent(this, ResumeActivity::class.java).setAction(ResumeActivity.ACTION_TOGGLE)
        } else {
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_UNLOCK_REASON, "tile")
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
