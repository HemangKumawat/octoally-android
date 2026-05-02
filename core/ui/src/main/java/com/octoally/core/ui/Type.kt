@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.octoally.core.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.octoally.core.ui.R

// ── Font families ─────────────────────────────────────────────────────────────
// Font .ttf files live in core/ui/src/main/res/font/ (single source of truth
// after the pass-2 consolidation). Feature modules inherit them transitively.
//
// Fraunces ships as a single variable font file with a weight axis; we map each
// weight slot to the same TTF and rely on variationSettings to pick the axis
// value at render time. Without variationSettings, all three slots would render
// at the VF default (wght=400).

val FrauncesFamily = FontFamily(
    Font(
        R.font.fraunces_600,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.fraunces_500,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.fraunces_400,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    )
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrainsmono_500, FontWeight.Medium),
    Font(R.font.jetbrainsmono_400, FontWeight.Normal)
)

// ── Type scale (locked) ───────────────────────────────────────────────────────

/** Fraunces 600 · 48sp — large display headings */
val DisplayL = TextStyle(
    fontFamily = FrauncesFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 48.sp
)

/** Fraunces 500 · 32sp — section headings */
val DisplayM = TextStyle(
    fontFamily = FrauncesFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 32.sp
)

/** Fraunces 400 · 17sp — body, list items */
val BodyL = TextStyle(
    fontFamily = FrauncesFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp
)

/** Fraunces 400 · 15sp — secondary body */
val BodyM = TextStyle(
    fontFamily = FrauncesFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp
)

/** JetBrains Mono 500 · 12sp — captions, metadata, letter-spacing 0.04em */
val Caption = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.04.em
)

/** JetBrains Mono 400 · 12sp — terminal output, code. Tight line height matches web xterm.js cell density. */
val MonoCode = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp
)
