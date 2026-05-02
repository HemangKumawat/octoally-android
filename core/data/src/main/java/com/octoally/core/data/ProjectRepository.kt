package com.octoally.core.data

import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val dao: ProjectDao
) {
    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    suspend fun upsert(project: ProjectEntity) = dao.upsertByCwd(project)

    /** Prune unpinned records older than [days] days. */
    suspend fun pruneOld(days: Int = 30) {
        val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        dao.deleteOlderThan(cutoffMs)
    }
}
