package com.octoally.core.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*

// ── Motion specs (named constants — do not use magic numbers elsewhere) ────────

/** Standard UI transition duration in ms */
const val MotionDurationStandard = 300

/** Quick feedback duration in ms (button press, chip highlight) */
const val MotionDurationQuick = 150

/** Accent pulse duration — used only for the accentPulse single-frame flash */
const val MotionDurationPulse = 80

/** Easing for entering elements */
val MotionEasingEnter: Easing = FastOutSlowInEasing

/** Easing for exiting elements */
val MotionEasingExit: Easing = LinearOutSlowInEasing

/** Easing for emphasis (bounce-like) */
val MotionEasingEmphasis: Easing = FastOutLinearInEasing

/** Standard tween spec for most UI transitions */
fun <T> motionTween(durationMs: Int = MotionDurationStandard): TweenSpec<T> =
    tween(durationMillis = durationMs, easing = MotionEasingEnter)

/** Fade + slide in from bottom — used for bottom sheets and input lifts */
val SlideInFromBottom: EnterTransition = slideInVertically(
    animationSpec = tween(MotionDurationStandard, easing = MotionEasingEnter)
) { it / 3 } + fadeIn(animationSpec = tween(MotionDurationStandard))

/** Matching exit for SlideInFromBottom */
val SlideOutToBottom: ExitTransition = slideOutVertically(
    animationSpec = tween(MotionDurationStandard, easing = MotionEasingExit)
) { it / 3 } + fadeOut(animationSpec = tween(MotionDurationStandard))

/** Cross-fade for content swaps (prompt type changes) */
val ContentCrossFade: ContentTransform = fadeIn(
    animationSpec = tween(MotionDurationStandard)
) togetherWith fadeOut(animationSpec = tween(MotionDurationStandard))
