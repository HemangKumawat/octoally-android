package com.octoally.feature.session.ui

// rc10 TerminalWebView — Hybrid xterm.js WebView render surface.
// rc14 — wrapped in FrameLayout(MATCH_PARENT) to fix the rc10/11/12 black-body
//        bug. WebView extends AbsoluteLayout and ignores Compose fillMaxSize.
// rc16 — three additions:
//   1. Process-singleton TerminalWebViewPool: WebView + WebSocket persist across
//      session swaps. Swapping sessionId is an O(1) detach+attach instead of a
//      full Chromium init + seed fetch + WS reconnect (~3-7s -> <100ms).
//   2. Pinch-zoom gesture detector wrapped around the AndroidView; injects
//      window.__octo.setFontSize(N) on each pinch-to-zoom delta (debounced).
//   3. terminal.html owns single-finger touch-scroll directly via xterm
//      term.scrollLines (rc15 baseline).

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

private const val TAG = "TerminalWebView"

/**
 * Process-singleton pool of TerminalHolder. Keyed by sessionId. Each entry
 * owns its own WebView (with a live xterm.js + WebSocket). On session swap
 * the previous FrameLayout detaches its WebView (kept alive in the pool)
 * and the new FrameLayout attaches the next session's WebView.
 *
 * TTL: idle entries are evicted in LRU order when [POOL_MAX] is exceeded.
 *
 * rc16 trade-off: holds up to [POOL_MAX] WebViews + WS connections in
 * memory simultaneously. Each WebView is ~20-50MB; OkHttp WS is cheap.
 * For [POOL_MAX]=4 that's ~80-200MB peak — acceptable on a 12GB phone.
 */
private object TerminalWebViewPool {
    private const val POOL_MAX = 4
    private val entries = LinkedHashMap<String, TerminalHolder>()  // LRU iteration order

    @Synchronized
    fun acquire(sessionId: String): TerminalHolder {
        val existing = entries.remove(sessionId)
        if (existing != null) {
            entries[sessionId] = existing  // move to MRU end
            return existing
        }
        val fresh = TerminalHolder()
        entries[sessionId] = fresh
        evictIfNeeded()
        return fresh
    }

    @Synchronized
    fun release(sessionId: String) {
        entries[sessionId]?.markIdle()
    }

    private fun evictIfNeeded() {
        while (entries.size > POOL_MAX) {
            val it = entries.entries.iterator()
            val oldest = it.next()
            it.remove()
            oldest.value.shutdown()
            Log.i(TAG, "pool evicted sessionId=${oldest.key} (size=${entries.size})")
        }
    }
}

/**
 * Compose wrapper around a WebView hosting xterm.js for the OctoAlly
 * session render surface.
 *
 * rc16 — wraps the AndroidView in a Box that captures multi-finger transform
 * gestures (pinch zoom) and forwards them to xterm.js via window.__octo.setFontSize.
 * Single-finger touch-scroll is handled inside terminal.html (rc15 baseline) so
 * we don't fight the WebView for swipe events on the body.
 */
@Composable
fun TerminalWebView(
    sessionId: String,
    baseUrl: String,
    fetchDisplay: suspend (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember(sessionId, baseUrl) { TerminalWebViewPool.acquire(sessionId) }

    // rc21 — find-in-scrollback bar (board 1.2). Self-contained in this
    // composable so SessionScreen wiring is untouched (scope-fence). NOT a
    // ModalBottomSheet — inline overlay, so the Material3 two-sheet crash
    // rule cannot be tripped.
    var searchOpen by remember(sessionId) { mutableStateOf(false) }
    var searchQuery by remember(sessionId) { mutableStateOf("") }

    DisposableEffect(sessionId, baseUrl) {
        // Bind the holder to this scope's fetchDisplay (so re-entry into the
        // same session uses the latest VM method binding). Idempotent.
        holder.bindSession(sessionId, baseUrl, scope, fetchDisplay)
        onDispose {
            // Detach the WebView from this FrameLayout but keep the holder
            // warm in the pool for next attach.
            holder.detachFromHost()
            TerminalWebViewPool.release(sessionId)
        }
    }

    Box(
        modifier = modifier.pointerInput(sessionId) {
            // rc19 — pinch-zoom must NOT consume single-finger drags, or
            // terminal.html's one-finger touch-scroll handler is starved
            // (symptom: only two-finger scroll worked). Engage AND consume
            // ONLY when 2+ pointers are down; single-finger pointer events
            // are left unconsumed so they fall through to the WebView/xterm.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            holder.applyZoom(zoom)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { c ->
                val host = FrameLayout(c).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    setBackgroundColor(0xFF0F1117.toInt())
                }
                holder.ensureWebView(c, baseUrl)
                holder.attachToHost(host)
                host
            },
            update = { host ->
                // Defensive: if the holder's WebView got detached (e.g. its
                // previous host was destroyed) and our host is empty, reattach.
                if (host.childCount == 0) {
                    holder.attachToHost(host)
                }
            },
        )

        // rc21 — search affordance. Collapsed = a single small icon
        // (top-right). Expanded = a compact find bar. Pure client-side
        // (xterm-addon-search over the 5000-line scrollback); no WS frame.
        if (!searchOpen) {
            IconButton(
                onClick = { searchOpen = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Find in terminal",
                    tint = Color(0xFF8B93A7),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xFF1A1D27))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.isEmpty()) holder.clearFind()
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Find", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { holder.find(searchQuery, forward = true) },
                    ),
                )
                IconButton(onClick = { holder.find(searchQuery, forward = false) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        tint = Color(0xFFE4E8F1),
                    )
                }
                IconButton(onClick = { holder.find(searchQuery, forward = true) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Next match",
                        tint = Color(0xFFE4E8F1),
                    )
                }
                IconButton(onClick = {
                    searchOpen = false
                    searchQuery = ""
                    holder.clearFind()
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close find",
                        tint = Color(0xFFE4E8F1),
                    )
                }
            }
        }
    }
}

/**
 * Holds WebView + WS state across recompositions AND across session swaps
 * via [TerminalWebViewPool]. Each holder is bound to ONE sessionId for life;
 * the pool dictates whether a new holder is created or an existing one
 * reused on next attach.
 */
private class TerminalHolder {
    private var webView: WebView? = null
    private var sessionId: String? = null
    private var baseUrl: String? = null
    private var scope: CoroutineScope? = null
    private var fetchDisplay: (suspend (Int) -> String?)? = null
    private var currentHost: FrameLayout? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private var seedJob: Job? = null

    private var pageFinished = false
    private var jsReady = false
    private var started = false
    private var cols = 80
    private var rows = 24

    /** rc16 — pinch-zoom font size state, integer 7..28. */
    private var fontSize = 10
    private var lastZoomEmitMs = 0L

    fun ensureWebView(ctx: Context, baseUrl: String) {
        if (webView != null) return
        this.baseUrl = baseUrl
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .build()
        val wv = WebView(ctx.applicationContext).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(0xFF0F1117.toInt())
            isFocusable = false
            isFocusableInTouchMode = false
            @SuppressLint("SetJavaScriptEnabled")
            run { settings.javaScriptEnabled = true }
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.blockNetworkImage = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            settings.textZoom = 100
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String?) {
                    onPageFinishedInternal()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    val msg = cm.message() ?: return true
                    when {
                        msg.startsWith("WV_READY") -> onJsReady(msg)
                        msg.startsWith("WV_FIT_ERR") -> Log.w(TAG, "[js] $msg")
                        msg.startsWith("WV_WRITE_ERR") -> Log.w(TAG, "[js] $msg")
                        msg.startsWith("WV_FONT_SIZE") -> Log.i(TAG, "[js] $msg")
                        else -> Log.d(TAG, "[js] $msg")
                    }
                    return true
                }
            }
        }
        webView = wv
        wv.loadUrl("https://appassets.androidplatform.net/assets/terminal.html")
    }

    /** Attach our WebView into [host]. Detaches from previous host first. */
    fun attachToHost(host: FrameLayout) {
        val wv = webView ?: return
        if (currentHost === host && wv.parent === host) return
        val parent = wv.parent as? ViewGroup
        parent?.removeView(wv)
        host.addView(wv)
        currentHost = host
    }

    fun detachFromHost() {
        val wv = webView ?: return
        val parent = wv.parent as? ViewGroup ?: return
        parent.removeView(wv)
        if (currentHost === parent) currentHost = null
    }

    fun markIdle() { /* hook for future TTL */ }

    private fun onPageFinishedInternal() {
        pageFinished = true
        maybeStart()
    }

    private fun onJsReady(msg: String) {
        msg.removePrefix("WV_READY ").split(' ').forEach { kv ->
            val parts = kv.split('=')
            if (parts.size == 2) {
                when (parts[0]) {
                    "cols" -> cols = parts[1].toIntOrNull() ?: cols
                    "rows" -> rows = parts[1].toIntOrNull() ?: rows
                }
            }
        }
        if (cols < 2) cols = 80
        if (rows < 2) rows = 24
        jsReady = true
        Log.i(TAG, "WV_READY cols=$cols rows=$rows")
        maybeStart()
    }

    fun bindSession(
        sessionId: String,
        baseUrl: String,
        scope: CoroutineScope,
        fetchDisplay: suspend (Int) -> String?,
    ) {
        this.sessionId = sessionId
        this.baseUrl = baseUrl
        this.scope = scope
        this.fetchDisplay = fetchDisplay
        maybeStart()
    }

    private fun maybeStart() {
        if (started) return
        if (!pageFinished || !jsReady) return
        val sid = sessionId ?: return
        val url = baseUrl ?: return
        val sc = scope ?: return
        started = true
        seedJob = sc.launch(Dispatchers.IO) {
            val raw = runCatching { fetchDisplay?.invoke(2000) }.getOrNull().orEmpty()
            if (raw.isNotEmpty()) {
                writeBytes(raw)
                Log.i(TAG, "seed written bytes=${raw.length}")
            }
            withContext(Dispatchers.Main) { connect(url, sid) }
        }
    }

    /** rc16 — gesture-driven zoom. Throttled to ~12fps via lastZoomEmitMs. */
    fun applyZoom(zoomDelta: Float) {
        val now = System.currentTimeMillis()
        if (now - lastZoomEmitMs < 80L) return  // ~12 fps cap
        lastZoomEmitMs = now
        val current = fontSize
        val next = if (zoomDelta > 1f) current + 1 else current - 1
        val clamped = max(7, min(28, next))
        if (clamped == current) return
        fontSize = clamped
        val wv = webView ?: return
        wv.post {
            wv.evaluateJavascript("window.__octo && window.__octo.setFontSize($clamped);", null)
        }
    }

    /**
     * rc21 — find-in-scrollback (board 1.2). Bridges to xterm-addon-search via
     * window.__octo.findNext / findPrev. Pure client, no socket/WS interaction.
     * JSONObject.quote produces a JS-safe string literal (same hardening as
     * injectSys — a query with quotes/line-terminators can't break out).
     */
    fun find(query: String, forward: Boolean) {
        val wv = webView ?: return
        if (query.isEmpty()) return
        val q = JSONObject.quote(query)
        val fn = if (forward) "findNext" else "findPrev"
        wv.post {
            wv.evaluateJavascript("window.__octo && window.__octo.$fn($q);", null)
        }
    }

    fun clearFind() {
        val wv = webView ?: return
        wv.post {
            wv.evaluateJavascript("window.__octo && window.__octo.clearSearch();", null)
        }
    }

    private fun connect(baseUrl: String, sessionId: String) {
        if (ws != null) return  // already connected — pool hit
        val wsUrl = baseUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
            .trimEnd('/') + "/api/terminal/$sessionId?attempt=0"
        val req = Request.Builder().url(wsUrl).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WS open $wsUrl")
                webSocket.send("""{"type":"resize","cols":$cols,"rows":$rows}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (msg.optString("type")) {
                    "output" -> {
                        val data = msg.optString("data")
                        if (data.isNotEmpty()) writeBytes(data)
                    }
                    "exit" -> {
                        val code = msg.optInt("exitCode", -1)
                        injectSys("\r\n[33m[Process exited with code $code][0m\r\n")
                    }
                    "error" -> {
                        val m = msg.optString("message", "")
                        injectSys("\r\n[31m[Error: $m][0m\r\n")
                    }
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                writeBytes(bytes.utf8())
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WS failure: ${t.message}")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closing $code $reason")
            }
        })
    }

    private fun writeBytes(raw: String) {
        val wv = webView ?: return
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        wv.post {
            wv.evaluateJavascript("window.__octo && window.__octo.write('$b64');", null)
        }
    }

    private fun injectSys(text: String) {
        val wv = webView ?: return
        // Use JSONObject.quote to produce a fully JS-safe quoted string literal
        // (handles all control chars, U+2028 / U+2029 line terminators, embedded
        // quotes, etc.). The manual escape table missed JS line terminators —
        // an attacker-controlled error message could break out of the JS string.
        val quoted = JSONObject.quote(text)
        wv.post {
            wv.evaluateJavascript("window.__octo && window.__octo.sys($quoted);", null)
        }
    }

    fun shutdown() {
        seedJob?.cancel()
        seedJob = null
        runCatching { ws?.close(1000, "dispose") }
        ws = null
        runCatching {
            (webView?.parent as? ViewGroup)?.removeView(webView)
            webView?.destroy()
        }
        webView = null
        currentHost = null
    }
}
