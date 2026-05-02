package com.octoally.core.network.api

import com.octoally.core.network.model.CreateSessionRequest
import com.octoally.core.network.model.CreateSessionResponse
import com.octoally.core.network.model.ProjectsResponse
import com.octoally.core.network.model.SessionsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit binding for project and session list endpoints on the main
 * OctoAlly server (port 42010). Routes are mounted under `/api`.
 */
interface ProjectsApi {
    /** `GET /api/projects` — all projects known to the server. */
    @GET("api/projects")
    suspend fun projects(): ProjectsResponse

    /** `GET /api/sessions` — all sessions (any status). */
    @GET("api/sessions")
    suspend fun sessions(): SessionsResponse

    /** `POST /api/sessions` — create a new session. */
    @POST("api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): CreateSessionResponse
}
