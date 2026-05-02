package com.octoally.core.network.api

import com.octoally.core.network.model.FileContentResponse
import com.octoally.core.network.model.FileListResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit binding for the file-browser endpoints on the main OctoAlly server.
 * Routes are mounted under `/api/files`.
 */
interface FilesApi {
    /** `GET /api/files?path=…` — list directory contents. */
    @GET("api/files")
    suspend fun list(
        @Query("path") path: String,
    ): FileListResponse

    /** `GET /api/files/read?path=…` — read file contents. */
    @GET("api/files/read")
    suspend fun read(
        @Query("path") path: String,
    ): FileContentResponse
}
