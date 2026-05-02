package com.octoally.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.data.SettingsRepository
import com.octoally.core.network.api.SystemApi
import com.octoally.core.ui.theme.OctoAllyTheme
import com.octoally.core.ui.theme.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val host: String = SettingsRepository.DEFAULT_HOST,
    val mainPort: Int = SettingsRepository.DEFAULT_MAIN_PORT,
    val uploadPort: Int = SettingsRepository.DEFAULT_UPLOAD_PORT,
    val themeId: String = "default",
    val terminalFontSize: Int = 12,
    val serverVersion: String = "",
    val connectionTestResult: ConnectionTestResult = ConnectionTestResult.Idle,
    val appVersion: String = "",
)

enum class ConnectionTestResult { Idle, Testing, Success, Failed }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val systemApi: SystemApi,
    private val themeController: ThemeController,
) : ViewModel() {

    private val _host = MutableStateFlow(SettingsRepository.DEFAULT_HOST)
    private val _mainPort = MutableStateFlow(SettingsRepository.DEFAULT_MAIN_PORT)
    private val _uploadPort = MutableStateFlow(SettingsRepository.DEFAULT_UPLOAD_PORT)
    private val _fontSize = MutableStateFlow(12)
    private val _serverVersion = MutableStateFlow("")
    private val _testResult = MutableStateFlow(ConnectionTestResult.Idle)
    private val _appVersion = MutableStateFlow("")

    val state: StateFlow<SettingsUiState> = combine(
        listOf(
            _host,
            _mainPort,
            _uploadPort,
            _fontSize,
            themeController.current,
            _serverVersion,
            _testResult,
            _appVersion,
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            host = values[0] as String,
            mainPort = values[1] as Int,
            uploadPort = values[2] as Int,
            terminalFontSize = values[3] as Int,
            themeId = (values[4] as OctoAllyTheme).id,
            serverVersion = values[5] as String,
            connectionTestResult = values[6] as ConnectionTestResult,
            appVersion = values[7] as String,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            settingsRepo.host.collect { _host.value = it }
        }
        viewModelScope.launch {
            settingsRepo.mainPort.collect { _mainPort.value = it }
        }
        viewModelScope.launch {
            settingsRepo.uploadPort.collect { _uploadPort.value = it }
        }
        viewModelScope.launch {
            settingsRepo.terminalFontSize.collect { _fontSize.value = it }
        }
        fetchServerVersion()
    }

    fun setAppVersion(version: String) {
        _appVersion.value = version
    }

    fun updateHost(value: String) { _host.value = value }
    fun updateMainPort(value: String) { _mainPort.value = value.toIntOrNull() ?: _mainPort.value }
    fun updateUploadPort(value: String) { _uploadPort.value = value.toIntOrNull() ?: _uploadPort.value }

    fun updateFontSize(size: Int) {
        _fontSize.value = size.coerceIn(8, 20)
        viewModelScope.launch { settingsRepo.saveTerminalFontSize(size.coerceIn(8, 20)) }
    }

    fun setTheme(theme: OctoAllyTheme) {
        viewModelScope.launch { themeController.setTheme(theme) }
    }

    fun saveConnection() {
        viewModelScope.launch {
            settingsRepo.saveConnection(_host.value, _mainPort.value, _uploadPort.value)
        }
    }

    fun testConnection() {
        _testResult.value = ConnectionTestResult.Testing
        viewModelScope.launch {
            runCatching { systemApi.status() }
                .onSuccess { _testResult.value = ConnectionTestResult.Success }
                .onFailure { _testResult.value = ConnectionTestResult.Failed }
        }
    }

    private fun fetchServerVersion() {
        viewModelScope.launch {
            runCatching { systemApi.status() }
                .onSuccess {
                    _serverVersion.value = "Connected"
                }
        }
    }
}
