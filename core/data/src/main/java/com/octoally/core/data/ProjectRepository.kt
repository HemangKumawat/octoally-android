package com.octoally.core.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val dao: ProjectDao
) {
    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    suspend fun upsert(project: ProjectEntity) = dao.upsertByCwd(project)
}
