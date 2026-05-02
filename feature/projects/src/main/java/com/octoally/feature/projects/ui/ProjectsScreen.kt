package com.octoally.feature.projects.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.core.ui.theme.ThemeController
import com.octoally.feature.projects.ProjectWithSessions
import com.octoally.feature.projects.ProjectsViewModel
import com.octoally.feature.projects.SessionSummary
import kotlin.math.max

/**
 * Full-screen projects list — replaces the drawer rail.
 * Shows all projects with expandable sessions, search, and new-session action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    themeController: ThemeController,
    onOpenSession: (projectPath: String, sessionId: String) -> Unit,
    onNewSession: (projectPath: String, projectName: String) -> Unit,
    onNewSessionAdvanced: (projectPath: String, projectName: String) -> Unit = onNewSession,
    onOpenGit: (projectPath: String) -> Unit = {},
    onOpenFiles: (projectPath: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalOctoAllyColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        TopAppBar(
            title = {
                Text("Projects", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            },
            actions = {
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = !state.loading
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = if (state.loading) colors.textSecondary else colors.accent
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgPrimary)
        )

        // Search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bgSecondary)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (state.query.isEmpty()) {
                        Text("Search projects...", color = colors.textSecondary, fontSize = 15.sp)
                    }
                    inner()
                }
            )
        }

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.projects.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = colors.accent,
                            strokeWidth = 3.dp
                        )
                    }
                }
                state.projects.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.size(16.dp))
                        Text("No projects", color = colors.textPrimary, fontSize = 16.sp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Projects will appear here once created on the server.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = state.projects,
                            key = { it.project.cwd }
                        ) { item ->
                            ProjectCard(
                                item = item,
                                onToggle = { viewModel.toggleExpanded(item.project.cwd) },
                                onOpenSession = { sessionId ->
                                    onOpenSession(item.project.cwd, sessionId)
                                },
                                onNewSession = { onNewSession(item.project.cwd, item.project.label) },
                                onNewSessionAdvanced = { onNewSessionAdvanced(item.project.cwd, item.project.label) },
                                onOpenGit = { onOpenGit(item.project.cwd) },
                                onOpenFiles = { onOpenFiles(item.project.cwd) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    item: ProjectWithSessions,
    onToggle: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    onNewSession: () -> Unit,
    onNewSessionAdvanced: () -> Unit,
    onOpenGit: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val colors = LocalOctoAllyColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgSecondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.project.label,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.project.cwd,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
            if (item.sessions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgTertiary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${item.sessions.size}",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (item.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (item.expanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        if (item.expanded) {
            item.sessions.forEach { session ->
                SessionRow(session = session, onClick = { onOpenSession(session.id) })
            }
            // Action row: New session, Git, Files
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    // rc16 — tap = 1-tap "+ Terminal" (skip launcher form);
                    // long-press = launcher sheet for Advanced (mode/cli/task).
                    modifier = Modifier.combinedClickable(
                        onClick = onNewSession,
                        onLongClick = onNewSessionAdvanced,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ Terminal", color = colors.accent, fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.clickable(onClick = onOpenGit),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Git", color = colors.textSecondary, fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.clickable(onClick = onOpenFiles),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Files", color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    val dotColor = if (session.status == "running") Color(0xFF4CAF50) else Color(0xFFFFA726)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 48.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, shape = CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = session.title,
            color = colors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = relativeTime(session.lastActiveAt),
            color = colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

private fun relativeTime(lastActiveAtMs: Long): String {
    if (lastActiveAtMs <= 0L) return ""
    val deltaSec = max(0L, (System.currentTimeMillis() - lastActiveAtMs) / 1000L)
    return when {
        deltaSec < 60L        -> "just now"
        deltaSec < 3_600L     -> "${deltaSec / 60L}m ago"
        deltaSec < 86_400L    -> "${deltaSec / 3_600L}h ago"
        deltaSec < 2_592_000L -> "${deltaSec / 86_400L}d ago"
        else                  -> "${deltaSec / 2_592_000L}mo ago"
    }
}
