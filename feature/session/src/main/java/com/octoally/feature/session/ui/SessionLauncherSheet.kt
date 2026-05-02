package com.octoally.feature.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.octoally.core.ui.theme.LocalOctoAllyColors

data class SessionLaunchConfig(
    val task: String,
    val mode: String,
    val cliType: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLauncherSheet(
    projectName: String,
    onDismiss: () -> Unit,
    onLaunch: (SessionLaunchConfig) -> Unit,
) {
    val colors = LocalOctoAllyColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var task by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("session") }
    var cliType by remember { mutableStateOf("claude") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgSecondary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Text(
                text = "New Session",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = projectName,
                color = colors.textSecondary,
                fontSize = 13.sp
            )

            // Task description
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                label = { Text("Task description") },
                placeholder = { Text("What should the agent do?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.accent,
                    unfocusedLabelColor = colors.textSecondary,
                    cursorColor = colors.accent,
                    focusedPlaceholderColor = colors.textSecondary,
                    unfocusedPlaceholderColor = colors.textSecondary,
                )
            )

            // Mode selector
            Text("Mode", color = colors.textSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Session", Icons.Outlined.Terminal, mode == "session") { mode = "session" }
                ModeChip("Agent", Icons.Outlined.SmartToy, mode == "agent") { mode = "agent" }
                ModeChip("Terminal", Icons.Outlined.Code, mode == "terminal") { mode = "terminal" }
            }

            // CLI type selector
            Text("Backend", color = colors.textSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CliChip("Claude", cliType == "claude") { cliType = "claude" }
                CliChip("Codex", cliType == "codex") { cliType = "codex" }
            }

            // Launch button
            Button(
                onClick = {
                    onLaunch(
                        SessionLaunchConfig(
                            task = task.ifBlank { "Terminal" },
                            mode = mode,
                            cliType = cliType,
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.bgPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Launch", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accent.copy(alpha = 0.15f),
            selectedLabelColor = colors.accent,
            selectedLeadingIconColor = colors.accent,
            containerColor = colors.bgTertiary,
            labelColor = colors.textSecondary,
            iconColor = colors.textSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = colors.border,
            selectedBorderColor = colors.accent,
            enabled = true,
            selected = selected,
        )
    )
}

@Composable
private fun CliChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    val shape = RoundedCornerShape(8.dp)
    val bg = if (selected) colors.accent.copy(alpha = 0.15f) else colors.bgTertiary
    val borderColor = if (selected) colors.accent else colors.border
    val textColor = if (selected) colors.accent else colors.textSecondary

    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
