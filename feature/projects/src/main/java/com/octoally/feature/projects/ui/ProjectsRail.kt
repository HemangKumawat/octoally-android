package com.octoally.feature.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.core.ui.theme.ThemeController
import com.octoally.core.ui.theme.ThemePickerSheet
import com.octoally.feature.projects.ProjectWithSessions
import com.octoally.feature.projects.ProjectsState
import com.octoally.feature.projects.ProjectsViewModel
import com.octoally.feature.projects.SessionSummary
import kotlin.math.max

/**
 * Left-side "Projects" rail mirroring the web dashboard sidebar.
 *
 * Renders a vertical list of projects from the local Room cache
 * ([ProjectsViewModel]). Each row is expandable and reveals its active
 * sessions plus a "+ New session" action. A search field at the top filters
 * projects client-side; a settings icon at the bottom opens the
 * [ThemePickerSheet].
 *
 * Hosted inside a [androidx.compose.material3.ModalNavigationDrawer] by the
 * caller; this composable itself is just the drawer content and is layout-
 * agnostic enough to be dropped into a permanent side column later without
 * changes.
 */
@Composable
fun ProjectsRail(
    themeController: ThemeController,
    onOpenSession: (projectId: String, sessionId: String) -> Unit,
    onNewSession: (projectId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var themeSheetOpen by remember { mutableStateOf(false) }
    val colors = LocalOctoAllyColors.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(colors.bgSecondary)
            .padding(vertical = 12.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Projects",
                color = colors.textPrimary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh projects",
                tint = if (state.loading) colors.textSecondary else colors.accent,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = !state.loading) { viewModel.refresh() }
            )
        }

        // ── Search field ──────────────────────────────────────────────────────
        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery
        )

        Spacer(Modifier.size(8.dp))

        // ── Project list ──────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading && state.projects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = LocalOctoAllyColors.current.accent,
                        strokeWidth = 2.dp
                    )
                }
            } else if (state.projects.isEmpty()) {
                EmptyProjectsState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = state.projects,
                        key = { it.project.cwd }
                    ) { item ->
                        ProjectRow(
                            item = item,
                            onToggle = { viewModel.toggleExpanded(item.project.cwd) },
                            onOpenSession = { sessionId ->
                                onOpenSession(item.project.cwd, sessionId)
                            },
                            onNewSession = { onNewSession(item.project.cwd) }
                        )
                    }
                }
            }
        }

        // ── Footer: settings icon ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Theme settings",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { themeSheetOpen = true }
            )
        }
    }

    if (themeSheetOpen) {
        ThemePickerSheet(
            controller = themeController,
            onDismiss = { themeSheetOpen = false }
        )
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val colors = LocalOctoAllyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgTertiary)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            textStyle = TextStyle(
                color = colors.textPrimary,
                fontSize = 14.sp
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search projects",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
                inner()
            }
        )
    }
}

@Composable
private fun ProjectRow(
    item: ProjectWithSessions,
    onToggle: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    onNewSession: () -> Unit
) {
    val colors = LocalOctoAllyColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
    ) {
        // Header row — tap toggles expansion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.project.label,
                    color = colors.textPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = item.project.cwd,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
            }
            Icon(
                imageVector = if (item.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (item.expanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (item.expanded) {
            // Session rows
            item.sessions.forEach { session ->
                SessionRow(session = session, onClick = { onOpenSession(session.id) })
            }
            // "+ New session" action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewSession)
                    .padding(start = 36.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "New session",
                    color = colors.accent,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    // Green = actively streaming output; amber = detached (alive but idle).
    val dotColor = if (session.status == "running") Color(0xFF4CAF50) else Color(0xFFFFA726)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 36.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, shape = CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = session.title,
            color = colors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = relativeTime(session.lastActiveAt),
            color = colors.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun EmptyProjectsState() {
    val colors = LocalOctoAllyColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "No projects",
            color = colors.textPrimary,
            fontSize = 14.sp
        )
        Text(
            text = "Create one on the server via the web UI. It will show up here once the agent registers it.",
            color = colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

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
