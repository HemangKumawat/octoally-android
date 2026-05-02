package com.octoally.core.network.di

import com.octoally.core.network.DefaultNetworkConfig
import com.octoally.core.network.NetworkConfig
import com.octoally.core.network.NoopTokenProvider
import com.octoally.core.network.TokenProvider
import com.octoally.core.network.api.FilesApi
import com.octoally.core.network.api.GitApi
import com.octoally.core.network.api.ProjectsApi
import com.octoally.core.network.api.SessionApi
import com.octoally.core.network.api.SkillsApi
import com.octoally.core.network.api.SystemApi
import com.octoally.core.network.api.UploadApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt bindings for the HTTP layer.
 *
 * Provides a single shared [OkHttpClient] plus two Retrofit instances —
 * one pointed at the main OctoAlly server (42010) and one at the clipboard-
 * bridge upload sidecar (7799). All three API interfaces are derived from
 * those two Retrofit instances.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainServer
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class UploadServer

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS) // keeps WebSocket alive on Tailscale/mobile
        .build()

    @Provides
    @Singleton
    @MainServer
    fun provideMainRetrofit(
        client: OkHttpClient,
        json: Json,
        config: NetworkConfig,
    ): Retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(config.mainBaseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @UploadServer
    fun provideUploadRetrofit(
        client: OkHttpClient,
        json: Json,
        config: NetworkConfig,
    ): Retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(config.uploadBaseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideSkillsApi(@MainServer retrofit: Retrofit): SkillsApi =
        retrofit.create(SkillsApi::class.java)

    @Provides
    @Singleton
    fun provideSystemApi(@MainServer retrofit: Retrofit): SystemApi =
        retrofit.create(SystemApi::class.java)

    @Provides
    @Singleton
    fun provideUploadApi(@UploadServer retrofit: Retrofit): UploadApi =
        retrofit.create(UploadApi::class.java)

    @Provides
    @Singleton
    fun provideProjectsApi(@MainServer retrofit: Retrofit): ProjectsApi =
        retrofit.create(ProjectsApi::class.java)

    @Provides
    @Singleton
    fun provideGitApi(@MainServer retrofit: Retrofit): GitApi =
        retrofit.create(GitApi::class.java)

    @Provides
    @Singleton
    fun provideFilesApi(@MainServer retrofit: Retrofit): FilesApi =
        retrofit.create(FilesApi::class.java)

    @Provides
    @Singleton
    fun provideSessionApi(@MainServer retrofit: Retrofit): SessionApi =
        retrofit.create(SessionApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenModule {
    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: NoopTokenProvider): TokenProvider
}
