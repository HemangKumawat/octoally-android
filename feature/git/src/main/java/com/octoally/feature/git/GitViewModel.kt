package com.octoally.feature.git

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.network.api.GitApi
import com.octoally.core.network.model.GitCommitRequest
import com.octoally.core.network.model.GitFileDto
import com.octoally.core.network.model.GitFileRequest
import com.octoally.core.network.model.GitProjectRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GitUiState(
    val branch: String = "",
    val files: List<GitFileDto> = emptyList(),
    val ahead: Int = 0,
    val behind: Int = 0,
    val diff: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GitViewModel @Inject constructor(
    private val gitApi: GitApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectPath: String
        get() = savedStateHandle.get<String>("projectPath") ?: ""

    fun setProjectPath(path: String) {
        savedStateHandle["projectPath"] = path
    }

    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    fun loadStatus() {
        if (projectPath.isBlank()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { gitApi.status(projectPath) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            branch = response.branch,
                            files = response.files,
                            ahead = response.ahead,
                            behind = response.behind,
                            loading = false,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, error = err.message) }
                }
        }
    }

    fun stageFiles(files: List<String>) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { gitApi.stage(GitFileRequest(projectPath, files)) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
            loadStatus()
        }
    }

    fun unstageFiles(files: List<String>) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { gitApi.unstage(GitFileRequest(projectPath, files)) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
            loadStatus()
        }
    }

    fun commit(message: String) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { gitApi.commit(GitCommitRequest(projectPath, message)) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
            loadStatus()
        }
    }

    fun push() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { gitApi.push(GitProjectRequest(projectPath)) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
            loadStatus()
        }
    }

    fun pull() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { gitApi.pull(GitProjectRequest(projectPath)) }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
            loadStatus()
        }
    }

    fun loadDiff(file: String? = null) {
        if (projectPath.isBlank()) return
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { gitApi.diff(projectPath, file) }
                .onSuccess { response -> _state.update { it.copy(diff = response.diff) } }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }
        }
    }
}
