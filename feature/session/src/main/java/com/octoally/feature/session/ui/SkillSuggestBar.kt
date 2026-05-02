package com.octoally.feature.session.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.network.model.Skill
import com.octoally.core.ui.BodyL
import com.octoally.core.ui.Caption
import com.octoally.core.ui.MonoCode
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.feature.session.skills.GpuBadge
import com.octoally.feature.session.skills.SkillSuggestEffect
import com.octoally.feature.session.skills.SkillSuggestViewModel
import com.octoally.feature.session.skills.SuggestedSkill
import com.octoally.feature.session.skills.SuggestionSource

/** 200ms matches the theme-swap animation window used elsewhere. */
private const val SUGGEST_ANIM_MS = 200

/**
 * Skill-suggest pill bar. Renders above the [CommandBar]:
 *   [GPU badge] [pinned ★ pills] [recent 🕑 pills] [suggested ✨ pills] [⋯]
 *
 * This composable is intentionally decoupled from [CommandBar]: the parent
 * passes in [inputText] (the current TextFieldState value) and receives an
 * [onExecuteSkill] callback when a pill is tapped. No text-watcher is added
 * to the command input by this widget — Task #5's contract is to keep the
 * two composables composable in isolation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillSuggestBar(
    inputText: String,
    onExecuteSkill: (Skill) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillSuggestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalOctoAllyColors.current

    // Re-rank whenever the parent's input buffer changes.
    LaunchedEffect(inputText) { viewModel.onInputChange(inputText) }

    // Funnel invoke() -> parent execute call. The VM records usage; the
    // parent is responsible for actually running the slash command.
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SkillSuggestEffect.Execute -> onExecuteSkill(effect.skill)
            }
        }
    }

    val visible = state.suggestions.take(SkillSuggestViewModel.VISIBLE_CAP)
    val overflow = state.suggestions.size - visible.size

    // Collapse entirely when no suggestions — reclaim ~40dp of vertical space.
    if (visible.isNotEmpty()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(colors.bgSecondary)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GpuStatusBadge(state.gpu)

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(visible, key = { it.skill.id }) { item ->
                    SkillPill(
                        item = item,
                        onClick = { viewModel.invoke(item.skill) },
                    )
                }
                if (overflow > 0) {
                    item(key = "__overflow__") {
                        OverflowPill(count = overflow, onClick = { viewModel.toggleExpanded() })
                    }
                }
            }
        }
    }

    if (state.expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleExpanded() },
            sheetState = sheetState,
            containerColor = colors.bgSecondary,
        ) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(state.suggestions, key = { "sheet-${it.skill.id}" }) { item ->
                    ExpandedRow(item = item, onClick = {
                        viewModel.invoke(item.skill)
                        viewModel.toggleExpanded()
                    })
                }
            }
        }
    }
}

// ── Pill components ──────────────────────────────────────────────────────────

@Composable
private fun SkillPill(
    item: SuggestedSkill,
    onClick: () -> Unit,
) {
    val colors = LocalOctoAllyColors.current
    val containerColor by animateColorAsState(
        targetValue = colors.bgTertiary,
        animationSpec = tween(SUGGEST_ANIM_MS),
        label = "pill_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = colors.textPrimary,
        animationSpec = tween(SUGGEST_ANIM_MS),
        label = "pill_fg",
    )
    val borderColor by animateColorAsState(
        targetValue = colors.border,
        animationSpec = tween(SUGGEST_ANIM_MS),
        label = "pill_border",
    )

    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = sourceIcon(item.source),
            contentDescription = sourceLabel(item.source),
            modifier = Modifier.size(14.dp),
            tint = sourceTint(item.source),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = item.skill.command,
            style = MonoCode,
            color = contentColor,
        )
    }
}

@Composable
private fun OverflowPill(count: Int, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.bgTertiary,
            contentColor = colors.textSecondary,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.MoreHoriz,
            contentDescription = "Show $count more",
            modifier = Modifier.size(14.dp),
            tint = colors.textSecondary,
        )
        Spacer(Modifier.width(4.dp))
        Text(text = "+$count", style = Caption, color = colors.textSecondary)
    }
}

@Composable
private fun ExpandedRow(item: SuggestedSkill, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = sourceIcon(item.source),
            contentDescription = sourceLabel(item.source),
            modifier = Modifier.size(16.dp),
            tint = sourceTint(item.source),
        )
        Text(
            text = item.skill.command,
            style = MonoCode,
            color = colors.textPrimary,
            modifier = Modifier.width(160.dp),
        )
        Text(
            text = item.skill.description ?: item.skill.category,
            style = Caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.accent,
                contentColor = colors.bgPrimary,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(text = "Run", style = Caption, color = colors.bgPrimary)
        }
    }
}

// ── GPU badge ────────────────────────────────────────────────────────────────

@Composable
private fun GpuStatusBadge(state: GpuBadge) {
    val colors = LocalOctoAllyColors.current
    val targetDot = when (state) {
        GpuBadge.GPU      -> colors.success
        GpuBadge.CPU_ONLY -> colors.warning
        GpuBadge.UNKNOWN  -> colors.textSecondary
    }
    val animatedDot by animateColorAsState(
        targetValue = targetDot,
        animationSpec = tween(SUGGEST_ANIM_MS),
        label = "gpu_dot",
    )
    val label = when (state) {
        GpuBadge.GPU      -> "GPU"
        GpuBadge.CPU_ONLY -> "CPU"
        GpuBadge.UNKNOWN  -> "—"
    }
    // Description surfaced via contentDescription so TalkBack can announce it;
    // long-press tooltip would require tooltip APIs currently unavailable here.
    val description = when (state) {
        GpuBadge.GPU      -> "Ollama: GPU accelerated"
        GpuBadge.CPU_ONLY -> "Ollama: CPU-only fallback"
        GpuBadge.UNKNOWN  -> "Ollama: status unknown"
    }

    Row(
        modifier = Modifier
            .background(colors.bgTertiary, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(animatedDot, shape = CircleShape),
        )
        Icon(
            imageVector = Icons.Outlined.Memory,
            contentDescription = description,
            modifier = Modifier.size(12.dp),
            tint = colors.textSecondary,
        )
        Text(text = label, style = Caption, color = colors.textPrimary)
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun sourceIcon(source: SuggestionSource): ImageVector = when (source) {
    SuggestionSource.PINNED    -> Icons.Outlined.PushPin
    SuggestionSource.RECENT    -> Icons.Outlined.Schedule
    SuggestionSource.SUGGESTED -> Icons.Outlined.AutoAwesome
}

/** TalkBack label — read before the skill command. */
private fun sourceLabel(source: SuggestionSource): String = when (source) {
    SuggestionSource.PINNED    -> "Pinned skill"
    SuggestionSource.RECENT    -> "Recent skill"
    SuggestionSource.SUGGESTED -> "Suggested skill"
}

@Composable
private fun sourceTint(source: SuggestionSource) = when (source) {
    SuggestionSource.PINNED    -> LocalOctoAllyColors.current.accent
    SuggestionSource.RECENT    -> LocalOctoAllyColors.current.textSecondary
    SuggestionSource.SUGGESTED -> LocalOctoAllyColors.current.accent
}
