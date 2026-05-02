package com.octoally.core.ui

import androidx.compose.ui.graphics.Color

/**
 * 16-color ANSI terminal palette. Indices 0–7 are standard, 8–15 are bright.
 * Each [OctoAllyTheme] provides its own palette so terminal colors respect
 * the active theme (matching the web dashboard's per-theme xterm palettes).
 */
data class AnsiColorPalette(
    val black: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val blue: Color,
    val magenta: Color,
    val cyan: Color,
    val white: Color,
    val brightBlack: Color,
    val brightRed: Color,
    val brightGreen: Color,
    val brightYellow: Color,
    val brightBlue: Color,
    val brightMagenta: Color,
    val brightCyan: Color,
    val brightWhite: Color,
) {
    /** Look up standard color by ANSI index (0–15). */
    operator fun get(index: Int): Color = when (index) {
        0  -> black
        1  -> red
        2  -> green
        3  -> yellow
        4  -> blue
        5  -> magenta
        6  -> cyan
        7  -> white
        8  -> brightBlack
        9  -> brightRed
        10 -> brightGreen
        11 -> brightYellow
        12 -> brightBlue
        13 -> brightMagenta
        14 -> brightCyan
        15 -> brightWhite
        else -> white
    }

    companion object {
        /**
         * Resolve a 256-color index to a [Color].
         * 0–15: standard palette (use [AnsiColorPalette.get]).
         * 16–231: 6×6×6 color cube.
         * 232–255: 24-step grayscale ramp.
         */
        fun color256(index: Int, palette: AnsiColorPalette): Color = when {
            index < 16 -> palette[index]
            index < 232 -> {
                val i = index - 16
                val r = (i / 36) * 51
                val g = ((i % 36) / 6) * 51
                val b = (i % 6) * 51
                Color(r, g, b)
            }
            else -> {
                val gray = 8 + (index - 232) * 10
                Color(gray, gray, gray)
            }
        }
    }
}

/** Default dark palette — matches web Terminal.tsx xterm theme (Default Dark). */
val DefaultAnsiPalette = AnsiColorPalette(
    black        = Color(0xFF1A1D27),
    red          = Color(0xFFEF4444),
    green        = Color(0xFF22C55E),
    yellow       = Color(0xFFEAB308),
    blue         = Color(0xFF3B82F6),
    magenta      = Color(0xFFA855F7),
    cyan         = Color(0xFF06B6D4),
    white        = Color(0xFFE4E8F1),
    brightBlack  = Color(0xFF4B5563),
    brightRed    = Color(0xFFF87171),
    brightGreen  = Color(0xFF4ADE80),
    brightYellow = Color(0xFFFDE047),
    brightBlue   = Color(0xFF60A5FA),
    brightMagenta= Color(0xFFC084FC),
    brightCyan   = Color(0xFF22D3EE),
    brightWhite  = Color(0xFFF9FAFB),
)
