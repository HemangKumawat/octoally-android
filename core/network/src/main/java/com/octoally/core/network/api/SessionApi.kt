package com.octoally.core.network.api

import com.octoally.core.network.model.ExecuteRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Small public helper so feature modules can classify Retrofit failures without
 * a compile-time dependency on retrofit2 itself. Feature modules only pull
 * retrofit transitively via core-network's implementation scope.
 *
 * Returns the HTTP status code if [t] is a retrofit HttpException, else null.
 */
fun httpStatusOrNull(t: Throwable): Int? =
    if (t is retrofit2.HttpException) t.code() else null

/**
 * Retrofit binding for per-session REST endpoints on the main OctoAlly
 * server (port 42010). Replaces raw OkHttpClient calls in SessionViewModel
 * so that all HTTP traffic flows through the shared Retrofit instance
 * (consistent base URL, auth interceptor when wired, timeout config).
 */
interface SessionApi {
    /** `POST /api/sessions/:id/execute` — send input to the agent. */
    @POST("api/sessions/{sessionId}/execute")
    suspend fun execute(
        @Path("sessionId") sessionId: String,
        @Body request: ExecuteRequest
    ): JsonObject

    /** `POST /api/sessions/:id/cancel` — cancel the running agent turn. */
    @POST("api/sessions/{sessionId}/cancel")
    suspend fun cancel(@Path("sessionId") sessionId: String): JsonObject

    /** `GET /api/sessions/:id/display` — snapshot of current terminal output. */
    @GET("api/sessions/{sessionId}/display")
    suspend fun display(
        @Path("sessionId") sessionId: String,
        @Query("lines") lines: Int = 200
    ): JsonObject

    /**
     * `GET /api/sessions/:id` — metadata for a single session.
     * Used to validate an MRU session id on cold-launch before wiring the WS.
     * Retrofit throws `retrofit2.HttpException` on non-2xx; callers should
     * inspect `exception.code()` for 404 (session cancelled/deleted → clear MRU).
     */
    @GET("api/sessions/{sessionId}")
    suspend fun get(@Path("sessionId") sessionId: String): JsonObject

    /**
     * `POST /api/sessions/:id/reconnect` — reattach a detached tmux session so
     * the WebSocket route `/api/sessions/:id/agent` starts serving output.
     *
     * Server semantics (session-manager.ts:1027-1033):
     *   - 200 + { ok, session } only when session.status == "detached"
     *     AND the tmux/dtach socket is still alive.
     *   - 404 for active, running, completed, or unknown sessions.
     * Callers MUST gate on status == "detached" from [get] before calling.
     */
    @POST("api/sessions/{sessionId}/reconnect")
    suspend fun reconnect(@Path("sessionId") sessionId: String): JsonObject
}
