package com.octoally.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One feature/integration entry returned from `/api/system/status`.
 * Shape from `server/src/routes/skills.ts`:
 *   { id, name, description?, enabled?, connected?, status, details? }
 *
 * The server uses `enabled` for features and `connected` for integrations —
 * both are optional here so one DTO works for both.
 */
@Serializable
data class SystemStatusItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val enabled: Boolean? = null,
    val connected: Boolean? = null,
    val status: String,
    val details: JsonElement? = null,
)

/**
 * Response from `GET /api/system/status`.
 */
@Serializable
data class SystemStatus(
    val features: List<SystemStatusItem> = emptyList(),
    val integrations: List<SystemStatusItem> = emptyList(),
)
