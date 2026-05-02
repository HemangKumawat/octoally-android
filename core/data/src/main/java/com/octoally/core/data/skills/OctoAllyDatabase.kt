package com.octoally.core.data.skills

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Database housing per-project skill UX state ported from the localStorage
 * layer of the OctoAlly web fork:
 *   - pinned skills (manual user pins)
 *   - recent skills (LRU, cap 10 per project, managed by [RecentSkillDao.touch])
 *   - skill frequency (counter + last-used timestamp, feeds weighted suggestions)
 *
 * Schema is exported to `core/data/schemas/` for migration review (see
 * `build.gradle.kts` -> `room.schemaLocation`).
 */
@Database(
    entities = [
        PinnedSkillEntity::class,
        RecentSkillEntity::class,
        SkillFrequencyEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OctoAllyDatabase : RoomDatabase() {
    abstract fun pinnedSkillDao(): PinnedSkillDao
    abstract fun recentSkillDao(): RecentSkillDao
    abstract fun skillFrequencyDao(): SkillFrequencyDao
}
