package com.octoally.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body for POST /sessions/:id/execute. */
@Serializable
data class ExecuteRequest(
    val input: String,
    @SerialName("wait_for") val waitFor: String? = null
)
