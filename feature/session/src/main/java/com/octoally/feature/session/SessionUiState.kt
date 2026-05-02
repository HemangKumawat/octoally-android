package com.octoally.feature.session

import androidx.compose.ui.text.AnnotatedString
import java.util.concurrent.atomic.AtomicLong

/** Connection status for the top-bar chip. */
enum class ConnectionStatus { CONNECTING, CONNECTED, STALE, DISCONNECTED }

private val lineIdSeq = AtomicLong(0L)
/** Monotonic id generator for [TaggedLine]. Stable across reducer calls so
 *  LazyColumn can use `key = { it.id }` and only re-compose appended rows. */
internal fun nextLineId(): Long = lineIdSeq.incrementAndGet()

/**
 * Latest model routing decision, surfaced as a coloured pill in the session UI.
 *
 * Mirrors the web dashboard's `RouteDecisionData` minimally — only the two
 * fields the mobile UI actually renders are tracked. The provider drives the
 * colour mapping; the model drives the label text.
 */
data class RouteInfo(val provider: String, val model: String)

/** Classification of a terminal output line for rendering decisions. */
enum class LineType {
    /** Normal agent response content. */
    CONTENT,
    /** Spinner / progress indicator (e.g. ⠋ Thinking…). Deduped: replaces previous spinner. */
    SPINNER,
    /** Metadata: model info, context tokens, permission headers, etc. Collapsible. */
    METADATA,
}

/** A terminal output line tagged with its [LineType] for smart rendering.
 *  [id] is a stable monotonic identity used as the LazyColumn key — do NOT
 *  derive it from text/type (content duplicates must still get distinct ids). */
data class TaggedLine(
    val text: AnnotatedString,
    val type: LineType,
    val id: Long = nextLineId(),
)

/**
 * Full UI state for the session screen.
 *
 * @param processState  e.g. "idle", "busy", "waiting_for_input"
 * @param promptType    e.g. "text", "confirmation", "choice", null
 * @param choices       populated when promptType == "choice"
 * @param lastLine      latest output line rendered in the terminal
 * @param connectionStatus top-bar chip colour/label
 * @param outputLines   full scrollback buffer with line classification
 * @param noSession     true when no session is selected (cold launch / deleted MRU).
 *                      Renders the empty-state screen instead of the terminal.
 */
data class SessionUiState(
    val processState: String = "idle",
    val promptType: String? = null,
    val choices: List<String> = emptyList(),
    val lastLine: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
    val outputLines: List<TaggedLine> = emptyList(),
    /** Incomplete line being streamed — not yet terminated by \n. */
    val partialLine: String = "",
    /** Latest model route decision; null until the first route_decision event arrives. */
    val lastRoute: RouteInfo? = null,
    val noSession: Boolean = false
)
