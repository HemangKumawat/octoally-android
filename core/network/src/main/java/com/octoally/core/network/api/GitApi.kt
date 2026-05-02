package com.octoally.core.network.api

import com.octoally.core.network.model.GitCommitRequest
import com.octoally.core.network.model.GitDiffResponse
import com.octoally.core.network.model.GitFileRequest
import com.octoally.core.network.model.GitOpResponse
import com.octoally.core.network.model.GitProjectRequest
import com.octoally.core.network.model.GitStatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface GitApi {
    @GET("api/git/status")
    suspend fun status(@Query("path") projectPath: String): GitStatusResponse

    @POST("api/git/stage")
    suspend fun stage(@Body request: GitFileRequest): GitOpResponse

    @POST("api/git/unstage")
    suspend fun unstage(@Body request: GitFileRequest): GitOpResponse

    @POST("api/git/commit")
    suspend fun commit(@Body request: GitCommitRequest): GitOpResponse

    @POST("api/git/push")
    suspend fun push(@Body request: GitProjectRequest): GitOpResponse

    @POST("api/git/pull")
    suspend fun pull(@Body request: GitProjectRequest): GitOpResponse

    @GET("api/git/diff")
    suspend fun diff(
        @Query("path") projectPath: String,
        @Query("file") file: String? = null,
    ): GitDiffResponse
}
