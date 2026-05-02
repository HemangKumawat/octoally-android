package com.octoally.core.ui

import androidx.compose.ui.graphics.Color

// ── Legacy single-theme tokens (pre-multi-theme) ──────────────────────────────
//
// These constants are retained only for backward compatibility with feature
// modules that have not yet migrated to `LocalOctoAllyColors.current`. New
// code MUST read tokens from the composition-local provided by
// `OctoAllyMaterialTheme`. Once all call sites are migrated these `val`s can
// be deleted.

@Deprecated(
    message = "Use LocalOctoAllyColors.current.bgPrimary inside a composable.",
    replaceWith = ReplaceWith(
        "LocalOctoAllyColors.current.bgPrimary",
        "com.octoally.core.ui.theme.LocalOctoAllyColors"
    )
)
val ColorBg            = Color(0xFF0A0A0A)

@Deprecated(
    message = "Use LocalOctoAllyColors.current.bgSecondary inside a composable.",
    replaceWith = ReplaceWith(
        "LocalOctoAllyColors.current.bgSecondary",
        "com.octoally.core.ui.theme.LocalOctoAllyColors"
    )
)
val ColorSurface       = Color(0xFF141414)

@Deprecated(
    message = "Use LocalOctoAllyColors.current.bgTertiary inside a composable.",
    replaceWith = ReplaceWith(
        "LocalOctoAllyColors.current.bgTertiary",
        "com.octoally.core.ui.theme.LocalOctoAllyColors"
    )
)
val ColorSurfaceRaised = Color(0xFF1F1F1F)

@Deprecated(
    message = "Use LocalOctoAllyColors.current.textPrimary inside a composable.",
    replaceWith = ReplaceWith(
        "LocalOctoAllyColors.current.textPrimary",
        "com.octoally.core.ui.theme.LocalOctoAllyColors"
    )
)
val ColorTextPrimary   = Color(0xFFF0F0F0)

@Deprecated(
    message = "Use LocalOctoAllyColors.current.textSecondary inside a composable.",
    replaceWith = ReplaceWith(
        "LocalOctoAllyColors.current.textSecondary",
        "com.octoally.core.ui.theme.LocalOctoAllyColors"
    )
)
val ColorTextMuted     = Color(0xFF5A5A5A)

@Deprecated(
    message = "Use LocalOctoAllyColors.current.accent inside a composable.",
    replaceWith = ReplaceWith(
        "LocalOctoAllyColors.current.accent",
        "com.octoally.core.ui.theme.LocalOctoAllyColors"
    )
)
val ColorAccent        = Color(0xFFD4E85C)

@Deprecated(
    message = "Accent pulse is a single-frame animation token with no theme-aware equivalent yet.",
    replaceWith = ReplaceWith("LocalOctoAllyColors.current.accent")
)
val ColorAccentPulse   = Color(0xFFE8FF6B)

// ── Semantic aliases (deprecated) ─────────────────────────────────────────────
@Deprecated("Use LocalOctoAllyColors.current.success")
val ColorConnected     = ColorAccent

@Deprecated("Use LocalOctoAllyColors.current.warning")
val ColorStale         = Color(0xFFE07B54)

@Deprecated("Use LocalOctoAllyColors.current.accent")
val ColorConnecting    = Color(0xFF5A8AA0)

@Deprecated("Use LocalOctoAllyColors.current.textSecondary")
val ColorDisconnected  = ColorTextMuted
