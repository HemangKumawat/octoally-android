package com.octoally.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.network.model.FileEntry
import com.octoally.core.ui.MonoCode
import com.octoally.core.ui.theme.LocalOctoAllyColors

/**
 * Full-screen file explorer for a single project.
 *
 * @param projectPath Absolute path of the project root on the server.
 * @param onOpenFile Called with the server-relative file path when the user taps a file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    projectPath: String,
    onOpenFile: (path: String) -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val colors = LocalOctoAllyColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectPath) {
        viewModel.setProjectPath(projectPath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Files",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    if (state.currentPath.isNotBlank()) {
                        Text(
                            text = state.currentPath,
                            color = colors.textSecondary,
                            style = MonoCode,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (state.currentPath.isNotBlank()) viewModel.goUp()
                        else onNavigateBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = if (state.currentPath.isNotBlank()) "Go up" else "Back",
                        tint = colors.textPrimary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgSecondary),
        )

        when {
            state.loading -> LoadingState(modifier = Modifier.fillMaxSize())

            state.error != null -> ErrorState(
                message = state.error!!,
                modifier = Modifier.fillMaxSize(),
            )

            state.entries.isEmpty() -> EmptyState(modifier = Modifier.fillMaxSize())

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.path }) { entry ->
                    if (entry.type == "directory") {
                        DirectoryRow(
                            entry = entry,
                            onClick = { viewModel.navigate(entry.path) },
                        )
                    } else {
                        FileRow(
                            entry = entry,
                            onClick = { onOpenFile(entry.path) },
                        )
                    }
                }
            }
        }
    }
}

// ── Row composables ───────────────────────────────────────────────────────────

@Composable
private fun DirectoryRow(entry: FileEntry, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = entry.name,
            color = colors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconForFile(entry.name),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = entry.name,
            color = colors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.size > 0) {
            Text(
                text = formatSize(entry.size),
                color = colors.textSecondary,
                style = MonoCode,
                fontSize = 11.sp,
            )
        }
    }
}

// ── Utility composables ───────────────────────────────────────────────────────

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val colors = LocalOctoAllyColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = LocalOctoAllyColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "Empty directory",
                color = colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    val colors = LocalOctoAllyColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = colors.error,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

// ── Pure helpers ──────────────────────────────────────────────────────────────

private fun iconForFile(name: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "ts", "js", "py", "java", "go", "rs", "cpp", "c", "h", "swift" ->
            Icons.Outlined.Code
        "json", "yaml", "yml", "toml" ->
            Icons.Outlined.DataObject
        "md", "txt", "rst" ->
            Icons.Outlined.Article
        else ->
            Icons.Outlined.Description
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1_024L -> "${bytes}B"
    bytes < 1_048_576L -> "${"%.1f".format(bytes / 1_024.0)}K"
    bytes < 1_073_741_824L -> "${"%.1f".format(bytes / 1_048_576.0)}M"
    else -> "${"%.1f".format(bytes / 1_073_741_824.0)}G"
}
