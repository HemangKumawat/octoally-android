package com.octoally.feature.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.feature.session.palette.CommandPaletteViewModel
import com.octoally.feature.session.palette.PaletteResult
import com.octoally.feature.session.palette.SelectedAction

/**
 * Ctrl+K-style command palette as an Android [ModalBottomSheet].
 *
 * Port of `dashboard/src/components/CommandPalette.tsx`. Kept inside the
 * :feature:session module so it can freely read theme tokens from :core:ui
 * and skill data from the VM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteSheet(
    vm: CommandPaletteViewModel = hiltViewModel(),
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onAction: (SelectedAction) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = LocalOctoAllyColors.current
    val focusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val targetHeight = (configuration.screenHeightDp * 0.8f).dp

    LaunchedEffect(Unit) {
        // Autofocus the search field when the sheet opens.
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            vm.close()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = colors.bgSecondary,
    ) {
        CompositionLocalProvider(LocalOctoAllyColors provides colors) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetHeight),
        ) {
            // ── Search input (autofocus, Esc closes) ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = {
                        Text(
                            text = "Search skills, actions...",
                            color = colors.textSecondary,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                vm.close()
                                onDismiss()
                                true
                            } else {
                                false
                            }
                        },
                )
            }

            // Divider line (hairline in theme border color).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )

            // ── Results list ─────────────────────────────────────────────────
            if (state.results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.loading) "Loading skills…" else "No results",
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.results, key = { rowKey(it) }) { row ->
                        PaletteRow(
                            result = row,
                            onClick = {
                                vm.onSelect(row) { action ->
                                    onAction(action)
                                    // onDismiss() intentionally NOT called here — each action in
                                    // handlePaletteAction owns its own dismissal so that
                                    // OpenThemePicker can await paletteSheetState.hide() before
                                    // composing the theme sheet (two simultaneous ModalBottomSheets
                                    // crash Material3).
                                }
                            },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PaletteRow(
    result: PaletteResult,
    onClick: () -> Unit,
) {
    val colors = LocalOctoAllyColors.current
    val (icon, label, category, description) = when (result) {
        is PaletteResult.SkillResult -> RowContent(
            icon = Icons.Outlined.Bolt,
            label = result.skill.command,
            category = result.skill.category,
            description = result.skill.description,
        )
        is PaletteResult.ActionResult -> RowContent(
            icon = result.icon,
            label = result.label,
            category = "action",
            description = null,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
            style = LocalTextStyle.current.copy(fontSize = 14.sp),
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        // Category chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.bgTertiary)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = category,
                color = colors.textSecondary,
                style = LocalTextStyle.current.copy(fontSize = 10.sp),
            )
        }
        if (description != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = description,
                color = colors.textSecondary,
                style = LocalTextStyle.current.copy(fontSize = 11.sp),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }
    }
}

private data class RowContent(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val category: String,
    val description: String?,
)

private fun rowKey(row: PaletteResult): String = when (row) {
    is PaletteResult.SkillResult -> "s:${row.skill.id}"
    is PaletteResult.ActionResult -> "a:${row.id}"
}
