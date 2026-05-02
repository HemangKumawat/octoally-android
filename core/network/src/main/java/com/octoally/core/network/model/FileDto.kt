package com.octoally.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single file or directory entry returned by `GET /api/files`. */
@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    /** `"file"` or `"directory"` */
    val type: String,
    val size: Long = 0,
)

/** Response envelope for `GET /api/files`. */
@Serializable
data class FileListResponse(
    val files: List<FileEntry> = emptyList(),
    val path: String = "",
)

/** Response envelope for `GET /api/files/read`. */
@Serializable
data class FileContentResponse(
    val content: String = "",
    val path: String = "",
    /** Hint from the server about the language/syntax (e.g. `"kotlin"`, `"json"`). */
    val language: String? = null,
)
