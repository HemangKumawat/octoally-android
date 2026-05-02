package com.octoally.core.network.model

import kotlinx.serialization.Serializable

/**
 * JSON body for `POST /upload` on the clipboard-bridge sidecar (port 7799).
 *
 * Shape from `dashboard/public/assets/clipboard-bridge.js`:
 *   { name: string, data: string }   // `data` is a base64 data URL
 *
 * The server responds with { path: string }. This is NOT multipart.
 */
@Serializable
data class UploadRequest(
    val name: String,
    val data: String,
)

/**
 * Response from `POST /upload`.
 */
@Serializable
data class UploadResponse(
    val path: String,
)
