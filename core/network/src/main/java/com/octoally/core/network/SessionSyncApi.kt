package com.octoally.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Typed contract exposed by SessionSyncService to feature modules.
 *
 * Lives in :core:network so :feature:session can reference it without
 * depending on :app (which owns the service implementation).
 *
 * Replaces the reflection-based binder cast in SessionViewModel —
 * compile-time checks, no Method.invoke, no silent failures.
 */
interface SessionSyncApi {
    /**
     * Register (or re-use) a session and return its event flow.
     * Idempotent — calling twice with the same id returns the same flow.
     * Each call increments an internal ref-count; pair with [stopObserving].
     */
    fun observeSession(sessionId: String, baseUrl: String): Flow<AgentEvent>

    /**
     * Decrement the ref-count for [sessionId]. When it hits zero and the
     * session has been quiet for a short grace window, the service closes
     * the underlying WebSocket. Used by VM lifecycle callbacks — it avoids
     * killing sockets across quick nav re-entries (e.g. rotation, re-opening
     * the same session).
     */
    fun stopObserving(sessionId: String)

    /**
     * Bypass backoff and reconnect immediately. No-ops if the session is unknown.
     * Emits [AgentEvent.Connected] through the existing event flow on success so
     * observers see the status flip without needing to re-subscribe.
     */
    fun forceReconnect(sessionId: String, baseUrl: String)
}

/**
 * Binder contract. The service's Binder implements this so the
 * ViewModel can cast IBinder -> SessionSyncBinderApi and call
 * [getApi] to get the typed service reference.
 */
interface SessionSyncBinderApi {
    fun getApi(): SessionSyncApi
}
