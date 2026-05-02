package com.octoally.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val cwd: String,
    val label: String,
    val pinned: Boolean,
    val lastSeenAt: Long,
    val createdAt: Long
)
