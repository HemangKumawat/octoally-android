package com.octoally.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * rc16 — single source of truth for tactile feedback across the app.
 *
 * Usage:
 *   val tap = rememberTapHaptic()
 *   IconButton(onClick = { tap(); doStuff() }) { ... }
 *
 * The lambda is cheap (fires the system HapticFeedback service) so callers
 * can inline it in onClick lambdas without churn. Centralised so the
 * intensity / type can be swapped in one place if the user says "too much"
 * or "more" — Material3 only exposes two HapticFeedbackType variants
 * (LongPress = ~30ms tick, TextHandleMove = ~10ms shorter tick), we map
 * "tap" → LongPress (felt as a confident click) and "edge" → TextHandleMove
 * (felt as a soft tick at scroll boundaries / theme switch).
 */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

@Composable
fun rememberEdgeHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** Direct one-shot helper for non-composable contexts (e.g. callbacks already
 *  inside a composable but in a non-@Composable lambda). */
fun HapticFeedback.tap() = performHapticFeedback(HapticFeedbackType.LongPress)
fun HapticFeedback.edge() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
