package com.octoally.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.octoally.core.ui.AnsiColorPalette
import com.octoally.core.ui.DefaultAnsiPalette

// Theme system v2 (rc13) — six personality variants from the 2026-04-28 design
// handoff. Tokens mirror tokens.jsx in design_handoff_octoally; consumers using
// the legacy 11-token names (bgPrimary, accent, etc.) keep working through the
// computed aliases on OctoAllyColors.

sealed class OctoAllyTheme(val id: String, val displayName: String) {
    object Operator : OctoAllyTheme("operator", "Pro Operator")
    object Neon     : OctoAllyTheme("neon",     "Neon Vibecoder")
    object Crt      : OctoAllyTheme("crt",      "Retro CRT")
    object Studio   : OctoAllyTheme("studio",   "Tasteful Studio")
    object Zen      : OctoAllyTheme("zen",      "Swiss Minimal")
    object Critter  : OctoAllyTheme("critter",  "Playful Creature")

    companion object {
        val all: List<OctoAllyTheme> = listOf(
            Operator, Neon, Crt, Studio, Zen, Critter
        )

        fun fromId(id: String): OctoAllyTheme = when (id) {
            "operator" -> Operator
            "neon"     -> Neon
            "crt"      -> Crt
            "studio"   -> Studio
            "zen"      -> Zen
            "critter"  -> Critter
            // Legacy IDs from the pre-rc13 token set fall back to Operator (the
            // closest dark+blue/orange equivalent of the old Default).
            "default", "cyberpunk", "nord", "solarized", "dracula", "monokai", "sunset" -> Operator
            "light" -> Studio
            else    -> Operator
        }
    }
}

data class OctoAllyColors(
    // Canonical designer tokens (tokens.jsx PALETTES.<variant>).
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val surfaceLo: Color,
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val textMute: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent4: Color,
    val accent5: Color,
    val chipBg: Color,
    val danger: Color,
    val dark: Boolean,
    val ansiPalette: AnsiColorPalette = DefaultAnsiPalette,
) {
    // Legacy aliases — pre-rc13 11-token shape. Existing call sites
    // (`colors.bgPrimary`, `colors.accent`, etc.) keep compiling and read the
    // closest new token. Drop these once every call site uses the new names.
    val bgPrimary: Color     get() = bg
    val bgSecondary: Color   get() = surface
    val bgTertiary: Color    get() = surfaceHi
    val border: Color        get() = outline
    val textPrimary: Color   get() = text
    val textSecondary: Color get() = textDim
    val accent: Color        get() = primary
    val accentHover: Color   get() = secondary
    val success: Color       get() = tertiary
    val warning: Color       get() = accent4
    val error: Color         get() = danger
}

private val OperatorColors = OctoAllyColors(
    bg        = Color(0xFF0D0F12),
    surface   = Color(0xFF14171C),
    surfaceHi = Color(0xFF1C2026),
    surfaceLo = Color(0xFF090B0D),
    outline   = Color(0xFF272C33),
    text      = Color(0xFFE6E9EE),
    textDim   = Color(0xFF8B93A0),
    textMute  = Color(0xFF5A6270),
    primary   = Color(0xFFFF7A1A),
    onPrimary = Color(0xFF0D0F12),
    secondary = Color(0xFF4EA8FF),
    tertiary  = Color(0xFF22C38A),
    accent4   = Color(0xFFE6C84B),
    accent5   = Color(0xFFC56DFF),
    chipBg    = Color(0x1FFF7A1A),
    danger    = Color(0xFFFF4A5C),
    dark      = true,
)

private val NeonColors = OctoAllyColors(
    bg        = Color(0xFF0E0B14),
    surface   = Color(0xFF15111D),
    surfaceHi = Color(0xFF1E1930),
    surfaceLo = Color(0xFF0A0810),
    outline   = Color(0xFF332850),
    text      = Color(0xFFF4EDFF),
    textDim   = Color(0xFFA79BC4),
    textMute  = Color(0xFF6A5E88),
    primary   = Color(0xFFC6FF3D),
    onPrimary = Color(0xFF0B1400),
    secondary = Color(0xFFFF3DF0),
    tertiary  = Color(0xFF3DC9FF),
    accent4   = Color(0xFFFFB43D),
    accent5   = Color(0xFFB83DFF),
    chipBg    = Color(0x1FC6FF3D),
    danger    = Color(0xFFFF5470),
    dark      = true,
)

private val CrtColors = OctoAllyColors(
    bg        = Color(0xFF061006),
    surface   = Color(0xFF0A1A0A),
    surfaceHi = Color(0xFF102610),
    surfaceLo = Color(0xFF050C05),
    outline   = Color(0xFF1C4020),
    text      = Color(0xFFA6FFA6),
    textDim   = Color(0xFF5FCF5F),
    textMute  = Color(0xFF3A7A3A),
    primary   = Color(0xFF7DFF7D),
    onPrimary = Color(0xFF03100A),
    secondary = Color(0xFFFFB74D),
    tertiary  = Color(0xFF64D8FF),
    accent4   = Color(0xFFFF77B3),
    accent5   = Color(0xFFFFF176),
    chipBg    = Color(0x1A7DFF7D),
    danger    = Color(0xFFFF6B6B),
    dark      = true,
)

private val StudioColors = OctoAllyColors(
    bg        = Color(0xFFF5F2EC),
    surface   = Color(0xFFFDFCF9),
    surfaceHi = Color(0xFFFFFFFF),
    surfaceLo = Color(0xFFEBE6DC),
    outline   = Color(0xFFD8D2C4),
    text      = Color(0xFF1A1714),
    textDim   = Color(0xFF564F45),
    textMute  = Color(0xFF9A9082),
    primary   = Color(0xFFC65A3A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2D4A3E),
    tertiary  = Color(0xFF3B5A7A),
    accent4   = Color(0xFFC9A949),
    accent5   = Color(0xFF7A4A6D),
    chipBg    = Color(0xFFECE5D5),
    danger    = Color(0xFFA8442E),
    dark      = false,
)

private val ZenColors = OctoAllyColors(
    bg        = Color(0xFFEDEDEA),
    surface   = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFFFFFFF),
    surfaceLo = Color(0xFFE4E4E0),
    outline   = Color(0xFF0A0A0A),
    text      = Color(0xFF0A0A0A),
    textDim   = Color(0xFF4A4A4A),
    textMute  = Color(0xFF8A8A8A),
    primary   = Color(0xFF0A0A0A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF3B2F),
    tertiary  = Color(0xFF4A4A4A),
    accent4   = Color(0xFF0A0A0A),
    accent5   = Color(0xFF4A4A4A),
    chipBg    = Color(0xFFE4E4E0),
    danger    = Color(0xFFFF3B2F),
    dark      = false,
)

private val CritterColors = OctoAllyColors(
    bg        = Color(0xFFFFF1F6),
    surface   = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFFFF9FB),
    surfaceLo = Color(0xFFFFE1EA),
    outline   = Color(0xFFFFC7D7),
    text      = Color(0xFF3D1A2B),
    textDim   = Color(0xFF8A5A6F),
    textMute  = Color(0xFFC097A7),
    primary   = Color(0xFFFF4D8F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7C5CFF),
    tertiary  = Color(0xFF2DD4BF),
    accent4   = Color(0xFFFFC94D),
    accent5   = Color(0xFF4DC3FF),
    chipBg    = Color(0xFFFFDFE9),
    danger    = Color(0xFFFF3D6A),
    dark      = false,
)

fun colorsFor(theme: OctoAllyTheme): OctoAllyColors = when (theme) {
    is OctoAllyTheme.Operator -> OperatorColors
    is OctoAllyTheme.Neon     -> NeonColors
    is OctoAllyTheme.Crt      -> CrtColors
    is OctoAllyTheme.Studio   -> StudioColors
    is OctoAllyTheme.Zen      -> ZenColors
    is OctoAllyTheme.Critter  -> CritterColors
}
