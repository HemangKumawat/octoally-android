package com.octoally.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.network.model.GitFileDto
import com.octoally.core.ui.theme.LocalOctoAllyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitPanelScreen(
    projectPath: String,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GitViewModel = hiltViewModel(),
) {
    val colors = LocalOctoAllyColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Set projectPath once on first composition; reload if it changes.
    LaunchedEffect(projectPath) {
        viewModel.setProjectPath(projectPath)
        viewModel.loadStatus()
    }

    var showCommitDialog by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }

    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        containerColor = colors.bgPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Git", color = colors.textPrimary)
                        if (state.branch.isNotBlank()) {
                            BranchChip(branch = state.branch, colors = colors)
                        }
                        if (state.ahead > 0) {
                            AheadBehindIndicator(count = state.ahead, up = true, colors = colors)
                        }
                        if (state.behind > 0) {
                            AheadBehindIndicator(count = state.behind, up = false, colors = colors)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.bgSecondary,
                    titleContentColor = colors.textPrimary,
                ),
                actions = {
                    IconButton(onClick = { viewModel.loadStatus() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = colors.textSecondary)
                    }
                }
            )
        },
        bottomBar = {
            GitActionBar(
                onStageAll = {
                    val unstaged = state.files.filter { !it.staged }.map { it.path }
                    if (unstaged.isNotEmpty()) viewModel.stageFiles(unstaged)
                },
                onUnstageAll = {
                    val staged = state.files.filter { it.staged }.map { it.path }
                    if (staged.isNotEmpty()) viewModel.unstageFiles(staged)
                },
                onCommit = { showCommitDialog = true },
                onPush = { viewModel.push() },
                onPull = { viewModel.pull() },
                colors = colors,
            )
        },
        modifier = modifier,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { viewModel.loadStatus() },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "", color = colors.error, fontSize = 14.sp)
                }
            } else if (state.files.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No changes", color = colors.textSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bgPrimary),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(state.files, key = { it.path }) { file ->
                        GitFileRow(
                            file = file,
                            onToggleStage = {
                                if (file.staged) viewModel.unstageFiles(listOf(file.path))
                                else viewModel.stageFiles(listOf(file.path))
                            },
                            colors = colors,
                        )
                        HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (showCommitDialog) {
        CommitDialog(
            message = commitMessage,
            onMessageChange = { commitMessage = it },
            onDismiss = { showCommitDialog = false },
            onConfirm = {
                if (commitMessage.isNotBlank()) {
                    viewModel.commit(commitMessage)
                    commitMessage = ""
                    showCommitDialog = false
                }
            },
            colors = colors,
        )
    }
}

@Composable
private fun BranchChip(branch: String, colors: com.octoally.core.ui.theme.OctoAllyColors) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.bgTertiary,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = branch,
            color = colors.accent,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AheadBehindIndicator(
    count: Int,
    up: Boolean,
    colors: com.octoally.core.ui.theme.OctoAllyColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            imageVector = if (up) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
            contentDescription = if (up) "Ahead" else "Behind",
            tint = if (up) colors.success else colors.warning,
            modifier = Modifier.size(14.dp),
        )
        Text(text = "$count", color = if (up) colors.success else colors.warning, fontSize = 12.sp)
    }
}

@Composable
private fun GitFileRow(
    file: GitFileDto,
    onToggleStage: () -> Unit,
    colors: com.octoally.core.ui.theme.OctoAllyColors,
) {
    val statusColor = when (file.status.lowercase()) {
        "added"     -> colors.success
        "deleted"   -> colors.error
        "modified"  -> colors.warning
        "renamed"   -> colors.accent
        else        -> colors.textSecondary
    }
    val statusLabel = when (file.status.lowercase()) {
        "added"     -> "A"
        "deleted"   -> "D"
        "modified"  -> "M"
        "renamed"   -> "R"
        else        -> "?"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPrimary)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status badge
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = statusColor.copy(alpha = 0.15f),
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(statusLabel, color = statusColor, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = file.path,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )

        Checkbox(
            checked = file.staged,
            onCheckedChange = { onToggleStage() },
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.textSecondary,
                checkmarkColor = colors.bgPrimary,
            ),
        )
    }
}

@Composable
private fun GitActionBar(
    onStageAll: () -> Unit,
    onUnstageAll: () -> Unit,
    onCommit: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    colors: com.octoally.core.ui.theme.OctoAllyColors,
) {
    Surface(color = colors.bgSecondary, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GitActionButton("Stage All", colors.bgTertiary, colors.textPrimary, onStageAll, Modifier.weight(1f))
            GitActionButton("Unstage", colors.bgTertiary, colors.textPrimary, onUnstageAll, Modifier.weight(1f))
            GitActionButton("Commit", colors.accent, colors.bgPrimary, onCommit, Modifier.weight(1f))
            GitActionButton("Push", colors.success.copy(alpha = 0.8f), colors.bgPrimary, onPush, Modifier.weight(1f))
            GitActionButton("Pull", colors.warning.copy(alpha = 0.8f), colors.bgPrimary, onPull, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GitActionButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun CommitDialog(
    message: String,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    colors: com.octoally.core.ui.theme.OctoAllyColors,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSecondary,
        title = { Text("Commit", color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                label = { Text("Commit message", color = colors.textSecondary) },
                placeholder = { Text("Describe your changes…", color = colors.textSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                ),
                maxLines = 4,
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = message.isNotBlank(),
            ) {
                Text("Commit", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
    )
}
