package com.octoally.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.octoally.app.notifications.HookNotifier
import com.octoally.core.network.AgentEvent
import com.octoally.core.network.OctoAllyWebSocketClient
import com.octoally.core.network.SessionSyncApi
import com.octoally.core.network.SessionSyncBinderApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider

/** Foreground service that keeps WebSocket clients alive beyond Activity lifecycle. */
@AndroidEntryPoint
class SessionSyncService : LifecycleService(), SessionSyncApi {

    companion object {
        private const val NOTIF_CHANNEL_ID = "octoally_sync"
        private const val NOTIF_ID = 1001
        // rc8: socket lifecycle now follows on-screen session (ref-counted).
        // This is the belt-and-suspenders fallback: if stopObserving never fires
        // (process death, crash), zombie sockets die within 30s.
        private const val IDLE_TIMEOUT_MS = 30 * 1000L
        // Grace window — if a VM stops observing and a new VM picks up the same
        // session within this period (rotation, rapid re-entry), the socket is
        // preserved instead of being torn down and rebuilt.
        private const val STOP_GRACE_MS = 1_500L
    }

    // Hilt injects a Provider so we can create multiple clients on demand.
    @Inject lateinit var wsClientProvider: Provider<OctoAllyWebSocketClient>

    @Inject lateinit var hookNotifier: HookNotifier

    inner class SyncBinder : Binder(), SessionSyncBinderApi {
        override fun getApi(): SessionSyncApi = this@SessionSyncService
    }

    private val binder = SyncBinder()

    // sessionId → (client, event relay, last-active timestamp, active-observer count)
    private data class SessionEntry(
        val client: OctoAllyWebSocketClient,
        val relay: MutableSharedFlow<AgentEvent>,
        var lastActiveAt: Long = System.currentTimeMillis(),
        var refCount: Int = 0
    )

    private val sessions = ConcurrentHashMap<String, SessionEntry>()
    private var idleWatchJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Alerts channel is created (and owned) by HookNotifier; create it here so it
        // exists before the first hook_notification frame arrives.
        hookNotifier.ensureChannel()
        // API 34+ (UPSIDE_DOWN_CAKE) requires an explicit foregroundServiceType argument —
        // otherwise the system throws MissingForegroundServiceTypeException and kills the service.
        // ServiceCompat handles the version split cleanly for API 29+.
        //
        // Wrap in try/catch: on API 31+, if the caller did not use
        // startForegroundService() (e.g. bindService-only path), startForeground
        // throws ForegroundServiceStartNotAllowedException. Letting this kill the
        // whole process masks the real error from users — instead log+stop the
        // service and let the Activity keep running (degraded, no background WS).
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(0),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } catch (t: Throwable) {
            android.util.Log.e("SessionSyncService", "startForeground failed", t)
            stopSelf()
            return
        }
        startIdleWatcher()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        idleWatchJob?.cancel()
        sessions.values.forEach { it.client.disconnect() }
        sessions.clear()
        super.onDestroy()
    }

    // ── Public API (via Binder) ───────────────────────────────────────────────

    /**
     * Register a session and return its event flow.
     * Idempotent — calling twice with same id returns existing flow.
     * Increments the ref-count so the socket can be torn down by [stopObserving]
     * once the last observer goes away.
     */
    override fun observeSession(sessionId: String, baseUrl: String): Flow<AgentEvent> {
        val entry = sessions.getOrPut(sessionId) {
            val client = wsClientProvider.get()
            val relay = MutableSharedFlow<AgentEvent>(replay = 1)
            val e = SessionEntry(client, relay)
            lifecycleScope.launch {
                client.events.collect { event ->
                    e.lastActiveAt = System.currentTimeMillis()
                    // Session-isolation guard: if the server stamps a session_id
                    // on a frame and it doesn't match this entry's sessionId,
                    // drop the event. Prevents cross-terminal output bleed when
                    // the server fans out to multiple sockets. Event types with
                    // no sessionId (e.g. Connected, Stale, Reconnecting) are
                    // always forwarded — they're connection-state signals for
                    // THIS socket.
                    val frameSessionId = event.sessionIdOrNull()
                    if (frameSessionId != null && frameSessionId != sessionId) {
                        android.util.Log.d(
                            "SessionSyncService",
                            "Drop foreign event ${event::class.simpleName} " +
                                "sessionId=$frameSessionId expected=$sessionId"
                        )
                        return@collect
                    }
                    // HookNotification is a fire-and-forget system notification — route
                    // it to HookNotifier and do NOT forward to the per-session UI relay
                    // (those events feed SessionScreen output streams).
                    if (event is AgentEvent.HookNotification) {
                        hookNotifier.notify(event)
                    } else {
                        relay.emit(event)
                    }
                }
            }
            client.connect(sessionId, baseUrl)
            e
        }
        entry.refCount++
        updateNotification()
        return entry.relay.asSharedFlow()
    }

    /**
     * Ref-counted counterpart of [observeSession]. Decrements the count; when
     * it reaches zero AND the session has been quiet for [STOP_GRACE_MS], the
     * WebSocket is closed and the entry removed.
     *
     * The grace window prevents tearing down and immediately rebuilding the
     * same socket during rapid nav churn (rotation, re-opening the same
     * session after briefly popping out).
     */
    override fun stopObserving(sessionId: String) {
        val entry = sessions[sessionId] ?: return
        entry.refCount = (entry.refCount - 1).coerceAtLeast(0)
        if (entry.refCount == 0) {
            // Schedule the actual teardown after the grace window. If another
            // observeSession() call comes in before then, refCount bumps back
            // up and the check below skips closing.
            lifecycleScope.launch {
                delay(STOP_GRACE_MS)
                val current = sessions[sessionId] ?: return@launch
                if (current.refCount == 0) {
                    current.client.disconnect()
                    sessions.remove(sessionId)
                    updateNotification()
                    if (sessions.isEmpty()) stopSelf()
                }
            }
        }
    }

    private fun AgentEvent.sessionIdOrNull(): String? = when (this) {
        is AgentEvent.Output -> sessionId
        is AgentEvent.StateChange -> sessionId
        is AgentEvent.ExecuteResult -> sessionId
        is AgentEvent.HookNotification -> sessionId
        AgentEvent.Connected, AgentEvent.Stale,
        is AgentEvent.Reconnecting, is AgentEvent.RouteDecision -> null
    }

    /** Bypass backoff and reconnect immediately. No-ops if session unknown. */
    override fun forceReconnect(sessionId: String, baseUrl: String) {
        sessions[sessionId]?.client?.forceReconnect(sessionId, baseUrl)
    }

    // ── Idle cleanup ──────────────────────────────────────────────────────────

    private fun startIdleWatcher() {
        idleWatchJob = lifecycleScope.launch {
            while (true) {
                delay(60_000L) // check every minute
                val now = System.currentTimeMillis()
                val stale = sessions.entries.filter { (_, v) ->
                    (now - v.lastActiveAt) > IDLE_TIMEOUT_MS
                }
                stale.forEach { (id, entry) ->
                    entry.client.disconnect()
                    sessions.remove(id)
                }
                updateNotification()
                if (sessions.isEmpty()) stopSelf()
            }
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "OctoAlly Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps active agent sessions alive" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(activeCount: Int): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("OctoAlly")
            .setContentText("$activeCount active session${if (activeCount != 1) "s" else ""}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotification() {
        val notif = buildNotification(sessions.size)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }
}
