package com.octoally.core.network.api

import com.octoally.core.network.model.SystemStatus
import retrofit2.http.GET

/**
 * Retrofit binding for the system-status endpoint on the main OctoAlly server
 * (port 42010). The route is mounted under `/api`.
 */
interface SystemApi {
    /** `GET /api/system/status` — features + integrations health summary. */
    @GET("api/system/status")
    suspend fun status(): SystemStatus
}
