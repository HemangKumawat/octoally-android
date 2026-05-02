package com.octoally.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ProjectsDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
