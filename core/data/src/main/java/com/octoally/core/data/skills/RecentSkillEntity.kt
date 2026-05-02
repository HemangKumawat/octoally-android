package com.octoally.core.data.skills

import androidx.room.Entity

/**
 * A recently-used skill for a given project. LRU cap (default 10) is enforced
 * by [RecentSkillDao.touch], which prunes the oldest rows after each insert.
 */
@Entity(
    tableName = "recent_skills",
    primaryKeys = ["projectId", "skillName"]
)
data class RecentSkillEntity(
    val projectId: String,
    val skillName: String,
    val usedAt: Long
)
