package com.octoally.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.octoally.core.ui.theme.LocalOctoAllyColors

/**
 * Renders unified diff text with syntax highlighting:
 *   + added lines    → green background
 *   - removed lines  → red background
 *   @@ hunk headers  → dimmed grey background
 *   everything else  → transparent (inherits container bg)
 *
 * Supports horizontal scrolling for wide diffs and shows line numbers.
 */
@Composable
fun DiffViewer(
    diff: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOctoAllyColors.current
    val lines = diff.lines()

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    Box(
        modifier = modifier
            .background(colors.bgPrimary)
            .verticalScroll(verticalScroll)
            .horizontalScroll(horizontalScroll),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            lines.forEachIndexed { index, line ->
                DiffLine(
                    lineNumber = index + 1,
                    line = line,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun DiffLine(
    lineNumber: Int,
    line: String,
    colors: com.octoally.core.ui.theme.OctoAllyColors,
) {
    val (bgColor, textColor) = when {
        line.startsWith("+") && !line.startsWith("+++") ->
            colors.success.copy(alpha = 0.15f) to colors.success
        line.startsWith("-") && !line.startsWith("---") ->
            colors.error.copy(alpha = 0.15f) to colors.error
        line.startsWith("@@") ->
            colors.bgTertiary to colors.textSecondary
        line.startsWith("+++") || line.startsWith("---") ->
            colors.bgTertiary to colors.accent
        else ->
            Color.Transparent to colors.textPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 0.5.dp),
    ) {
        // Line number gutter
        Text(
            text = "%4d".format(lineNumber),
            color = colors.textSecondary.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(colors.bgSecondary)
                .padding(horizontal = 8.dp, vertical = 1.dp),
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = line,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}
