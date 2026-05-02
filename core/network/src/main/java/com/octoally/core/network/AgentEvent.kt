package com.octoally.core.network

/** Sealed event hierarchy emitted by OctoAllyWebSocketClient to its SharedFlow. */
sealed class AgentEvent {
    /** Successfully opened the WebSocket connection. */
    object Connected : AgentEvent()

    /**
     * Agent process state changed.
     * @param processState e.g. "busy", "idle", "waiting_for_input"
     * @param promptType   e.g. "text", "confirmation", "choice", null
     * @param choices      non-null only when promptType == "choice"
     */
    data class StateChange(
        val processState: String,
        val promptType: String?,
        val choices: List<String>?,
        val sessionId: String? = null
    ) : AgentEvent()

    /** Streaming text output chunk from the agent. */
    data class Output(val text: String, val sessionId: String? = null) : AgentEvent()

    /**
     * Execute result returned from the server after a command/input submission.
     *
     * Maps to the server's `execute_result` WS frame which carries:
     *   `requestId`, `status` ("completed"|"timeout"|"pattern_matched"),
     *   `output`, `durationMs`, and `state`.
     *
     * @param status   server-side result status
     * @param output   clean rendered text
     * @param durationMs  execution duration in milliseconds
     */
    data class ExecuteResult(
        val status: String,
        val output: String,
        val durationMs: Long,
        val sessionId: String? = null
    ) : AgentEvent() {
        /** True when the command completed normally. */
        val success: Boolean get() = status == "completed"
    }

    /** Connection has gone stale — ping timed out or WebSocket closed unexpectedly. */
    object Stale : AgentEvent()

    /**
     * Client is in backoff between reconnect attempts. Emitted at the start of
     * each delay so the UI can show "Connecting…" instead of "Tap to retry"
     * while the socket is scheduled to reopen automatically.
     */
    data class Reconnecting(val attempt: Int) : AgentEvent()

    /**
     * Server-fired notification hook (session_complete, error_spike, tool_denied, ...).
     * Delivered fire-and-forget; SessionSyncService routes to HookNotifier instead of
     * forwarding to per-session UI relays.
     *
     * @param type     Server-side hook type string: "session_complete" | "error_spike"
     *                 | "tool_denied" | "rate_limit" | "budget_warn" | "model_change"
     * @param title    Human-readable heads-up title
     * @param message  Human-readable body
     * @param sessionId Originating session id, if any
     * @param severity UI severity — drives notification channel importance + vibration
     */
    data class HookNotification(
        val type: String,
        val title: String,
        val message: String,
        val sessionId: String? = null,
        val severity: Severity = Severity.INFO
    ) : AgentEvent()

    /** Severity bucket for [HookNotification]. */
    enum class Severity { INFO, WARN, ERROR }

    /**
     * Model routing decision for the current response.
     *
     * Matches the web dashboard's `route_decision` event contract
     * (see `dashboard/src/components/ModelBadge.tsx`). Also maps from the
     * server's `model_change` hook type (see `server/src/routes/hooks.ts`)
     * when that hook is forwarded over the session WebSocket.
     *
     * @param provider backend route label (e.g. "claude", "gemini_pro",
     *                 "deepthink", "haiku", "gemma") — fed to the colour
     *                 resolver in the UI badge
     * @param model    specific model identifier (e.g. "claude-3-5-sonnet",
     *                 "gemini-2.5-pro", "gemma-3-26b") shown on the pill
     * @param reason   optional human-readable justification for the route
     */
    data class RouteDecision(
        val provider: String,
        val model: String,
        val reason: String? = null
    ) : AgentEvent()
}
