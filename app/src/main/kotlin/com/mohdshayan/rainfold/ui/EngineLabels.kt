package com.mohdshayan.rainfold.ui

import android.content.Context
import androidx.annotation.StringRes
import com.mohdshayan.rainfold.data.LiveMix
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.core.mix.EngineKind

/** The words for each sound. Kept beside the UI so the pure engine has no Android resources. */
object EngineLabels {
    @StringRes
    fun name(kind: EngineKind): Int = when (kind) {
        EngineKind.WHITE -> R.string.engine_white
        EngineKind.PINK -> R.string.engine_pink
        EngineKind.BROWN -> R.string.engine_brown
        EngineKind.GREEN -> R.string.engine_green
        EngineKind.FAN -> R.string.engine_fan
        EngineKind.RAIN -> R.string.engine_rain
        EngineKind.STORM -> R.string.engine_storm
        EngineKind.WIND -> R.string.engine_wind
        EngineKind.SURF -> R.string.engine_surf
        EngineKind.STREAM -> R.string.engine_stream
        EngineKind.FIRE -> R.string.engine_fire
        EngineKind.AIR_CON -> R.string.engine_air_con
        EngineKind.TRAIN -> R.string.engine_train
        EngineKind.CRICKETS -> R.string.engine_crickets
        EngineKind.CAFE -> R.string.engine_cafe
    }

    @StringRes
    fun detail(kind: EngineKind): Int = when (kind) {
        EngineKind.WHITE -> R.string.engine_white_detail
        EngineKind.PINK -> R.string.engine_pink_detail
        EngineKind.BROWN -> R.string.engine_brown_detail
        EngineKind.GREEN -> R.string.engine_green_detail
        EngineKind.FAN -> R.string.engine_fan_detail
        EngineKind.RAIN -> R.string.engine_rain_detail
        EngineKind.STORM -> R.string.engine_storm_detail
        EngineKind.WIND -> R.string.engine_wind_detail
        EngineKind.SURF -> R.string.engine_surf_detail
        EngineKind.STREAM -> R.string.engine_stream_detail
        EngineKind.FIRE -> R.string.engine_fire_detail
        EngineKind.AIR_CON -> R.string.engine_air_con_detail
        EngineKind.TRAIN -> R.string.engine_train_detail
        EngineKind.CRICKETS -> R.string.engine_crickets_detail
        EngineKind.CAFE -> R.string.engine_cafe_detail
    }

    /** A saved mix shows its name; an unsaved one is named by its sounds, so the title never misleads. */
    fun title(context: Context, live: LiveMix): String =
        if (live.savedId != null) live.spec.name
        else if (live.spec.layers.isEmpty()) context.getString(R.string.nav_listen)
        else live.spec.layers.joinToString(", ") { context.getString(name(it.kind)) }
}
