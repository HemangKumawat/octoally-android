package com.octoally.app

import com.octoally.core.data.SettingsRepository
import com.octoally.core.network.DefaultNetworkConfig
import com.octoally.core.network.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides [NetworkConfig] backed by [SettingsRepository] (DataStore).
 *
 * On first launch the DataStore is empty and falls back to the default
 * Tailscale IP. The user can change the host/port via the Settings screen;
 * changes take effect after an app restart (Retrofit is a singleton).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(settings: SettingsRepository): NetworkConfig =
        DefaultNetworkConfig(
            mainHost = settings.readHostBlocking(),
            mainPort = settings.readMainPortBlocking(),
            uploadPort = settings.readUploadPortBlocking(),
        )
}
