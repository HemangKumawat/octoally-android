package com.octoally.core.network.api

import com.octoally.core.network.model.SkillIntentsResponse
import com.octoally.core.network.model.SkillsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit bindings for the skills and skill-suggest endpoints on the main
 * OctoAlly server (port 42010). All routes are served under `/api` on the
 * server, so the base URL passed to Retrofit must NOT include `/api`.
 */
interface SkillsApi {
    /**
     * `GET /api/skills[?project_path=…]` — full catalog of global + project skills.
     */
    @GET("api/skills")
    suspend fun list(
        @Query("project_path") projectPath: String? = null,
    ): SkillsResponse

    /**
     * `GET /api/skills/intents[?project_path=…]` — pre-computed intent map that
     * the client uses to rank skill suggestions locally. The server does NOT
     * expose a `POST /skill-suggest` endpoint; ranking is client-side against
     * this map.
     */
    @GET("api/skills/intents")
    suspend fun intents(
        @Query("project_path") projectPath: String? = null,
    ): SkillIntentsResponse
}
