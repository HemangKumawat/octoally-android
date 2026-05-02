package com.octoally.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.min
import kotlin.random.Random

/**
 * Owns one OkHttp WebSocket per session id.
 * Emits [AgentEvent] to a SharedFlow with replay=1.
 *
 * Lifecycle:
 *   call [connect] to open — idempotent if already open.
 *   call [disconnect] to tear down and cancel background jobs.
 */
class OctoAllyWebSocketClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val tokenProvider: TokenProvider
) {
    companion object {
        private val BACKOFF_STEPS = longArrayOf(500, 1000, 2000, 4000, 8000)
        private const val BACKOFF_CAP_MS = 15_000L
        private const val JITTER_FRACTION = 0.30
        private const val PING_INTERVAL_MS = 20_000L
        private const val PONG_TIMEOUT_MS = 10_000L
        private const val TAG = "OctoAllyWS"
    }

    private val _events = MutableSharedFlow<AgentEvent>(replay = 1)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pingSeq = AtomicInteger(0)
    private val lastPongAt = AtomicLong(0L)
    private val retryCount = AtomicInteger(0)
    private val firstFrameReceived = AtomicInteger(0)

    @Volatile private var activeSocket: WebSocket? = null
    @Volatile private var pingJob: Job? = null
    @Volatile private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun connect(sessionId: String, baseUrl: String) {
        scope.launch { openWebSocket(sessionId, baseUrl) }
        registerNetworkCallback(sessionId, baseUrl)
    }

    fun disconnect() {
        activeSocket?.close(1000, "client disconnect")
        activeSocket = null
        pingJob?.cancel()
        pingJob = null
        unregisterNetworkCallback()
        // Cancel children only — keep the scope alive so reconnect() works after a disconnect cycle.
        // Calling scope.cancel() here would permanently kill the SupervisorJob and silently no-op
        // all future connect() calls (Tailscale roam + reconnect would fail).
        scope.coroutineContext.job.cancelChildren()
        retryCount.set(0)
        firstFrameReceived.set(0)
    }

    /**
     * Bypass the current backoff and force an immediate reconnect.
     * Resets the retry counter so the next automatic reconnect cycle starts fresh.
     * Safe to call while already connected — the old socket is closed cleanly first.
     */
    fun forceReconnect(sessionId: String, baseUrl: String) {
        scope.launch {
            retryCount.set(0)
            firstFrameReceived.set(0)
            pingJob?.cancel()
            pingJob = null
            activeSocket?.close(1000, "user reconnect")
            openWebSocket(sessionId, baseUrl)
        }
    }

    // ── Internal WebSocket logic ──────────────────────────────────────────────

    private suspend fun openWebSocket(sessionId: String, baseUrl: String) {
        val token = tokenProvider.getToken()
        // Server (fastify) registers all routes with prefix '/api' and the
        // structured agent stream is '/sessions/:id/agent' — the earlier
        // '/sessions/:id/stream' path does not exist and caused immediate
        // close → Stale badge. See server/src/routes/agent.ts line 402.
        val wsUrl = baseUrl.replace(Regex("^http"), "ws") + "/api/sessions/$sessionId/agent"
        val builder = Request.Builder().url(wsUrl)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val request = builder.build()

        val listener = buildListener(sessionId, baseUrl)
        activeSocket?.cancel() // hard kill prevents onClosing race
        activeSocket = okHttpClient.newWebSocket(request, listener)
    }

    private fun buildListener(sessionId: String, baseUrl: String) = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WS connected: ${webSocket.request().url}")
            lastPongAt.set(System.currentTimeMillis())
            pingJob?.cancel()
            pingJob = scope.launch { pingLoop(webSocket) }
            scope.launch { _events.emit(AgentEvent.Connected) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (firstFrameReceived.compareAndSet(0, 1)) {
                retryCount.set(0) // reset backoff on first real frame
            }
            lastPongAt.set(System.currentTimeMillis())
            scope.launch { handleMessage(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WS failure: ${t.message}", t)
            scope.launch {
                _events.emit(AgentEvent.Stale)
                scheduleReconnect(sessionId, baseUrl)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WS closing: code=$code reason=$reason")
            webSocket.close(1000, null)
            if (code != 1000) {
                scope.launch {
                    _events.emit(AgentEvent.Stale)
                    scheduleReconnect(sessionId, baseUrl)
                }
            }
        }
    }

    private suspend fun handleMessage(text: String) {
        runCatching {
            val json = Json.parseToJsonElement(text).jsonObject
            // Frame-level session_id — server-side /api/sessions/:id/agent MAY
            // stamp this on every payload so the client can drop foreign frames
            // if the server ever multiplexes (defense-in-depth — SessionSyncService
            // enforces the actual guard).
            val frameSessionId = json["session_id"]?.jsonPrimitive?.contentOrNull
            when (json["type"]?.jsonPrimitive?.content) {
                "pong" -> lastPongAt.set(System.currentTimeMillis())
                "state_change" -> _events.emit(
                    AgentEvent.StateChange(
                        processState = json["process_state"]?.jsonPrimitive?.content ?: "unknown",
                        promptType = json["prompt_type"]?.jsonPrimitive?.contentOrNull,
                        choices = json["choices"]?.jsonArray?.map { it.jsonPrimitive.content },
                        sessionId = frameSessionId
                    )
                )
                "output" -> _events.emit(
                    AgentEvent.Output(
                        text = json["text"]?.jsonPrimitive?.content ?: "",
                        sessionId = frameSessionId
                    )
                )
                "execute_result" -> _events.emit(
                    AgentEvent.ExecuteResult(
                        status = json["status"]?.jsonPrimitive?.content ?: "unknown",
                        output = json["output"]?.jsonPrimitive?.content ?: "",
                        durationMs = json["durationMs"]?.jsonPrimitive?.long ?: 0L,
                        sessionId = frameSessionId
                    )
                )
                // Server-fired hook notifications. Server pass-3 will broadcast a frame
                // shaped like the REST payload from /hooks/notifications/fire:
                //   { type: "hook_notification", hook_type, session_id?, detail?, timestamp }
                "hook_notification" -> _events.emit(parseHookNotification(json))
                // Task #8 — model routing decision. Matches the web dashboard's
                // `route_decision` event contract (see dashboard/src/components/
                // ModelBadge.tsx). Also accepts the server-side `model_change`
                // hook type (see server/src/routes/hooks.ts) when emitted as a
                // top-level WebSocket frame.
                //
                // Payload shape (fields all optional, most-specific wins):
                //   { type: "route_decision",
                //     provider?: string,           // preferred provider field
                //     provider_backend?: string,   // dashboard fallback name
                //     route?: string,              // dashboard fallback name
                //     model?: string,
                //     reason?: string }
                "route_decision", "model_change" -> parseRouteDecision(json)?.let {
                    _events.emit(it)
                }
            }
        }
    }

    private fun parseHookNotification(json: JsonObject): AgentEvent.HookNotification {
        val hookType = json["hook_type"]?.jsonPrimitive?.contentOrNull
            ?: json["notification_type"]?.jsonPrimitive?.contentOrNull
            ?: "unknown"
        val sessionId = json["session_id"]?.jsonPrimitive?.contentOrNull
        val detail = json["detail"]?.jsonObject
        // Explicit title/message override wins; otherwise derive from hookType + detail.
        val (defaultTitle, defaultBody, defaultSeverity) = deriveHookPresentation(hookType, detail)
        val title = detail?.get("title")?.jsonPrimitive?.contentOrNull
            ?: json["title"]?.jsonPrimitive?.contentOrNull
            ?: defaultTitle
        val message = detail?.get("message")?.jsonPrimitive?.contentOrNull
            ?: json["message"]?.jsonPrimitive?.contentOrNull
            ?: defaultBody
        val severity = detail?.get("severity")?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { AgentEvent.Severity.valueOf(it.uppercase()) }.getOrNull() }
            ?: defaultSeverity
        return AgentEvent.HookNotification(
            type = hookType,
            title = title,
            message = message,
            sessionId = sessionId,
            severity = severity
        )
    }

    private fun deriveHookPresentation(
        hookType: String,
        detail: JsonObject?
    ): Triple<String, String, AgentEvent.Severity> = when (hookType) {
        "session_complete" -> {
            val status = detail?.get("status")?.jsonPrimitive?.contentOrNull ?: "done"
            val exit = detail?.get("exitCode")?.jsonPrimitive?.intOrNull
            val sev = if (exit != null && exit != 0) AgentEvent.Severity.WARN else AgentEvent.Severity.INFO
            Triple("Session finished", "Status: $status" + (exit?.let { " (exit $it)" } ?: ""), sev)
        }
        "error_spike" -> {
            val reason = detail?.get("reason")?.jsonPrimitive?.contentOrNull ?: "errors rising"
            Triple("Error spike", reason, AgentEvent.Severity.ERROR)
        }
        "tool_denied" -> {
            val tool = detail?.get("tool")?.jsonPrimitive?.contentOrNull ?: "tool"
            val reason = detail?.get("reason")?.jsonPrimitive?.contentOrNull ?: "denied"
            Triple("Tool denied", "$tool — $reason", AgentEvent.Severity.WARN)
        }
        "rate_limit" -> Triple(
            "Rate limit",
            detail?.get("reason")?.jsonPrimitive?.contentOrNull ?: "API rate limit hit",
            AgentEvent.Severity.WARN
        )
        "budget_warn" -> Triple(
            "Budget warning",
            detail?.get("reason")?.jsonPrimitive?.contentOrNull ?: "Token budget threshold crossed",
            AgentEvent.Severity.WARN
        )
        "model_change" -> Triple(
            "Model changed",
            detail?.get("to")?.jsonPrimitive?.contentOrNull
                ?: detail?.get("reason")?.jsonPrimitive?.contentOrNull
                ?: "Routing updated",
            AgentEvent.Severity.INFO
        )
        else -> Triple("OctoAlly", hookType, AgentEvent.Severity.INFO)
    }

    /**
     * Parse a `route_decision` or `model_change` frame into an [AgentEvent.RouteDecision].
     *
     * Accepts three field-naming styles (aligned with the web dashboard's
     * ModelBadge.tsx contract):
     *   1. Canonical: `provider`, `model`, `reason`
     *   2. Dashboard: `provider_backend` | `route`, `model`, `reason`
     *   3. Hook-style: `detail.to` (model), `detail.from` (reason fallback)
     *
     * Returns null when neither a provider-ish nor a model field is present —
     * matches the web's `parseRouteDecision` null-return semantics so we never
     * emit an empty badge.
     */
    private fun parseRouteDecision(json: JsonObject): AgentEvent.RouteDecision? {
        val detail = json["detail"]?.jsonObject
        val provider = json["provider"]?.jsonPrimitive?.contentOrNull
            ?: json["provider_backend"]?.jsonPrimitive?.contentOrNull
            ?: json["route"]?.jsonPrimitive?.contentOrNull
            ?: detail?.get("provider")?.jsonPrimitive?.contentOrNull
            ?: detail?.get("provider_backend")?.jsonPrimitive?.contentOrNull
            ?: detail?.get("route")?.jsonPrimitive?.contentOrNull
        val model = json["model"]?.jsonPrimitive?.contentOrNull
            ?: detail?.get("model")?.jsonPrimitive?.contentOrNull
            ?: detail?.get("to")?.jsonPrimitive?.contentOrNull
        if (provider.isNullOrBlank() && model.isNullOrBlank()) return null
        val reason = json["reason"]?.jsonPrimitive?.contentOrNull
            ?: detail?.get("reason")?.jsonPrimitive?.contentOrNull
            ?: detail?.get("from")?.jsonPrimitive?.contentOrNull
        return AgentEvent.RouteDecision(
            provider = provider ?: "unknown",
            model = model ?: provider ?: "unknown",
            reason = reason
        )
    }

    private suspend fun pingLoop(webSocket: WebSocket) {
        while (true) {
            delay(PING_INTERVAL_MS)
            val seq = pingSeq.incrementAndGet()
            webSocket.send("""{"type":"ping","seq":$seq}""")
            delay(PONG_TIMEOUT_MS)
            val elapsed = System.currentTimeMillis() - lastPongAt.get()
            if (elapsed > PONG_TIMEOUT_MS) {
                webSocket.cancel() // force close — onFailure will trigger reconnect
                break
            }
        }
    }

    private suspend fun scheduleReconnect(sessionId: String, baseUrl: String) {
        val attempt = retryCount.getAndIncrement()
        Log.d(TAG, "WS reconnect attempt=$attempt session=$sessionId")
        val baseDelay = if (attempt < BACKOFF_STEPS.size) {
            BACKOFF_STEPS[attempt]
        } else {
            BACKOFF_CAP_MS
        }
        val jitter = (baseDelay * JITTER_FRACTION * (Random.nextDouble() * 2 - 1)).toLong()
        val delayMs = min(baseDelay + jitter, BACKOFF_CAP_MS).coerceAtLeast(100L)
        // Signal the UI we're actively trying again — replaces the sticky
        // "Tap to retry" chip with "Connecting" until the socket opens or fails.
        _events.emit(AgentEvent.Reconnecting(attempt))
        delay(delayMs)
        firstFrameReceived.set(0)
        openWebSocket(sessionId, baseUrl)
    }

    // ── Network roam callback ─────────────────────────────────────────────────

    private fun registerNetworkCallback(sessionId: String, baseUrl: String) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Roam detected — hard-kill old socket to prevent dual-socket race
                // (onClosing on the old socket would otherwise trigger scheduleReconnect
                // producing two sockets emitting to the same SharedFlow).
                pingJob?.cancel()
                pingJob = null
                activeSocket?.cancel() // hard kill, no close frame → no onClosing race
                retryCount.set(0)
                firstFrameReceived.set(0)
                scope.launch { openWebSocket(sessionId, baseUrl) }
            }
        }
        connectivityCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    private fun unregisterNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        connectivityCallback = null
    }
}
