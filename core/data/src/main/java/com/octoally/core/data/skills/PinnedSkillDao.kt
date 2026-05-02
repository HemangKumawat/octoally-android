package com.octoally.core.data.skills

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedSkillDao {

    @Query(
        """
        SELECT * FROM pinned_skills
        WHERE projectId = :projectId
        ORDER BY pinnedAt DESC
        """
    )
    fun observePinned(projectId: String): Flow<List<PinnedSkillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pin(entity: PinnedSkillEntity)

    @Query(
        """
        DELETE FROM pinned_skills
        WHERE projectId = :projectId AND skillName = :skillName
        """
    )
    suspend fun unpin(projectId: String, skillName: String)
}
