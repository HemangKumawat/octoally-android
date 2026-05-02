package com.octoally.core.ui.theme

import androidx.compose.runtime.Composable

@Composable
fun OctoAllyTheme(content: @Composable () -> Unit) {
    OctoAllyMaterialTheme(theme = OctoAllyTheme.Operator, content = content)
}
