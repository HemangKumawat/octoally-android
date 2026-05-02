package com.octoally.core.data.skills

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillFrequencyDao {

    @Query("SELECT * FROM skill_frequency WHERE projectId = :projectId")
    fun observeAll(projectId: String): Flow<List<SkillFrequencyEntity>>

    /**
     * Upsert count+1 and lastUsedAt = ts. We rely on ON CONFLICT DO UPDATE so this
     * is atomic in a single SQL statement; no @Transaction + read-modify-write needed.
     */
    @Query(
        """
        INSERT INTO skill_frequency (projectId, skillName, count, lastUsedAt)
        VALUES (:projectId, :skillName, 1, :ts)
        ON CONFLICT(projectId, skillName) DO UPDATE SET
            count = count + 1,
            lastUsedAt = :ts
        """
    )
    suspend fun increment(projectId: String, skillName: String, ts: Long)
}
