package com.octoally.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op bearer-token provider — returns null. Placeholder until DataStore-backed
 * auth is wired in. Present so Hilt can satisfy the TokenProvider binding for
 * OctoAllyWebSocketClient.
 */
@Singleton
class NoopTokenProvider @Inject constructor() : TokenProvider {
    override suspend fun getToken(): String? = null
    override fun observeToken(): Flow<String?> = flowOf(null)
}
