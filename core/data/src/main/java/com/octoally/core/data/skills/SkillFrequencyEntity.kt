package com.octoally.core.data.skills

import androidx.room.Entity

/**
 * Rolling frequency counter for a skill within a project. Feeds the
 * weighted suggestion ranking (freq * exp(-ln2 * ageHours / 12h)) used by
 * [SkillUsageRepository.rankedSuggestions].
 */
@Entity(
    tableName = "skill_frequency",
    primaryKeys = ["projectId", "skillName"]
)
data class SkillFrequencyEntity(
    val projectId: String,
    val skillName: String,
    val count: Int,
    val lastUsedAt: Long
)
