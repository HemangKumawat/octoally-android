package com.octoally.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitStatusResponse(
    val branch: String = "",
    val files: List<GitFileDto> = emptyList(),
    val ahead: Int = 0,
    val behind: Int = 0,
)

/**
 * Git file status — server returns short-format `x` (index) and `y` (worktree).
 * Derived properties provide the human-readable status and staged flag.
 */
@Serializable
data class GitFileDto(
    val path: String,
    val x: String = " ",
    val y: String = " ",
) {
    /** Whether the file has changes in the staging area. */
    val staged: Boolean get() = x != "?" && x != " "

    /** Human-readable status derived from the most relevant column. */
    val status: String get() = when {
        x == "?" && y == "?" -> "added"       // untracked
        x == "A" || y == "A" -> "added"
        x == "D" || y == "D" -> "deleted"
        x == "R" || y == "R" -> "renamed"
        x == "M" || y == "M" -> "modified"
        else -> "modified"
    }
}

@Serializable
data class GitFileRequest(
    val path: String,
    val files: List<String>,
)

@Serializable
data class GitCommitRequest(
    val path: String,
    val message: String,
)

@Serializable
data class GitProjectRequest(
    val path: String,
)

@Serializable
data class GitOpResponse(
    val ok: Boolean,
    val message: String? = null,
)

@Serializable
data class GitDiffResponse(
    val diff: String = "",
)
