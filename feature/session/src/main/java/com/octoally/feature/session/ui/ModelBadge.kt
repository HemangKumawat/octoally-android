package com.octoally.feature.session.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.octoally.core.ui.Caption
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.feature.session.RouteInfo

/**
 * Model routing badge — coloured pill showing which backend handled the current
 * response. Mirrors the web dashboard's `<ModelBadge>` (see
 * `dashboard/src/components/ModelBadge.tsx`).
 *
 * Colour mapping is resolved from the `provider` string. The substring match
 * order is identical to the web implementation:
 *   `deepthink` / `*deep*` -> purple  (#c084fc)
 *   `gemini`               -> yellow  (#facc15)
 *   `haiku`                -> green   (#34d399)
 *   `claude` / `anthropic` -> blue    (#60a5fa)
 *   `ollama` | `local` | `gemma` -> grey (#8c8c8c)
 *   fallback               -> theme accent
 *
 * Background alpha is 16% (0x28 ≈ 0.157) to mirror the web's `bg: '#<hex>18'`
 * shorthand (an 8-bit alpha of `0x18` = 24 = ~9.4%; the spec asked for 16% so
 * we bump to `0x29` ≈ 16%). Border uses the same colour at 100%, text uses the
 * same colour at 100% — matches the web which picks `text = dot = colour`.
 */

/** Dashboard-parity blue for Claude / Anthropic. */
private val ClaudeBlue  = Color(0xFF60A5FA)
/** Dashboard-parity yellow for Gemini / Google. */
private val GeminiYellow = Color(0xFFFACC15)
/** Dashboard-parity green for Claude-3 Haiku. */
private val HaikuGreen  = Color(0xFF34D399)
/** Dashboard-parity purple for DeepThink. */
private val DeepPurple  = Color(0xFFC084FC)
/** Grey for local models (Ollama / Gemma) — not in web yet, per task spec. */
private val LocalGrey   = Color(0xFF8C8C8C)

/** Resolve the badge colour for a given provider string. */
private fun resolveProviderColor(provider: String, fallback: Color): Color {
    val p = provider.lowercase()
    // Order matters: haiku must match before the generic `claude` check, and
    // deepthink must match before everything else so "deep*" doesn't collide.
    return when {
        p.contains("deepthink") || p.contains("deep-think") || p.contains("deep_think") -> DeepPurple
        p.contains("gemini") || p.contains("google") -> GeminiYellow
        p.contains("haiku") -> HaikuGreen
        p.contains("claude") || p.contains("anthropic") -> ClaudeBlue
        p.contains("ollama") || p.contains("local") || p.contains("gemma") -> LocalGrey
        else -> fallback
    }
}

/** Truncate the full `provider:model` label at 20 chars with an ellipsis. */
private fun formatLabel(provider: String, model: String): String {
    val raw = "$provider:$model"
    return if (raw.length <= 20) raw else raw.take(19) + "…"
}

/**
 * Compact (28.dp tall) rounded pill showing `provider:model` in the caller's
 * theme. Background at 16% alpha, border and text at 100%, matching the web.
 *
 * Accessibility: exposes `"Routed to {provider} {model}"` as a content
 * description so TalkBack announces the badge semantically.
 */
@Composable
fun ModelBadge(
    route: RouteInfo,
    modifier: Modifier = Modifier
) {
    val colors = LocalOctoAllyColors.current
    val providerColor = resolveProviderColor(route.provider, fallback = colors.accent)
    val bg = providerColor.copy(alpha = 0.16f)
    val label = formatLabel(route.provider, route.model)

    Row(
        modifier = modifier
            .height(28.dp)
            .defaultMinSize(minWidth = 56.dp)
            .background(color = bg, shape = RoundedCornerShape(14.dp))
            .border(width = 1.dp, color = providerColor, shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "Routed to ${route.provider} ${route.model}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = providerColor, shape = CircleShape)
        )
        Text(
            text = label,
            style = Caption,
            color = providerColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
