package com.octoally.feature.session

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.octoally.core.network.AgentEvent
import com.octoally.core.network.SessionSyncApi
import com.octoally.core.network.SessionSyncBinderApi
import com.octoally.core.network.api.SessionApi
import com.octoally.core.network.api.httpStatusOrNull
import com.octoally.core.network.model.ExecuteRequest
import com.octoally.core.ui.AnsiParser
import com.octoally.core.ui.DefaultAnsiPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * ViewModel for the session screen.
 *
 * Architecture (locked per A3 advisor decision):
 *   - VM does NOT own the WebSocket client. SessionSyncService does.
 *   - VM binds to SessionSyncService, calls observeSession(sessionId, baseUrl)
 *     to get a per-session Flow<AgentEvent>, and collects from THAT flow.
 *   - On onCleared() we only unbind. We never call disconnect — the service
 *     owns connection lifecycle (idle timeout, roam, shared across tabs).
 *
 * Binding is typed via [SessionSyncBinderApi] (lives in :core:network) so
 * :feature:session does not need a compile-time dependency on :app. The
 * earlier reflection-based cast (binder.javaClass.getMethod("getService"))
 * has been replaced by a checked `as?` cast — failures now surface at
 * compile time or as a null binder rather than silent reflection errors.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    application: Application,
    private val sessionApi: SessionApi,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // ── NavArgs ───────────────────────────────────────────────────────────────

    val sessionId: String? = savedStateHandle.get<String>(ARG_SESSION_ID)
        ?.takeIf { it.isNotBlank() }
    val baseUrl: String = savedStateHandle.get<String>(ARG_BASE_URL)
        ?: DEFAULT_BASE_URL

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    /**
     * One-shot user-facing messages (snackbars). Buffered so a failure that
     * fires before the UI subscribes isn't dropped silently.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** ANSI style carry-over state across output chunks. */
    private var ansiStyleState = AnsiParser.StyleState()

    // ── Service binding ───────────────────────────────────────────────────────

    private var boundApi: SessionSyncApi? = null
    private var eventCollectJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val api = (binder as? SessionSyncBinderApi)?.getApi() ?: return
            boundApi = api
            startCollecting(api)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundApi = null
            eventCollectJob?.cancel()
            eventCollectJob = null
        }
    }

    private var isBound: Boolean = false
    private var lastReconnectAt = 0L

    init {
        // If no session id was passed, emit NoSession immediately.
        // Otherwise validate the id against the server (cheap GET); a 404 means
        // the MRU pointed at a cancelled/deleted session — clear it and emit
        // NoSession so the user sees the empty-state instead of a zombie socket.
        if (sessionId == null) {
            _uiState.update { it.copy(noSession = true) }
        } else {
            viewModelScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { sessionApi.get(sessionId) }
                }
                val err = result.exceptionOrNull()
                if (err != null) {
                    if (httpStatusOrNull(err) == 404) {
                        clearMruSessionId(getApplication())
                        _uiState.update { it.copy(noSession = true) }
                    }
                    // 5xx / network: fall through — WS layer will surface it.
                    return@launch
                }
                val status = result.getOrNull()
                    ?.get("session")?.let { it as? JsonObject }
                    ?.get("status")?.jsonPrimitive?.content
                if (status == "detached") {
                    android.util.Log.i(TAG, "Session $sessionId detached → POST /reconnect")
                    val rc = withContext(Dispatchers.IO) {
                        runCatching { sessionApi.reconnect(sessionId) }
                    }
                    rc.onFailure { t ->
                        val code = httpStatusOrNull(t)
                        android.util.Log.w(TAG, "reconnect failed code=$code: ${t.message}")
                        if (code != null && code >= 500) {
                            _messages.tryEmit("Couldn't reattach session")
                        }
                        // 404 → server already marked it active; silent.
                    }
                    rc.onSuccess {
                        android.util.Log.i(TAG, "Session $sessionId reattached OK")
                    }
                }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Bind to SessionSyncService. Safe to call multiple times. No-op when session id is null. */
    fun connect() {
        if (isBound) return
        if (sessionId == null) return
        val ctx: Context = getApplication()
        val intent = Intent().apply {
            setClassName(ctx, SERVICE_FQN)
        }
        // On API 34+ a foreground service that calls startForeground() in its
        // onCreate must have been started via startForegroundService() — plain
        // bindService(BIND_AUTO_CREATE) does NOT grant the foreground-start
        // token, so the service throws ForegroundServiceStartNotAllowedException
        // and the whole process dies. Start the service explicitly first, then
        // bind to it. bindService is idempotent once the service is running.
        runCatching { ContextCompat.startForegroundService(ctx, intent) }
        val ok = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        isBound = ok
    }

    /** POST /sessions/:id/execute with optional waitFor hint. */
    fun execute(input: String, waitFor: String? = null) {
        val id = sessionId ?: return
        viewModelScope.launch {
            runCatching { sessionApi.execute(id, ExecuteRequest(input, waitFor)) }
                .onFailure { _messages.tryEmit("Send failed: ${it.shortReason()}") }
        }
    }

    /**
     * rc10: cold-entry seed for [com.octoally.feature.session.ui.TerminalWebView].
     * Returns raw PTY bytes (ANSI preserved) from `GET /api/sessions/:id/display`
     * or null on error. Does NOT mutate [uiState] — the WebView owns render.
     */
    suspend fun fetchDisplayRaw(lines: Int = 2000): String? {
        val id = sessionId ?: return null
        return runCatching {
            withContext(Dispatchers.IO) { sessionApi.display(id, lines) }
                .get("output")?.jsonPrimitive?.content
        }.getOrNull()
    }

    /**
     * Bypass the current reconnect backoff and force an immediate WS reconnect.
     * Debounced to [RECONNECT_DEBOUNCE_MS] so rapid palette taps don't queue
     * multiple reconnects. No-ops if not yet bound to the service or no session.
     */
    fun reconnect() {
        val id = sessionId ?: return
        val now = System.currentTimeMillis()
        if (now - lastReconnectAt < RECONNECT_DEBOUNCE_MS) return
        lastReconnectAt = now
        boundApi?.forceReconnect(id, baseUrl)
    }

    /** POST /sessions/:id/cancel */
    fun cancel() {
        val id = sessionId ?: return
        viewModelScope.launch {
            runCatching { sessionApi.cancel(id) }
                .onFailure { _messages.tryEmit("Cancel failed: ${it.shortReason()}") }
        }
    }

    private fun Throwable.shortReason(): String =
        message?.take(120) ?: this::class.simpleName ?: "unknown error"

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // NOTE: do NOT call disconnect() on the WebSocket client here.
        // The client is owned by SessionSyncService (A3 advisor decision).
        // rc8: call stopObserving (ref-count aware) so the socket is closed
        // only when no other VM is still watching this session. SessionSyncService
        // also has a 30s idle fallback timeout as belt-and-suspenders.
        sessionId?.let { id -> boundApi?.stopObserving(id) }
        eventCollectJob?.cancel()
        eventCollectJob = null
        if (isBound) {
            runCatching { getApplication<Application>().unbindService(connection) }
            isBound = false
        }
        boundApi = null
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun startCollecting(api: SessionSyncApi) {
        val id = sessionId ?: return
        eventCollectJob?.cancel()
        eventCollectJob = viewModelScope.launch(Dispatchers.Default) {
            api.observeSession(id, baseUrl).collect { event ->
                _uiState.update { current -> current.applyEvent(event) }
                // rc10: cold-entry seed for the LazyColumn is gone — TerminalWebView
                // now owns render and fetches /display via fetchDisplayRaw on mount.
            }
        }
    }

    private fun clearMruSessionId(ctx: Context) {
        ctx.getSharedPreferences(PREFS_MRU, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_MRU_SESSION)
            .apply()
    }

    // ── State reducer ─────────────────────────────────────────────────────────

    private fun SessionUiState.applyEvent(event: AgentEvent): SessionUiState = when (event) {
        AgentEvent.Connected -> copy(connectionStatus = ConnectionStatus.CONNECTED)

        is AgentEvent.StateChange -> copy(
            processState = event.processState,
            promptType = event.promptType,
            choices = event.choices ?: emptyList()
        )

        is AgentEvent.Output -> {
            // rc10: terminal render is owned by TerminalWebView via /api/terminal
            // WS. We still use the last non-empty line for `lastLine` only —
            // skill-suggest bar + palette read it. No outputLines/partialLine
            // mutation (double-render with xterm was the rc9 stacked-status-bar bug).
            val last = event.text.lineSequence().filter { it.isNotBlank() }.lastOrNull()
            if (last != null) copy(lastLine = last) else this
        }

        is AgentEvent.ExecuteResult -> {
            // Flush any partial line first, then add the tool result.
            val flushedPartial = if (partialLine.isNotBlank()) {
                val pr = AnsiParser.parse(partialLine + "\n", DEFAULT_PALETTE, DEFAULT_FG, ansiStyleState)
                ansiStyleState = pr.styleState
                pr.lines.map { TaggedLine(it, classifyLine(it.text)) }
            } else emptyList()
            val flushed = outputLines + flushedPartial
            val resultParsed = AnsiParser.parse(event.output, DEFAULT_PALETTE, DEFAULT_FG, AnsiParser.StyleState())
            val statusTag = if (event.success) "done" else event.status
            val durationSec = event.durationMs / 1000.0
            val prefix = "[$statusTag ${String.format(java.util.Locale.US, "%.1fs", durationSec)}] "
            val line = AnnotatedString(prefix + resultParsed.lines.joinToString("\n") { it.text })
            val newLines = (flushed + TaggedLine(line, LineType.CONTENT)).takeLast(OUTPUT_LINE_CAP)
            copy(outputLines = newLines, partialLine = "", lastLine = line.text)
        }

        AgentEvent.Stale -> copy(connectionStatus = ConnectionStatus.STALE)

        is AgentEvent.Reconnecting -> copy(connectionStatus = ConnectionStatus.CONNECTING)

        is AgentEvent.RouteDecision -> {
            // Flush any partial line, then add the route marker.
            val flushedPartial = if (partialLine.isNotBlank()) {
                val pr = AnsiParser.parse(partialLine + "\n", DEFAULT_PALETTE, DEFAULT_FG, ansiStyleState)
                ansiStyleState = pr.styleState
                pr.lines.map { TaggedLine(it, classifyLine(it.text)) }
            } else emptyList()
            val flushed = outputLines + flushedPartial
            val line = AnnotatedString("[routed → ${event.provider}:${event.model}]")
            val newLines = (flushed + TaggedLine(line, LineType.METADATA)).takeLast(OUTPUT_LINE_CAP)
            copy(
                outputLines = newLines,
                partialLine = "",
                lastLine = line.text,
                lastRoute = RouteInfo(provider = event.provider, model = event.model)
            )
        }

        // Hook notifications are delivered to the notification channel by
        // SessionSyncService/HookNotifier; they do not mutate session UI state.
        is AgentEvent.HookNotification -> this
    }

    companion object {
        private const val TAG = "SessionViewModel"
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_BASE_URL = "baseUrl"
        // DEFAULT_SESSION_ID removed rc8 — cold-launch lands on Projects list.
        // Fallback base URL — overridden by NetworkConfig from DataStore settings.
        // Empty by default; users configure their server IP in the Settings tab.
        const val DEFAULT_BASE_URL = ""

        /** How many scrollback lines to retain before dropping oldest. */
        private const val OUTPUT_LINE_CAP = 500
        /** Min gap between user-triggered reconnects to swallow double-taps. */
        private const val RECONNECT_DEBOUNCE_MS = 2_000L

        // Fully-qualified class name of SessionSyncService in the :app module.
        // Kept as a string to avoid pulling :app as a compile dependency.
        private const val SERVICE_FQN =
            "com.octoally.app.service.SessionSyncService"

        // MRU session prefs — mirror the string values in :app/MruSession.kt
        // so the VM can clear a stale id on 404 without a compile dep on :app.
        private const val PREFS_MRU = "octoally_mru"
        private const val KEY_MRU_SESSION = "mru_session_id"

        // Default palette + fg for ANSI parsing (before theme is available in VM)
        private val DEFAULT_PALETTE = DefaultAnsiPalette
        private val DEFAULT_FG = Color(0xFFE4E8F1) // matches Default Dark textPrimary

        // ── Line classification ──────────────────────────────────────────
        // Patterns are checked AFTER ANSI stripping, on plain text only.
        // Intentionally conservative: mis-classifying content as metadata
        // hides real output. Only match well-known server prefixes.

        private val SPINNER_RE = Regex("^\\s*[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏⣾⣽⣻⢿⡿⣟⣯⣷◐◓◑◒◴◷◶◵⣀⣤⣶⣿●○◉◎⏳⌛▖▘▝▗]\\s")
        private val METADATA_RE = Regex(
            "^\\s*(?:" +
                "(?:model|context|tokens|permission|cost|cache|session|cwd|tip)[:\\s]" +
                "|╭─|╰─|│\\s" +           // box-drawing frames (welcome block)
                "|\\$\\s*\\d+\\.\\d+\\s" + // cost lines like "$ 0.03"
                "|>\\s*\\d+k?\\s+token" +  // token summaries
                ")",
            RegexOption.IGNORE_CASE
        )

        fun classifyLine(plainText: String): LineType = when {
            plainText.isBlank() -> LineType.CONTENT
            SPINNER_RE.containsMatchIn(plainText) -> LineType.SPINNER
            METADATA_RE.containsMatchIn(plainText) -> LineType.METADATA
            else -> LineType.CONTENT
        }
    }
}
