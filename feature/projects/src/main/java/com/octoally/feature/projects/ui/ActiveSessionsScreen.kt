package com.octoally.feature.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.feature.projects.ProjectsViewModel
import com.octoally.feature.projects.SessionSummary
import kotlin.math.max

/**
 * Global view of all active sessions across all projects.
 * Shows session cards grouped by project, with filter chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(
    onOpenSession: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalOctoAllyColors.current
    var filter by remember { mutableStateOf("all") } // all, running, detached

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        TopAppBar(
            title = { Text("Sessions", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
            actions = {
                IconButton(onClick = { viewModel.refresh() }, enabled = !state.loading) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = if (state.loading) colors.textSecondary else colors.accent
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgPrimary)
        )

        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            val chips = listOf("all" to "All", "running" to "Running", "detached" to "Detached")
            items(chips) { (id, label) ->
                FilterChip(
                    selected = filter == id,
                    onClick = { filter = id },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.copy(alpha = 0.15f),
                        selectedLabelColor = colors.accent,
                        containerColor = colors.bgTertiary,
                        labelColor = colors.textSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = colors.border,
                        selectedBorderColor = colors.accent,
                        enabled = true,
                        selected = filter == id,
                    )
                )
            }
        }

        // Collect all sessions from all projects
        val allSessions = state.projects.flatMap { pw ->
            pw.sessions.map { session -> pw.project.label to session }
        }.filter { (_, session) ->
            filter == "all" || session.status == filter
        }

        val totalCount = allSessions.size

        // Session count
        Text(
            text = "$totalCount active session${if (totalCount != 1) "s" else ""}",
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && allSessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = colors.accent,
                            strokeWidth = 3.dp
                        )
                    }
                }
                allSessions.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No active sessions", color = colors.textPrimary, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sessions will appear here when running.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    // Group by project
                    val grouped = allSessions.groupBy { it.first }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (projectName, sessions) ->
                            item(key = "header_$projectName") {
                                Text(
                                    text = projectName,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(
                                items = sessions,
                                key = { it.second.id }
                            ) { (_, session) ->
                                SessionCard(
                                    session = session,
                                    onClick = { onOpenSession(session.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionSummary, onClick: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    val dotColor = if (session.status == "running") Color(0xFF4CAF50) else Color(0xFFFFA726)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = session.status.replaceFirstChar { it.uppercase() },
                color = if (session.status == "running") colors.success else colors.warning,
                fontSize = 12.sp
            )
        }
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
