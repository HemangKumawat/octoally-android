package com.octoally.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.octoally.app.notifications.HookNotifier
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.core.ui.theme.OctoAllyMaterialTheme
import com.octoally.core.ui.theme.ThemeController
import com.octoally.feature.files.FileExplorerScreen
import com.octoally.feature.files.FileViewerScreen
import com.octoally.feature.git.GitPanelScreen
import com.octoally.feature.projects.ui.ActiveSessionsScreen
import com.octoally.feature.projects.ui.ProjectsScreen
import com.octoally.feature.session.SessionViewModel
import com.octoally.feature.session.ui.SessionLauncherSheet
import com.octoally.feature.session.ui.SessionScreen
import com.octoally.feature.session.ui.SkillsCatalogScreen
import com.octoally.core.network.model.Skill
import com.octoally.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Bottom navigation destinations ───────────────────────────────────────────

private enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    Projects("projects", "Projects", Icons.Outlined.Folder),
    Sessions("sessions", "Sessions", Icons.Outlined.PlayArrow),
    Terminal("terminal/{sessionId}?baseUrl={baseUrl}", "Terminal", Icons.Outlined.Terminal),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val sharedImageUriState = mutableStateOf<Uri?>(null)

    @Inject lateinit var themeController: ThemeController
    @Inject lateinit var networkConfig: com.octoally.core.network.NetworkConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        sharedImageUriState.value = extractSharedImageUri(intent)
        val pendingSessionId = intent?.getStringExtra(HookNotifier.EXTRA_NAVIGATE_SESSION)
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

        setContent {
            OctoAllyMaterialTheme(themeController = themeController) {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()
                val sharedUri by remember { sharedImageUriState }
                val baseUrl = networkConfig.mainBaseUrl.trimEnd('/')
                // rc8: DEFAULT_SESSION_ID removed. Cold launch lands on Projects.
                // MRU is checked lazily — we do NOT auto-navigate into the last
                // session, but the Terminal tab still resolves to it when the
                // user taps the bottom bar. The SessionVM validates the id
                // against the server on init and falls back to NoSession on 404.
                val mruSessionId = remember { readMruSessionId(this@MainActivity) }
                val effectiveSessionId: String? = pendingSessionId ?: mruSessionId
                val terminalRoute: String = effectiveSessionId
                    ?.let { "terminal/$it?baseUrl=${Uri.encode(baseUrl)}" }
                    ?: "terminal/none?baseUrl=${Uri.encode(baseUrl)}"

                // Notification permission (API 33+)
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* no-op */ }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val already = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!already) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // rc8: hook-notification deep-link. If launched via a notification
                // (pendingSessionId != null) jump straight into that session's
                // terminal; otherwise leave the user on Projects.
                LaunchedEffect(pendingSessionId) {
                    if (!pendingSessionId.isNullOrBlank()) {
                        writeMruSessionId(this@MainActivity, pendingSessionId)
                        navController.navigate(
                            "terminal/$pendingSessionId?baseUrl=${Uri.encode(baseUrl)}"
                        )
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val colors = LocalOctoAllyColors.current

                val snackbarHostState = remember { SnackbarHostState() }

                // Session launcher sheet state
                var launcherProjectPath by remember { mutableStateOf<String?>(null) }
                var launcherProjectName by remember { mutableStateOf("") }
                // Pending skill command from SkillsCatalog → terminal execution
                var pendingSkillCommand by remember { mutableStateOf<String?>(null) }

                // Helper for bottom nav selection — Terminal tab matches its route pattern
                fun isTabSelected(tab: BottomTab): Boolean = when (tab) {
                    BottomTab.Terminal -> currentRoute == tab.route
                    else -> currentRoute == tab.route
                }

                Scaffold(
                    containerColor = colors.bgPrimary,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(containerColor = colors.bgSecondary) {
                            BottomTab.entries.forEach { tab ->
                                val selected = isTabSelected(tab)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        val dest = if (tab == BottomTab.Terminal) terminalRoute else tab.route
                                        navController.navigate(dest) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                    colors = navItemColors(colors)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    // rc8: cold-launch destination is Projects. Deep-links from
                    // hook notifications (pendingSessionId != null) still open
                    // the terminal — see LaunchedEffect below.
                    NavHost(
                        navController = navController,
                        startDestination = BottomTab.Projects.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // ── Projects tab ─────────────────────────────────────
                        composable(BottomTab.Projects.route) {
                            val projectsVm: com.octoally.feature.projects.ProjectsViewModel = hiltViewModel()
                            // Surface refresh/create failures on the global snackbar host.
                            LaunchedEffect(projectsVm) {
                                projectsVm.messages.collect { msg ->
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                            ProjectsScreen(
                                viewModel = projectsVm,
                                themeController = themeController,
                                onOpenSession = { projectPath, sessionId ->
                                    writeMruSessionId(this@MainActivity, sessionId)
                                    // rc16 — remember which project this session belongs to so the
                                    // in-terminal "+" FAB can spawn another session in the same project.
                                    val projectName = projectsVm.state.value.projects
                                        .firstOrNull { it.project.cwd == projectPath }
                                        ?.project?.label.orEmpty()
                                    writeMruProject(this@MainActivity, projectPath, projectName)
                                    navController.navigate(
                                        "terminal/$sessionId?baseUrl=${Uri.encode(baseUrl)}"
                                    )
                                },
                                // rc16 — 1-tap path: skip launcher sheet, spawn Terminal directly.
                                onNewSession = { projectPath, projectName ->
                                    coroutineScope.launch {
                                        val sessionId = projectsVm.createSession(
                                            projectPath = projectPath,
                                            task = "Terminal",
                                            mode = "terminal",
                                            cliType = "claude",
                                        )
                                        if (sessionId != null) {
                                            writeMruSessionId(this@MainActivity, sessionId)
                                            writeMruProject(this@MainActivity, projectPath, projectName)
                                            navController.navigate(
                                                "terminal/$sessionId?baseUrl=${Uri.encode(baseUrl)}"
                                            )
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to create session")
                                        }
                                    }
                                },
                                // Long-press path: original launcher form (task / mode / cli).
                                onNewSessionAdvanced = { projectPath, projectName ->
                                    launcherProjectPath = projectPath
                                    launcherProjectName = projectName
                                },
                                onOpenGit = { projectPath ->
                                    navController.navigate("git/${Uri.encode(projectPath)}")
                                },
                                onOpenFiles = { projectPath ->
                                    navController.navigate("files/${Uri.encode(projectPath)}")
                                },
                            )

                            if (launcherProjectPath != null) {
                                val pendingName = launcherProjectName
                                SessionLauncherSheet(
                                    projectName = pendingName,
                                    onDismiss = { launcherProjectPath = null },
                                    onLaunch = { config ->
                                        val path = launcherProjectPath ?: return@SessionLauncherSheet
                                        launcherProjectPath = null
                                        coroutineScope.launch {
                                            val sessionId = projectsVm.createSession(
                                                projectPath = path,
                                                task = config.task,
                                                mode = config.mode,
                                                cliType = config.cliType,
                                            )
                                            if (sessionId != null) {
                                                writeMruSessionId(this@MainActivity, sessionId)
                                                writeMruProject(this@MainActivity, path, pendingName)
                                                navController.navigate(
                                                    "terminal/$sessionId?baseUrl=${Uri.encode(baseUrl)}"
                                                )
                                            } else {
                                                snackbarHostState.showSnackbar("Failed to create session")
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // ── Sessions tab ─────────────────────────────────────
                        composable(BottomTab.Sessions.route) {
                            ActiveSessionsScreen(
                                onOpenSession = { sessionId ->
                                    writeMruSessionId(this@MainActivity, sessionId)
                                    navController.navigate(
                                        "terminal/$sessionId?baseUrl=${Uri.encode(baseUrl)}"
                                    )
                                }
                            )
                        }

                        // ── Terminal tab (session screen) ────────────────────
                        composable(
                            route = "terminal/{${SessionViewModel.ARG_SESSION_ID}}" +
                                "?baseUrl={${SessionViewModel.ARG_BASE_URL}}",
                            arguments = listOf(
                                navArgument(SessionViewModel.ARG_SESSION_ID) {
                                    type = NavType.StringType
                                },
                                navArgument(SessionViewModel.ARG_BASE_URL) {
                                    type = NavType.StringType
                                    defaultValue = baseUrl
                                }
                            )
                        ) {
                            val sessionVm: SessionViewModel = hiltViewModel()
                            // rc16 — local ProjectsViewModel for one-shot createSession from
                            // the in-terminal "+" FAB. Per-backstack-entry instance.
                            val terminalProjectsVm: com.octoally.feature.projects.ProjectsViewModel = hiltViewModel()
                            // Consume pending skill command from SkillsCatalog
                            LaunchedEffect(pendingSkillCommand) {
                                pendingSkillCommand?.let { cmd ->
                                    sessionVm.execute(cmd)
                                    pendingSkillCommand = null
                                }
                            }
                            SessionScreen(
                                viewModel = sessionVm,
                                sharedImageUri = sharedUri,
                                onOpenProjects = {
                                    navController.navigate(BottomTab.Projects.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onMenuClick = {
                                    // Root cause of the "hamburger dead after
                                    // entering a session" bug: `terminal/{id}`
                                    // destinations all share the SAME node id
                                    // in the nav graph, so popUpTo(startDest)
                                    // + launchSingleTop + restoreState can
                                    // silently no-op from a non-start terminal.
                                    // Strategy: if Projects is already on the
                                    // back stack (it is, since users reach the
                                    // new terminal via Projects), just pop back
                                    // to it. Fall through to a fresh tab-swap
                                    // navigate only when Projects isn't there.
                                    val popped = navController.popBackStack(
                                        route = BottomTab.Projects.route,
                                        inclusive = false
                                    )
                                    if (!popped) {
                                        navController.navigate(BottomTab.Projects.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                onNewSession = {
                                    // rc16 — spawn a new "Terminal" session in the MRU project
                                    // (same project as the current session). 1-tap, no sheet.
                                    val mruPath = readMruProjectPath(this@MainActivity)
                                    val mruName = readMruProjectName(this@MainActivity).orEmpty()
                                    if (mruPath == null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Open a project first to spawn a new terminal"
                                            )
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            val sessionId = terminalProjectsVm.createSession(
                                                projectPath = mruPath,
                                                task = "Terminal",
                                                mode = "terminal",
                                                cliType = "claude",
                                            )
                                            if (sessionId != null) {
                                                writeMruSessionId(this@MainActivity, sessionId)
                                                writeMruProject(this@MainActivity, mruPath, mruName)
                                                navController.navigate(
                                                    "terminal/$sessionId?baseUrl=${Uri.encode(baseUrl)}"
                                                )
                                            } else {
                                                snackbarHostState.showSnackbar("Failed to create session")
                                            }
                                        }
                                    }
                                },
                                themeController = themeController,
                                appVersion = appVersion,
                            )
                        }

                        // ── Git panel ────────────────────────────────────────
                        composable(
                            route = "git/{projectPath}",
                            arguments = listOf(
                                navArgument("projectPath") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val projectPath = entry.arguments?.getString("projectPath") ?: ""
                            GitPanelScreen(
                                projectPath = Uri.decode(projectPath),
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        // ── File explorer ────────────────────────────────────
                        composable(
                            route = "files/{projectPath}",
                            arguments = listOf(
                                navArgument("projectPath") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val projectPath = entry.arguments?.getString("projectPath") ?: ""
                            FileExplorerScreen(
                                projectPath = Uri.decode(projectPath),
                                onOpenFile = { filePath ->
                                    navController.navigate(
                                        "fileview/${Uri.encode(projectPath)}/${Uri.encode(filePath)}"
                                    )
                                },
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        // ── File viewer ──────────────────────────────────────
                        composable(
                            route = "fileview/{projectPath}/{filePath}",
                            arguments = listOf(
                                navArgument("projectPath") { type = NavType.StringType },
                                navArgument("filePath") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val projectPath = entry.arguments?.getString("projectPath") ?: ""
                            val filePath = entry.arguments?.getString("filePath") ?: ""
                            FileViewerScreen(
                                projectPath = Uri.decode(projectPath),
                                filePath = Uri.decode(filePath),
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // ── Skills catalog ───────────────────────────────────
                        composable("skills") {
                            SkillsCatalogScreen(
                                onExecuteSkill = { skill ->
                                    pendingSkillCommand = "/${skill.command}"
                                    navController.navigate(terminalRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onDismiss = { navController.popBackStack() }
                            )
                        }

                        // ── Settings tab ─────────────────────────────────────
                        composable(BottomTab.Settings.route) {
                            SettingsScreen(appVersion = appVersion)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedImageUri(intent)?.let { sharedImageUriState.value = it }
    }

    private fun extractSharedImageUri(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("image/")) return null
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

@androidx.compose.runtime.Composable
private fun navItemColors(colors: com.octoally.core.ui.theme.OctoAllyColors) =
    NavigationBarItemDefaults.colors(
        selectedIconColor = colors.accent,
        selectedTextColor = colors.accent,
        unselectedIconColor = colors.textSecondary,
        unselectedTextColor = colors.textSecondary,
        indicatorColor = colors.bgTertiary,
    )
