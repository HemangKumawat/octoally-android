package com.octoally.core.data.skills

import android.content.Context
import androidx.room.Room
import com.octoally.core.data.ProjectDao
import com.octoally.core.data.ProjectsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOctoAllyDatabase(@ApplicationContext ctx: Context): OctoAllyDatabase =
        Room.databaseBuilder(ctx, OctoAllyDatabase::class.java, "octoally.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePinnedSkillDao(db: OctoAllyDatabase): PinnedSkillDao = db.pinnedSkillDao()

    @Provides
    fun provideRecentSkillDao(db: OctoAllyDatabase): RecentSkillDao = db.recentSkillDao()

    @Provides
    fun provideSkillFrequencyDao(db: OctoAllyDatabase): SkillFrequencyDao = db.skillFrequencyDao()

    @Provides
    @Singleton
    fun provideProjectsDatabase(@ApplicationContext ctx: Context): ProjectsDatabase =
        Room.databaseBuilder(ctx, ProjectsDatabase::class.java, "projects.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProjectDao(db: ProjectsDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideClock(): () -> Long = { System.currentTimeMillis() }
}
