package com.octoally.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.ui.MonoCode
import com.octoally.core.ui.theme.LocalOctoAllyColors

/**
 * Read-only monospace file viewer.
 *
 * Renders the file content with line numbers in a fixed-width left gutter.
 * Both horizontal and vertical scrolling are supported.
 *
 * @param projectPath Absolute path of the project root on the server.
 * @param filePath Server-relative path of the file to display.
 * @param onNavigateBack Called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    projectPath: String,
    filePath: String,
    onNavigateBack: () -> Unit,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val colors = LocalOctoAllyColors.current
    val fileContent by viewModel.fileContent.collectAsStateWithLifecycle()

    LaunchedEffect(projectPath, filePath) {
        viewModel.setProjectPath(projectPath)
        viewModel.readFile(filePath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = filePath.substringAfterLast('/'),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgSecondary),
        )

        when {
            fileContent == null || fileContent!!.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                }
            }

            fileContent!!.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = fileContent!!.error!!,
                        color = colors.error,
                        style = MonoCode,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            else -> {
                val lines = fileContent!!.content.lines()
                val gutterWidth = lines.size.toString().length.coerceAtLeast(2)

                val vertScroll = rememberScrollState()
                val horizScroll = rememberScrollState()

                // Outer box fills the remaining screen and enables vertical scroll
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vertScroll)
                ) {
                    // Inner row enables horizontal scroll for long lines.
                    // IntrinsicSize.Min ensures the row shrinks to content width so the
                    // horizontal scroll state measures correctly.
                    Row(
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .fillMaxWidth()
                            .horizontalScroll(horizScroll)
                    ) {
                        // ── Line-number gutter ────────────────────────────────
                        Column(
                            modifier = Modifier
                                .background(colors.bgSecondary)
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            lines.forEachIndexed { index, _ ->
                                Text(
                                    text = (index + 1).toString().padStart(gutterWidth),
                                    color = colors.textSecondary,
                                    style = MonoCode,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        // ── Code content ──────────────────────────────────────
                        Column(
                            modifier = Modifier
                                .background(colors.bgPrimary)
                                .fillMaxHeight()
                                .padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            lines.forEach { line ->
                                Text(
                                    text = line.ifEmpty { " " }, // keep row height for blank lines
                                    color = colors.textPrimary,
                                    style = MonoCode,
                                    fontSize = 12.sp,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
