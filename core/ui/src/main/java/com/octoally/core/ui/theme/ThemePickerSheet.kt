package com.octoally.core.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// rc13 — six personality variants from the 2026-04-28 design handoff. IDs and
// names mirror tokens.jsx PALETTES.<variant>.
private val THEME_IDS = arrayOf(
    "operator", "neon", "crt", "studio", "zen", "critter"
)
private val THEME_NAMES = arrayOf(
    "Pro Operator", "Neon Vibecoder", "Retro CRT", "Tasteful Studio", "Swiss Minimal", "Playful Creature"
)

@Composable
fun ThemePickerSheet(
    controller: ThemeController,
    onDismiss: () -> Unit
) {
    val colors = LocalOctoAllyColors.current
    val currentTheme by controller.current.collectAsState()
    val activeId = currentTheme.id

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(text = "Theme", color = colors.text) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (i in THEME_IDS.indices) {
                    val id = THEME_IDS[i]
                    val name = THEME_NAMES[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                controller.setTheme(OctoAllyTheme.fromId(id))
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            color = colors.text,
                            modifier = Modifier.weight(1f)
                        )
                        if (id == activeId) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = colors.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.primary)
            }
        }
    )
}
