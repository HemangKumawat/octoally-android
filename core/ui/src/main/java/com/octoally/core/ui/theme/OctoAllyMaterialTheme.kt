package com.octoally.core.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val THEME_TRANSITION_MS = 200

val LocalOctoAllyColors = staticCompositionLocalOf<OctoAllyColors> {
    error("OctoAllyColors not provided — wrap your UI in OctoAllyMaterialTheme { ... }")
}

@Composable
fun OctoAllyMaterialTheme(
    themeController: ThemeController,
    content: @Composable () -> Unit
) {
    @Suppress("USELESS_CAST")
    val raw = themeController.current.collectAsStateWithLifecycle().value as Any?
    val safeTheme = (raw as? OctoAllyTheme) ?: OctoAllyTheme.Operator
    OctoAllyMaterialTheme(theme = safeTheme, content = content)
}

@Composable
fun OctoAllyMaterialTheme(
    theme: OctoAllyTheme,
    content: @Composable () -> Unit
) {
    val target = colorsFor(theme)

    val bg        by target.bg.animated()
    val surface   by target.surface.animated()
    val surfaceHi by target.surfaceHi.animated()
    val surfaceLo by target.surfaceLo.animated()
    val outline   by target.outline.animated()
    val text      by target.text.animated()
    val textDim   by target.textDim.animated()
    val textMute  by target.textMute.animated()
    val primary   by target.primary.animated()
    val onPrimary by target.onPrimary.animated()
    val secondary by target.secondary.animated()
    val tertiary  by target.tertiary.animated()
    val accent4   by target.accent4.animated()
    val accent5   by target.accent5.animated()
    val chipBg    by target.chipBg.animated()
    val danger    by target.danger.animated()

    val animated = OctoAllyColors(
        bg        = bg,
        surface   = surface,
        surfaceHi = surfaceHi,
        surfaceLo = surfaceLo,
        outline   = outline,
        text      = text,
        textDim   = textDim,
        textMute  = textMute,
        primary   = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        tertiary  = tertiary,
        accent4   = accent4,
        accent5   = accent5,
        chipBg    = chipBg,
        danger    = danger,
        dark      = target.dark,
        ansiPalette = target.ansiPalette,
    )

    val scheme = if (target.dark) {
        darkColorScheme(
            primary          = primary,
            onPrimary        = onPrimary,
            secondary        = secondary,
            onSecondary      = onPrimary,
            tertiary         = tertiary,
            onTertiary       = onPrimary,
            background       = bg,
            onBackground     = text,
            surface          = surface,
            onSurface        = text,
            surfaceVariant   = surfaceHi,
            onSurfaceVariant = textDim,
            outline          = outline,
            outlineVariant   = surfaceLo,
            error            = danger,
            onError          = onPrimary,
        )
    } else {
        lightColorScheme(
            primary          = primary,
            onPrimary        = onPrimary,
            secondary        = secondary,
            onSecondary      = onPrimary,
            tertiary         = tertiary,
            onTertiary       = onPrimary,
            background       = bg,
            onBackground     = text,
            surface          = surface,
            onSurface        = text,
            surfaceVariant   = surfaceHi,
            onSurfaceVariant = textDim,
            outline          = outline,
            outlineVariant   = surfaceLo,
            error            = danger,
            onError          = onPrimary,
        )
    }

    CompositionLocalProvider(LocalOctoAllyColors provides animated) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
private fun Color.animated(): State<Color> =
    animateColorAsState(
        targetValue = this,
        animationSpec = tween(durationMillis = THEME_TRANSITION_MS),
        label = "octoally-theme-color"
    )
