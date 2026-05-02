package com.octoally.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    /** Insert or replace the project record keyed by cwd. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertByCwd(project: ProjectEntity)

    /** Observe all projects, ordered by pinned first then lastSeenAt descending. */
    @Query("""
        SELECT * FROM projects
        ORDER BY pinned DESC, lastSeenAt DESC
    """)
    fun observeAll(): Flow<List<ProjectEntity>>

    /**
     * Delete projects older than [days] days where pinned = false.
     * [nowMs] should be System.currentTimeMillis() from the call site.
     */
    @Query("""
        DELETE FROM projects
        WHERE pinned = 0
          AND lastSeenAt < :cutoffMs
    """)
    suspend fun deleteOlderThan(cutoffMs: Long)
}
