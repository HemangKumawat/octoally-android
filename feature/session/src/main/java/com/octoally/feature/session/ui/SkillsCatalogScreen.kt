package com.octoally.feature.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.network.model.Skill
import com.octoally.core.ui.BodyL
import com.octoally.core.ui.BodyM
import com.octoally.core.ui.Caption
import com.octoally.core.ui.JetBrainsMonoFamily
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.feature.session.skills.SkillsCatalogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsCatalogScreen(
    onExecuteSkill: (Skill) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SkillsCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalOctoAllyColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Skills",
                        style = BodyL,
                        color = colors.textPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.bgSecondary,
                ),
            )
        },
        containerColor = colors.bgPrimary,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Search bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bgSecondary),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Spacer(Modifier.width(4.dp))
                TextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearch,
                    placeholder = {
                        Text(
                            text = "Search skills…",
                            color = colors.textSecondary,
                            style = BodyM,
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Category filter chips ─────────────────────────────────────────
            if (state.categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryChip(
                        label = "All",
                        selected = state.selectedCategory == null,
                        colors = colors,
                        onClick = { viewModel.setCategory(null) },
                    )
                    for (category in state.categories) {
                        CategoryChip(
                            label = category,
                            selected = state.selectedCategory == category,
                            colors = colors,
                            onClick = { viewModel.setCategory(category) },
                        )
                    }
                }
            }

            // Hairline divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )

            // ── Skill list / empty state / loading ────────────────────────────
            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }

                state.filteredSkills.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "No skills found",
                                style = BodyM,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.filteredSkills, key = { it.id }) { skill ->
                            SkillCard(
                                skill = skill,
                                colors = colors,
                                onClick = { onExecuteSkill(skill) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    colors: com.octoally.core.ui.theme.OctoAllyColors,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = Caption,
                color = if (selected) colors.bgPrimary else colors.textSecondary,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accent,
            selectedLabelColor = colors.bgPrimary,
            containerColor = colors.bgTertiary,
            labelColor = colors.textSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.border,
            selectedBorderColor = colors.accent,
        ),
    )
}

@Composable
private fun SkillCard(
    skill: Skill,
    colors: com.octoally.core.ui.theme.OctoAllyColors,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colors.bgSecondary,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Skill name
                Text(
                    text = skill.name,
                    style = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                // /command in accent, monospace
                Text(
                    text = "/${skill.command}",
                    style = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = colors.accent,
                )
                // Description if present
                val desc = skill.description
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = LocalTextStyle.current.copy(fontSize = 13.sp),
                        color = colors.textSecondary,
                        maxLines = 2,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Category badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.bgTertiary)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = skill.category,
                    style = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = JetBrainsMonoFamily),
                    color = colors.textSecondary,
                )
            }
        }
    }
}
