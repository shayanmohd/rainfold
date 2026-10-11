package com.mohdshayan.rainfold.core.mix

import kotlin.math.PI
import kotlin.math.cos

/**
 * The sleep timer's fade: a quarter cosine from full level to silence, which sounds even to the ear
 * and reaches exactly zero at the end.
 */
object FadeCurve {
    fun gain(elapsedMs: Long, fadeMs: Long): Float {
        if (elapsedMs <= 0L) return 1f
        if (fadeMs <= 0L || elapsedMs >= fadeMs) return 0f
        val p = elapsedMs.toDouble() / fadeMs
        return cos(p * PI / 2).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * One running sleep timer. The sound plays at full level until [fadeStartsAt], fades over the last
 * [fadeMs], and stops at [endsAt].
 */
data class SleepPlan(val startedAt: Long, val durationMs: Long, val fadeMs: Long) {
    val endsAt: Long get() = startedAt + durationMs
    val fadeStartsAt: Long get() = endsAt - fadeMs.coerceAtMost(durationMs)

    fun gainAt(now: Long): Float = FadeCurve.gain(now - fadeStartsAt, endsAt - fadeStartsAt)

    fun finishedAt(now: Long): Boolean = now >= endsAt

    fun remainingMs(now: Long): Long = (endsAt - now).coerceAtLeast(0L)

    companion object {
        const val MIN_MINUTES = 15
        const val MAX_MINUTES = 600
        const val MIN_FADE = 1
        const val MAX_FADE = 30

        fun of(startedAt: Long, minutes: Int, fadeMinutes: Int): SleepPlan {
            val d = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) * 60_000L
            val f = fadeMinutes.coerceIn(MIN_FADE, MAX_FADE) * 60_000L
            return SleepPlan(startedAt, d, f.coerceAtMost(d))
        }
    }
}
