package com.octoally.core.network.api

import com.octoally.core.network.model.UploadRequest
import com.octoally.core.network.model.UploadResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit binding for the clipboard-bridge upload sidecar (port 7799).
 *
 * NOTE: despite the name "upload", this is a JSON endpoint that accepts a
 * base64 data URL, NOT a multipart form. Shape confirmed from
 * `dashboard/public/assets/clipboard-bridge.js`.
 */
interface UploadApi {
    /** `POST /upload` with `{name, data}` — returns `{path}`. */
    @POST("upload")
    suspend fun upload(@Body body: UploadRequest): UploadResponse
}
