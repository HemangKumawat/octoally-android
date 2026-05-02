package com.octoally.core.data.skills

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecentSkillDao {

    @Query(
        """
        SELECT * FROM recent_skills
        WHERE projectId = :projectId
        ORDER BY usedAt DESC
        LIMIT :limit
        """
    )
    abstract fun observeRecent(projectId: String, limit: Int = 10): Flow<List<RecentSkillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: RecentSkillEntity)

    @Query(
        """
        DELETE FROM recent_skills
        WHERE projectId = :projectId
          AND skillName NOT IN (
              SELECT skillName FROM recent_skills
              WHERE projectId = :projectId
              ORDER BY usedAt DESC
              LIMIT :limit
          )
        """
    )
    abstract suspend fun pruneToLimit(projectId: String, limit: Int)

    @Transaction
    open suspend fun touch(projectId: String, skillName: String, ts: Long, limit: Int = 10) {
        upsert(RecentSkillEntity(projectId = projectId, skillName = skillName, usedAt = ts))
        pruneToLimit(projectId, limit)
    }

    @Query("DELETE FROM recent_skills WHERE projectId = :projectId")
    abstract suspend fun clear(projectId: String)
}
