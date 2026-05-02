package com.octoally.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val path: String,
    val description: String? = null,
    val color: String? = null,
)

@Serializable
data class ProjectsResponse(
    val projects: List<ProjectDto> = emptyList()
)

@Serializable
data class SessionDto(
    val id: String,
    @SerialName("project_id") val projectId: String? = null,
    val task: String = "Terminal",
    val status: String = "unknown",
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionDto> = emptyList()
)

@Serializable
data class CreateSessionRequest(
    @SerialName("project_path") val projectPath: String,
    val task: String = "Terminal",
    val mode: String = "session",
    @SerialName("cli_type") val cliType: String = "claude",
)

@Serializable
data class CreateSessionResponse(
    val ok: Boolean,
    val session: SessionDto? = null,
)
