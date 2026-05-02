package com.octoally.feature.session.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.octoally.core.ui.BodyM
import com.octoally.core.ui.ColorAccent
import com.octoally.core.ui.ColorBg
import com.octoally.core.ui.ColorTextPrimary
import com.octoally.feature.session.SessionUiState

/**
 * Inline prompt affordances rendered ABOVE the CommandBar.
 *
 * Only the confirmation case renders here — the "choice" prompt is a full
 * ModalBottomSheet and is rendered as a top-level overlay by SessionScreen
 * (alongside palette/theme) so sheet-state is a single owner. Nesting a
 * second ModalBottomSheet inside this composable used to crash Material3
 * when the palette sheet was also open.
 *
 *   - "confirmation" → twin Y/N buttons
 *   - "text" / null  → nothing (CommandBar handles busy + input inline)
 *   - "choice"       → nothing here; SessionScreen owns it
 */
@Composable
fun PromptRenderer(
    uiState: SessionUiState,
    onConfirm: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = uiState.promptType,
        label = "prompt_type_transition",
        modifier = modifier
    ) { promptType ->
        when (promptType) {
            "confirmation" -> ConfirmationButtons(onConfirm = onConfirm)
            else           -> Unit
        }
    }
}

@Composable
private fun ConfirmationButtons(
    onConfirm: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onConfirm(true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
        ) {
            Text("Yes", style = BodyM, color = ColorBg)
        }
        OutlinedButton(
            onClick = { onConfirm(false) },
            modifier = Modifier.weight(1f)
        ) {
            Text("No", style = BodyM, color = ColorTextPrimary)
        }
    }
}
