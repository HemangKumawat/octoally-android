package com.octoally.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.core.ui.theme.OctoAllyTheme

private val SETTINGS_THEME_IDS = arrayOf(
    "operator", "neon", "crt", "studio", "zen", "critter"
)
private val SETTINGS_THEME_NAMES = arrayOf(
    "Pro Operator", "Neon Vibecoder", "Retro CRT", "Tasteful Studio", "Swiss Minimal", "Playful Creature"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appVersion: String,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalOctoAllyColors.current

    LaunchedEffect(appVersion) { viewModel.setAppVersion(appVersion) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        TopAppBar(
            title = { Text("Settings", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgPrimary)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Server Connection ─────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Cloud, title = "Server Connection")

            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::updateHost,
                label = { Text("Host / IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(colors)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.mainPort.toString(),
                    onValueChange = viewModel::updateMainPort,
                    label = { Text("Main Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors(colors)
                )
                OutlinedTextField(
                    value = state.uploadPort.toString(),
                    onValueChange = viewModel::updateUploadPort,
                    label = { Text("Upload Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors(colors)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.saveConnection()
                        viewModel.testConnection()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.bgPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save & Test")
                }

                when (state.connectionTestResult) {
                    ConnectionTestResult.Testing -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).align(Alignment.CenterVertically),
                        color = colors.accent,
                        strokeWidth = 2.dp
                    )
                    ConnectionTestResult.Success -> StatusDot(
                        color = colors.success,
                        label = "Connected",
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    ConnectionTestResult.Failed -> StatusDot(
                        color = colors.error,
                        label = "Failed",
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    ConnectionTestResult.Idle -> {}
                }
            }

            // ── Appearance ────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Palette, title = "Appearance")

            Text("Theme", color = colors.textSecondary, fontSize = 13.sp)

            // Plain-string arrays — zero sealed-class references captured in
            // the forEach lambda closure. The Compose recomposer nullifies
            // sealed-class singleton objects when captured this way (same bug
            // class ThemePickerSheet.kt already dodges).
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in SETTINGS_THEME_IDS.indices) {
                    val id = SETTINGS_THEME_IDS[i]
                    val name = SETTINGS_THEME_NAMES[i]
                    ThemeRow(
                        name = name,
                        selected = state.themeId == id,
                        onClick = { viewModel.setTheme(OctoAllyTheme.fromId(id)) }
                    )
                }
            }

            // ── Terminal Font Size ────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.TextFields, title = "Terminal Font Size")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.terminalFontSize}sp",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(48.dp)
                )
                Slider(
                    value = state.terminalFontSize.toFloat(),
                    onValueChange = { viewModel.updateFontSize(it.toInt()) },
                    valueRange = 8f..20f,
                    steps = 11,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.bgTertiary
                    )
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Info, title = "About")

            InfoRow(label = "App Version", value = state.appVersion.ifBlank { "---" })
            InfoRow(label = "Server", value = state.serverVersion.ifBlank { "---" })

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    val colors = LocalOctoAllyColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThemeRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StatusDot(color: Color, label: String, modifier: Modifier = Modifier) {
    val textColors = LocalOctoAllyColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, color = textColors.textPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalOctoAllyColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.textSecondary, fontSize = 14.sp)
        Text(value, color = colors.textPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun textFieldColors(colors: com.octoally.core.ui.theme.OctoAllyColors) =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.accent,
    )
