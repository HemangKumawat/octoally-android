package com.octoally.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A skill available on the OctoAlly server.
 *
 * Matches the server shape from `server/src/routes/skills.ts`:
 *   { id, name, category, command, scope, description? }
 */
@Serializable
data class Skill(
    val id: String,
    val name: String,
    val category: String,
    val command: String,
    val scope: String,
    val description: String? = null,
)

/**
 * Response from `GET /api/skills`.
 */
@Serializable
data class SkillsResponse(
    val skills: List<Skill>,
    val categories: List<String> = emptyList(),
    val total: Int = 0,
)

/**
 * One suggestion entry returned from the skill-suggest intent map.
 *
 * The server exposes `GET /api/skills/intents` which returns a pre-computed
 * intent map; ranking/filtering happens client-side.
 */
@Serializable
data class SkillIntent(
    val command: String,
    val name: String,
    val category: String,
    val description: String,
    val intents: List<String> = emptyList(),
    val weight: Double = 1.0,
)

/**
 * Response from `GET /api/skills/intents`.
 */
@Serializable
data class SkillIntentsResponse(
    @SerialName("intents") val intents: List<SkillIntent>,
    val total: Int = 0,
)
