package com.octoally.feature.session.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.octoally.core.ui.*
import com.octoally.core.ui.rememberTapHaptic
import com.octoally.core.ui.theme.LocalOctoAllyColors
import com.octoally.core.ui.theme.OctoAllyColors
import com.octoally.core.ui.theme.ThemeController
import com.octoally.core.ui.theme.ThemePickerSheet
import com.octoally.feature.session.ConnectionStatus
import com.octoally.feature.session.SessionUiState
import com.octoally.feature.session.SessionViewModel
import com.octoally.feature.session.clipboard.ClipboardUploadViewModel
import com.octoally.feature.session.clipboard.UploadEvent
import com.octoally.feature.session.palette.CommandPaletteViewModel
import com.octoally.feature.session.palette.SelectedAction
import com.octoally.feature.session.skills.SkillSuggestViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First runnable screen.
 *
 * Wires SessionViewModel → UI. Uses a fake preview state so screen renders
 * without network — real WS wiring is handled by SessionViewModel on connect().
 */
@Composable
fun SessionScreen(
    viewModel: SessionViewModel = hiltViewModel(),
    paletteViewModel: CommandPaletteViewModel = hiltViewModel(),
    uploadViewModel: ClipboardUploadViewModel = hiltViewModel(),
    sharedImageUri: Uri? = null,
    onMenuClick: (() -> Unit)? = null,
    onNewSession: (() -> Unit)? = null,
    onOpenProjects: (() -> Unit)? = null,
    themeController: ThemeController? = null,
    appVersion: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // rc8: no session selected (cold launch with no MRU, or MRU 404'd server-side).
    // Short-circuit to the empty-state screen before wiring any WS machinery.
    if (uiState.noSession) {
        NoSessionPlaceholder(onOpenProjects = onOpenProjects ?: onMenuClick ?: {})
        return
    }
    val paletteState by paletteViewModel.state.collectAsStateWithLifecycle()
    val cmdState = rememberTextFieldState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    // Sub-sheets triggered from palette actions.
    var themePickerOpen by remember { mutableStateOf(false) }
    // Owned here so we can call sheetState.hide() and wait for the dismiss
    // animation to COMPLETE before showing the theme picker. Two simultaneous
    // ModalBottomSheets crash Material3 — the delay(300) approach was racy.
    @OptIn(ExperimentalMaterial3Api::class)
    val paletteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    @OptIn(ExperimentalMaterial3Api::class)
    val choiceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Choice prompts are mandatory — if one arrives while palette is open we
    // MUST close the palette first (Material3 crashes on two simultaneous sheets).
    val choicePromptActive = uiState.promptType == "choice" && uiState.choices.isNotEmpty()
    LaunchedEffect(choicePromptActive) {
        if (choicePromptActive && paletteState.open) {
            @OptIn(ExperimentalMaterial3Api::class)
            paletteSheetState.hide()
            paletteViewModel.close()
        }
    }

    // Surface ViewModel user-facing messages (execute/cancel failures, etc.).
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
        }
    }
    // Palette-load failures (skills catalog fetch) surface on the same host.
    LaunchedEffect(paletteViewModel) {
        paletteViewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
        }
    }

    // ── Photo Picker launcher (Android Photo Picker; no runtime permission) ──
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) uploadViewModel.upload(uri)
    }

    // Auto-upload a URI handed to us via ACTION_SEND (once per URI instance).
    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) uploadViewModel.upload(sharedImageUri)
    }

    // Upload events → snackbar + insert server path into the command field.
    LaunchedEffect(uploadViewModel) {
        uploadViewModel.events.collect { event ->
            when (event) {
                UploadEvent.Uploading -> snackbarHostState.showSnackbar(
                    message = "Uploading image…",
                    duration = SnackbarDuration.Short
                )
                is UploadEvent.Success -> {
                    insertPathIntoCommand(cmdState, event.path)
                    snackbarHostState.showSnackbar(
                        message = "Uploaded: ${event.path}",
                        duration = SnackbarDuration.Short
                    )
                }
                is UploadEvent.Failure -> snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    // rc10: auto-scroll handled by xterm internal scrollback; LazyColumn pin
    // logic retired along with the old output pane.

    val handlePaletteAction: (SelectedAction) -> Unit = { action ->
        when (action) {
            is SelectedAction.InvokeSkill   -> {
                viewModel.execute("/${action.skill.command}")
                @OptIn(ExperimentalMaterial3Api::class)
                scope.launch {
                    paletteSheetState.hide()
                    paletteViewModel.close()
                }
            }
            SelectedAction.OpenThemePicker  -> {
                // Theme picker is now an AlertDialog (not ModalBottomSheet),
                // so no two-sheet conflict — just close palette and show dialog.
                @OptIn(ExperimentalMaterial3Api::class)
                scope.launch {
                    paletteSheetState.hide()
                    paletteViewModel.close()
                }
                themePickerOpen = true
            }
            SelectedAction.NewSession       -> {
                @OptIn(ExperimentalMaterial3Api::class)
                scope.launch {
                    paletteSheetState.hide()
                    paletteViewModel.close()
                }
                onNewSession?.invoke()
            }
            SelectedAction.ToggleConnection -> {
                @OptIn(ExperimentalMaterial3Api::class)
                scope.launch {
                    paletteSheetState.hide()
                    paletteViewModel.close()
                }
                viewModel.reconnect()
            }
        }
    }

    // Mirror the command field into a plain String so SkillSuggestBar can
    // re-rank without the two composables sharing TextFieldState directly.
    // This keeps the decoupling required by Task #5.
    var commandInputText by remember { mutableStateOf("") }
    LaunchedEffect(cmdState) {
        snapshotFlow { cmdState.text.toString() }
            .collect { commandInputText = it }
    }

    val tap = rememberTapHaptic()
    Scaffold(
        topBar = { ConnectionTopBar(
            uiState = uiState,
            onMenuClick = onMenuClick?.let { cb -> { tap(); cb() } },
            onReconnectClick = { tap(); viewModel.reconnect() },
            appVersion = appVersion,
        ) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // rc16 — two FABs: + Terminal (one-tap new session, primary) and Search palette.
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onNewSession != null) {
                    SmallFloatingActionButton(
                        onClick = { tap(); onNewSession.invoke() },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "New terminal in this project",
                        )
                    }
                }
                SmallFloatingActionButton(
                    onClick = { tap(); paletteViewModel.open() },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Open command palette",
                    )
                }
            }
        },
        containerColor = ColorBg,
        modifier = Modifier.onPreviewKeyEvent { event ->
            // Hardware keyboard Ctrl+K / Cmd+K toggles the palette.
            val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
            if (event.type == KeyEventType.KeyDown && ctrlOrMeta && event.key == Key.K) {
                if (paletteState.open) paletteViewModel.close() else paletteViewModel.open()
                true
            } else {
                false
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // ── Terminal output pane — rc10 xterm.js WebView ──────────────────
            val sid = viewModel.sessionId
            if (sid != null) {
                TerminalWebView(
                    sessionId = sid,
                    baseUrl = viewModel.baseUrl,
                    fetchDisplay = { lines -> viewModel.fetchDisplayRaw(lines) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(LocalOctoAllyColors.current.bg),
                )
            } else {
                // Shouldn't happen — noSession short-circuits above — but keep
                // a structural placeholder so the Column still has a weighted pane.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0F1117))
                )
            }

            // ── Prompt area (confirmation only — choice is a top-level overlay) ──
            PromptRenderer(
                uiState = uiState,
                onConfirm = { yes -> tap(); viewModel.execute(if (yes) "y" else "n") },
            )

            // ── Skill-suggest pill bar (Task #5) ──────────────────────────────
            // Reads `commandInputText` (mirrored via snapshotFlow) instead of
            // hooking a text-watcher on cmdState directly. Tapping a pill both
            // records usage and triggers the slash-command execution below.
            SkillSuggestBar(
                inputText = commandInputText,
                onExecuteSkill = { skill -> viewModel.execute("/${skill.command}") },
            )

            // ── Command bar ───────────────────────────────────────────────────
            // During busy: disable input so Send/IME-Enter can't fire a second
            // command before the agent finishes (and the IME dismisses, which
            // is the visual cue the user actually reads).
            CommandBar(
                state = cmdState,
                enabled = (uiState.promptType == "text" || uiState.promptType == null) &&
                    uiState.processState != "busy",
                onSubmit = { input -> tap(); viewModel.execute(input) },
                isBusy = uiState.processState == "busy",
                onAttachClick = {
                    tap()
                    pickMedia.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )
        }
    }

    // ── Overlays ──────────────────────────────────────────────────────────────
    // Priority: choice (mandatory, modal) > palette > theme picker. Only one
    // ModalBottomSheet may be on screen at a time — Material3 crashes otherwise.
    if (choicePromptActive) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { /* mandatory — user must pick */ },
            sheetState = choiceSheetState,
            containerColor = ColorSurfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Select an option",
                    style = BodyL,
                    color = ColorTextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                uiState.choices.forEach { choice ->
                    TextButton(
                        onClick = { tap(); viewModel.execute(choice) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = choice,
                            style = BodyM,
                            color = ColorAccent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    } else if (paletteState.open) {
        @OptIn(ExperimentalMaterial3Api::class)
        CommandPaletteSheet(
            vm = paletteViewModel,
            sheetState = paletteSheetState,
            onDismiss = { paletteViewModel.close() },
            onAction = handlePaletteAction,
        )
    }

    if (themePickerOpen && themeController != null && !choicePromptActive) {
        ThemePickerSheet(
            controller = themeController,
            onDismiss = { themePickerOpen = false },
        )
    }
}

/** 3 seconds of idle scroll away from bottom before spring-back fires. */
private const val SCROLL_SNAP_BACK_DELAY_MS = 3_000L

/**
 * Append [path] to the command field, adding a leading space when the field
 * already has non-whitespace text so the user can compose a command around the
 * image reference (e.g. "summarise /tmp/octoally-drops/clipboard-123.png").
 */
private fun insertPathIntoCommand(state: TextFieldState, path: String) {
    val current = state.text.toString()
    val needsSpace = current.isNotEmpty() && !current.last().isWhitespace()
    state.edit {
        append(if (needsSpace) " $path" else path)
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionTopBar(
    uiState: SessionUiState,
    onMenuClick: (() -> Unit)? = null,
    onReconnectClick: (() -> Unit)? = null,
    appVersion: String? = null,
) {
    TopAppBar(
        title = {
            Text(
                text = if (appVersion != null) "Session · v$appVersion" else "Session",
                style = BodyL,
                color = ColorTextPrimary
            )
        },
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Open projects",
                        tint = ColorTextPrimary,
                    )
                }
            }
        },
        actions = {
            // Task #8 — show current model route when a route_decision event
            // has been seen for this session. Placed left of the connection
            // chip so the two pills read "what handled it | connection state".
            uiState.lastRoute?.let { route ->
                ModelBadge(route = route)
                Spacer(Modifier.width(8.dp))
            }
            ConnectionChip(
                status = uiState.connectionStatus,
                onReconnectClick = onReconnectClick
            )
            Spacer(Modifier.width(12.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorSurface)
    )
}

@Composable
private fun ConnectionChip(
    status: ConnectionStatus,
    onReconnectClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (status) {
        ConnectionStatus.CONNECTED    -> ColorConnected to "Connected"
        ConnectionStatus.CONNECTING   -> ColorConnecting to "Connecting"
        ConnectionStatus.STALE        -> ColorStale to "Tap to retry"
        ConnectionStatus.DISCONNECTED -> ColorDisconnected to "Tap to retry"
    }

    val animatedColor by animateColorAsState(
        targetValue = dotColor,
        animationSpec = tween(MotionDurationStandard),
        label = "chip_color"
    )

    val tappable = onReconnectClick != null &&
        (status == ConnectionStatus.STALE || status == ConnectionStatus.DISCONNECTED)

    Row(
        modifier = modifier
            .background(ColorSurfaceRaised, shape = MaterialTheme.shapes.small)
            .then(if (tappable) Modifier.clickable(onClick = onReconnectClick!!) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(animatedColor, shape = MaterialTheme.shapes.extraSmall)
        )
        Text(text = label, style = Caption, color = ColorTextPrimary)
    }
}

// ── Terminal line ────────────────────────────────────────────────────────────

// Matches execute result lines: [done 1.2s] output  or  [timeout 5.0s] output
private val EXEC_RESULT_RE = Regex("^\\[(.+?)\\s+([\\d.]+s)] (.*)", RegexOption.DOT_MATCHES_ALL)

@Composable
private fun TerminalLine(
    annotatedText: AnnotatedString,
    colors: OctoAllyColors,
    onCopy: () -> Unit,
) {
    val plainText = annotatedText.text
    val execMatch = EXEC_RESULT_RE.matchEntire(plainText)
    if (execMatch != null) {
        val (status, duration, output) = execMatch.destructured
        val isError = status != "done"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clickable(onClick = onCopy)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$status $duration",
                    style = Caption,
                    color = if (isError) colors.warning else colors.accent,
                )
            }
            if (output.isNotBlank()) {
                Text(
                    text = output.trim(),
                    style = MonoCode,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    } else {
        // Render with ANSI color spans from the parser
        Text(
            text = annotatedText,
            style = MonoCode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clickable(onClick = onCopy)
        )
    }
}

/** Metadata lines rendered in dim, smaller text — visually de-emphasized. */
@Composable
private fun MetadataLine(
    text: String,
    colors: OctoAllyColors,
    onCopy: () -> Unit,
) {
    Text(
        text = text,
        style = Caption,
        color = colors.textSecondary.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
            .clickable(onClick = onCopy)
    )
}

/**
 * rc8 empty-state screen. Rendered when the SessionViewModel has no session id
 * (cold launch without MRU, or MRU cleared by a 404 response). The button hands
 * back to the hosting activity, which navigates to the Projects tab.
 */
@Composable
private fun NoSessionPlaceholder(onOpenProjects: () -> Unit) {
    val colors = LocalOctoAllyColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "No session selected",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Open a project to start a terminal.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenProjects) {
                Text("Projects")
            }
        }
    }
}
