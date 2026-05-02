package com.octoally.core.data.skills

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillUsageRepository @Inject constructor(
    private val pinnedDao: PinnedSkillDao,
    private val recentDao: RecentSkillDao,
    private val frequencyDao: SkillFrequencyDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Record a single use: touch the LRU list and bump the frequency counter. */
    suspend fun recordUse(projectId: String, skillName: String) {
        val ts = clock()
        recentDao.touch(projectId, skillName, ts)
        frequencyDao.increment(projectId, skillName, ts)
    }

    /**
     * Weighted suggestions scored at each emission using the current wall clock.
     * We do not tick reactively — scores are computed once per frequency-table
     * change, which is plenty responsive for a suggestion panel.
     *
     * Top [limit] by score, descending.
     */
    fun rankedSuggestions(projectId: String, limit: Int = 20): Flow<List<RankedSkill>> =
        frequencyDao.observeAll(projectId).map { rows ->
            val now = clock()
            rows.asSequence()
                .map { row ->
                    RankedSkill(
                        name = row.skillName,
                        score = SkillRanking.score(row.count, row.lastUsedAt, now),
                        count = row.count,
                        lastUsedAt = row.lastUsedAt
                    )
                }
                .sortedByDescending { it.score }
                .take(limit)
                .toList()
        }

    fun pinnedSkills(projectId: String): Flow<List<String>> =
        pinnedDao.observePinned(projectId).map { list -> list.map { it.skillName } }

    fun recentSkills(projectId: String, limit: Int = 10): Flow<List<String>> =
        recentDao.observeRecent(projectId, limit).map { list -> list.map { it.skillName } }

    suspend fun pin(projectId: String, skillName: String) {
        pinnedDao.pin(
            PinnedSkillEntity(
                projectId = projectId,
                skillName = skillName,
                pinnedAt = clock()
            )
        )
    }

    suspend fun unpin(projectId: String, skillName: String) {
        pinnedDao.unpin(projectId, skillName)
    }

    suspend fun clearRecent(projectId: String) = recentDao.clear(projectId)
}
