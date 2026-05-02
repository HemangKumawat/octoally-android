package com.octoally.core.data.skills

import androidx.room.Entity

/**
 * A user-pinned skill for a given project.
 *
 * Pins are ordered by [pinnedAt] descending (most recent pin first) in the UI.
 */
@Entity(
    tableName = "pinned_skills",
    primaryKeys = ["projectId", "skillName"]
)
data class PinnedSkillEntity(
    val projectId: String,
    val skillName: String,
    val pinnedAt: Long
)
