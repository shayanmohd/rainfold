package com.mohdshayan.rainfold.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * What any screen needs to know about the one-time unlock. AppNav provides it once, so screens never
 * take a navController or a billing object: they read [unlocked] and call [open] with a reason.
 */
@Immutable
data class UnlockAccess(val unlocked: Boolean, val open: (reason: String) -> Unit)

val LocalUnlock = compositionLocalOf { UnlockAccess(unlocked = false, open = {}) }

/**
 * A click handler for a locked action: runs [action] when unlocked, otherwise opens the unlock screen
 * with [reason] (a short key such as "export", shown nowhere, used to pick the screen's first line).
 */
@Composable
fun lockedAction(reason: String, action: () -> Unit): () -> Unit {
    val access = LocalUnlock.current
    return { if (access.unlocked) action() else access.open(reason) }
}

/**
 * A locked area: [content] when unlocked, otherwise [locked], which should show what the area does
 * (a real preview or one sentence) and a button that calls the open lambda it is given.
 */
@Composable
fun UnlockGate(
    reason: String,
    locked: @Composable (open: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val access = LocalUnlock.current
    if (access.unlocked) content() else locked { access.open(reason) }
}
