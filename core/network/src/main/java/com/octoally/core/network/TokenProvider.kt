package com.octoally.core.network

import kotlinx.coroutines.flow.Flow

/** Abstraction that supplies a bearer token from DataStore. */
interface TokenProvider {
    /** Returns the current token as a one-shot suspend call. Null if unauthenticated. */
    suspend fun getToken(): String?

    /** Observable stream — emits whenever the stored token changes. */
    fun observeToken(): Flow<String?>
}
