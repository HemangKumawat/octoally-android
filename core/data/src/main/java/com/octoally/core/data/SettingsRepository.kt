package com.octoally.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("octoally_settings")

/**
 * Persists server connection settings via Jetpack DataStore.
 *
 * Provides both a [Flow] for reactive observation and a blocking
 * [readHostBlocking] for Hilt singleton initialization (Retrofit
 * needs the URL before composition starts).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store get() = context.settingsStore

    val host: Flow<String> = store.data.map { it[KEY_HOST] ?: DEFAULT_HOST }
    val mainPort: Flow<Int> = store.data.map { it[KEY_MAIN_PORT] ?: DEFAULT_MAIN_PORT }
    val uploadPort: Flow<Int> = store.data.map { it[KEY_UPLOAD_PORT] ?: DEFAULT_UPLOAD_PORT }
    val terminalFontSize: Flow<Int> = store.data.map { it[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE }

    /** Blocking read for Hilt provider — called once at app startup. */
    fun readHostBlocking(): String = runBlocking { host.first() }
    fun readMainPortBlocking(): Int = runBlocking { mainPort.first() }
    fun readUploadPortBlocking(): Int = runBlocking { uploadPort.first() }

    suspend fun saveConnection(host: String, mainPort: Int, uploadPort: Int) {
        store.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_MAIN_PORT] = mainPort
            prefs[KEY_UPLOAD_PORT] = uploadPort
        }
    }

    suspend fun saveTerminalFontSize(size: Int) {
        store.edit { prefs -> prefs[KEY_FONT_SIZE] = size }
    }

    companion object {
        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_MAIN_PORT = intPreferencesKey("server_main_port")
        private val KEY_UPLOAD_PORT = intPreferencesKey("server_upload_port")
        private val KEY_FONT_SIZE = intPreferencesKey("terminal_font_size")

        const val DEFAULT_HOST = ""
        const val DEFAULT_MAIN_PORT = 42010
        const val DEFAULT_UPLOAD_PORT = 7799
        const val DEFAULT_FONT_SIZE = 12
    }
}
