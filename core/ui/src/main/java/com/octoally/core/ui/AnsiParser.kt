package com.octoally.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Parses raw terminal output containing ANSI/VT100 escape sequences into
 * Compose [AnnotatedString] with [SpanStyle] color/weight/decoration spans.
 *
 * Supports: SGR reset, bold, dim, italic, underline, strikethrough,
 * standard 8-color (30-37/40-47), bright (90-97/100-107),
 * 256-color (38;5;N / 48;5;N), and truecolor (38;2;R;G;B / 48;2;R;G;B).
 *
 * Non-SGR escape sequences (cursor movement, OSC, charset selection) are
 * handled the same as the previous stripping logic: cursor moves become
 * spaces, everything else is discarded.
 */
object AnsiParser {

    /**
     * Mutable style state carried across lines so a color set on line N
     * remains active on line N+1 until explicitly reset.
     */
    data class StyleState(
        val fg: Color? = null,
        val bg: Color? = null,
        val bold: Boolean = false,
        val dim: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
    )

    /**
     * Result of parsing a raw chunk of terminal output.
     *
     * @param lines Completed lines (split on \n) as [AnnotatedString].
     * @param partial Raw leftover text after the last \n — not yet a complete
     *   line, will be prepended to the next chunk.
     * @param styleState Carry-over style for the next chunk.
     */
    data class ParseResult(
        val lines: List<AnnotatedString>,
        val partial: String,
        val styleState: StyleState,
    )

    /**
     * Parse [raw] terminal text into colored [AnnotatedString] lines.
     *
     * @param raw The raw text, potentially containing ANSI escape sequences.
     * @param palette The 16-color ANSI palette for the current theme.
     * @param defaultFg Default foreground color (theme's textPrimary).
     * @param carryState Style state from the previous parse call.
     */
    fun parse(
        raw: String,
        palette: AnsiColorPalette,
        defaultFg: Color,
        carryState: StyleState = StyleState(),
    ): ParseResult {
        // Phase 1: strip non-SGR escapes (cursor→space, OSC/charset→empty)
        val cleaned = stripNonSgr(raw)

        // Phase 2: handle \r (carriage return) per logical line
        val crHandled = handleCarriageReturns(cleaned)

        // Phase 3: split into lines and parse SGR into AnnotatedString
        val parts = crHandled.split('\n')
        val completedRaw = parts.dropLast(1)
        val partialRaw = parts.last()

        var state = carryState
        val result = mutableListOf<AnnotatedString>()

        for (line in completedRaw) {
            val (annotated, newState) = parseLine(line, palette, defaultFg, state)
            if (annotated.text.isNotBlank()) {
                result.add(annotated)
            }
            state = newState
        }

        return ParseResult(
            lines = result,
            partial = partialRaw,
            styleState = state,
        )
    }

    /**
     * Parse a single line into [AnnotatedString], consuming SGR sequences
     * and applying them as [SpanStyle] spans.
     */
    private fun parseLine(
        line: String,
        palette: AnsiColorPalette,
        defaultFg: Color,
        initialState: StyleState,
    ): Pair<AnnotatedString, StyleState> {
        var state = initialState
        val annotated = buildAnnotatedString {
            var i = 0
            while (i < line.length) {
                if (line[i] == '\u001b' && i + 1 < line.length && line[i + 1] == '[') {
                    // Parse CSI sequence
                    val end = findCsiEnd(line, i + 2)
                    if (end >= 0) {
                        val finalByte = line[end]
                        if (finalByte == 'm') {
                            // SGR sequence — update style
                            val paramStr = line.substring(i + 2, end)
                            state = applySgr(paramStr, state, palette)
                        }
                        // Skip the entire sequence (SGR or not)
                        i = end + 1
                    } else {
                        // Unterminated CSI — skip ESC[
                        i += 2
                    }
                } else {
                    // Regular character — emit with current style
                    val style = stateToSpanStyle(state, defaultFg)
                    val textStart = i
                    // Batch consecutive non-escape characters
                    while (i < line.length && !(line[i] == '\u001b' && i + 1 < line.length && line[i + 1] == '[')) {
                        i++
                    }
                    withStyle(style) {
                        append(line.substring(textStart, i))
                    }
                }
            }
        }
        return annotated to state
    }

    /** Find the end index (final byte) of a CSI sequence starting at [start]. */
    private fun findCsiEnd(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c in '\u0040'..'\u007e') return i  // Final byte
            if (c !in '\u0030'..'\u003f' && c !in '\u0020'..'\u002f') return -1 // Invalid
            i++
        }
        return -1 // Unterminated
    }

    /** Apply SGR parameters to [state]. */
    private fun applySgr(
        paramStr: String,
        state: StyleState,
        palette: AnsiColorPalette,
    ): StyleState {
        val params = if (paramStr.isEmpty()) listOf(0)
        else paramStr.split(';').mapNotNull { it.toIntOrNull() }

        var s = state
        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> s = StyleState() // Reset all
                1 -> s = s.copy(bold = true)
                2 -> s = s.copy(dim = true)
                3 -> s = s.copy(italic = true)
                4 -> s = s.copy(underline = true)
                9 -> s = s.copy(strikethrough = true)
                22 -> s = s.copy(bold = false, dim = false)
                23 -> s = s.copy(italic = false)
                24 -> s = s.copy(underline = false)
                29 -> s = s.copy(strikethrough = false)
                in 30..37 -> s = s.copy(fg = palette[p - 30])
                38 -> {
                    // Extended foreground: 38;5;N or 38;2;R;G;B
                    val (color, skip) = parseExtendedColor(params, i, palette)
                    if (color != null) s = s.copy(fg = color)
                    i += skip
                }
                39 -> s = s.copy(fg = null) // Default fg
                in 40..47 -> s = s.copy(bg = palette[p - 40])
                48 -> {
                    val (color, skip) = parseExtendedColor(params, i, palette)
                    if (color != null) s = s.copy(bg = color)
                    i += skip
                }
                49 -> s = s.copy(bg = null) // Default bg
                in 90..97 -> s = s.copy(fg = palette[p - 90 + 8])
                in 100..107 -> s = s.copy(bg = palette[p - 100 + 8])
            }
            i++
        }
        return s
    }

    /**
     * Parse extended color (256 or truecolor) starting at params[startIndex].
     * Returns (Color?, skip count beyond startIndex).
     */
    private fun parseExtendedColor(
        params: List<Int>,
        startIndex: Int,
        palette: AnsiColorPalette,
    ): Pair<Color?, Int> {
        if (startIndex + 1 >= params.size) return null to 0
        return when (params[startIndex + 1]) {
            5 -> {
                // 256-color: 38;5;N
                if (startIndex + 2 < params.size) {
                    val n = params[startIndex + 2].coerceIn(0, 255)
                    AnsiColorPalette.color256(n, palette) to 2
                } else null to 1
            }
            2 -> {
                // Truecolor: 38;2;R;G;B
                if (startIndex + 4 < params.size) {
                    val r = params[startIndex + 2].coerceIn(0, 255)
                    val g = params[startIndex + 3].coerceIn(0, 255)
                    val b = params[startIndex + 4].coerceIn(0, 255)
                    Color(r, g, b) to 4
                } else null to 1
            }
            else -> null to 0
        }
    }

    /** Convert [StyleState] to a Compose [SpanStyle]. */
    private fun stateToSpanStyle(state: StyleState, defaultFg: Color): SpanStyle {
        val fg = when {
            state.dim && state.fg != null -> state.fg.copy(alpha = 0.6f)
            state.dim -> defaultFg.copy(alpha = 0.6f)
            state.fg != null -> state.fg
            else -> defaultFg
        }
        val decorations = buildList {
            if (state.underline) add(TextDecoration.Underline)
            if (state.strikethrough) add(TextDecoration.LineThrough)
        }
        return SpanStyle(
            color = fg,
            background = state.bg ?: Color.Unspecified,
            fontWeight = if (state.bold) FontWeight.Bold else null,
            fontStyle = if (state.italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isNotEmpty())
                TextDecoration.combine(decorations) else null,
        )
    }

    // ── Non-SGR escape stripping (carried from SessionViewModel) ────────

    // CSI cursor movement — replaced with space
    private val CSI_CURSOR_RE = Regex("\u001b\\[[0-9;?]*[ABCDGHdf]")
    // OSC sequences
    private val OSC_RE = Regex("\u001b][^\u0007\u001b]*(?:\u0007|\u001b\\\\)")
    // Character-set + two-char escapes
    private val ESC_SHORT_RE = Regex("\u001b(?:[()\\*+][A-Za-z0-9]|[A-Za-z=>])")
    // Orphaned CSI (no ESC prefix) — but NOT SGR 'm' sequences since we parse those
    private val ORPHAN_CSI_RE = Regex("\\[\\d+(?:;\\d+)*[A-LN-Za-ln-z]")
    // Control characters (keep \n, \t, \r, and ESC for SGR parsing)
    private val CONTROL_CHAR_RE = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1a\\x7f]")

    /** Strip non-SGR escapes. Keeps ESC[...m sequences for color parsing. */
    private fun stripNonSgr(raw: String): String =
        raw.replace(CSI_CURSOR_RE, " ")
            .replace(OSC_RE, "")
            .replace(ESC_SHORT_RE, "")
            .replace(ORPHAN_CSI_RE, "")
            .replace(CONTROL_CHAR_RE, "")
            .replace(Regex("  +"), " ")

    /** Handle \r: keep only text after last standalone \r per logical line.
     *  \r\n is a CRLF line ending (normalize to \n), not an overwrite. */
    private fun handleCarriageReturns(text: String): String {
        // Normalize CRLF → LF first (CRLF = line ending, not overwrite)
        val normalized = text.replace("\r\n", "\n")
        // Now standalone \r means "go to column 0, overwrite"
        return normalized.split('\n').joinToString("\n") { line ->
            val cr = line.lastIndexOf('\r')
            if (cr >= 0) line.substring(cr + 1) else line
        }
    }
}
