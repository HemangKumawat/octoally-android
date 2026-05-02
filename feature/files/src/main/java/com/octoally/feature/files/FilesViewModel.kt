package com.octoally.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.network.api.FilesApi
import com.octoally.core.network.model.FileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state types ────────────────────────────────────────────────────────────

data class FilesUiState(
    val currentPath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class FileContentState(
    val path: String,
    val content: String,
    val language: String?,
    val loading: Boolean = false,
    val error: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Drives both the file explorer list and the file viewer.
 *
 * Call [setProjectPath] once from the host composable before observing [state].
 * Navigation within the project is handled by [navigate] and [goUp]; file reads
 * are triggered by [readFile] and observed via [fileContent].
 */
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val filesApi: FilesApi,
) : ViewModel() {

    /** Mutable backing state for the directory listing. */
    private val _currentPath = MutableStateFlow("")
    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /** Set by the host composable; drives all subsequent API calls. */
    var projectPath: String = ""
        private set

    val state: StateFlow<FilesUiState> = combine(
        _currentPath,
        _entries,
        _loading,
        _error,
    ) { path, entries, loading, error ->
        FilesUiState(
            currentPath = path,
            entries = entries,
            loading = loading,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = FilesUiState(loading = true),
    )

    /** File content stream — null when no file is open. */
    val fileContent = MutableStateFlow<FileContentState?>(null)

    // ── Public API ────────────────────────────────────────────────────────────

    /** Must be called by the host screen before [state] is useful. */
    fun setProjectPath(path: String) {
        if (projectPath == path) return
        projectPath = path
        loadDirectory("")
    }

    /** Navigate into [path] (relative to project root). */
    fun navigate(path: String) {
        loadDirectory(path)
    }

    /**
     * Navigate to the parent of the current directory.
     * No-op when already at root.
     */
    fun goUp() {
        val current = _currentPath.value
        if (current.isBlank()) return
        val parent = current.substringBeforeLast('/', "")
        loadDirectory(parent)
    }

    /** Fetch [path]'s content and push it into [fileContent]. */
    fun readFile(path: String) {
        sanitizePath(path) ?: run {
            fileContent.value = FileContentState(path = path, content = "", language = null, error = "Invalid path")
            return
        }
        fileContent.value = FileContentState(
            path = path,
            content = "",
            language = null,
            loading = true,
        )
        viewModelScope.launch {
            val fullPath = if (path.startsWith("/")) path else "$projectPath/$path"
            runCatching { filesApi.read(fullPath) }
                .onSuccess { resp ->
                    fileContent.value = FileContentState(
                        path = resp.path.ifBlank { path },
                        content = resp.content,
                        language = resp.language,
                        loading = false,
                    )
                }
                .onFailure { t ->
                    fileContent.value = FileContentState(
                        path = path,
                        content = "",
                        language = null,
                        loading = false,
                        error = t.message ?: "Failed to read file",
                    )
                }
        }
    }

    /** Clear the open file so the viewer can be dismissed. */
    fun closeFile() {
        fileContent.value = null
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Reject paths containing `..` segments to prevent directory traversal. */
    private fun sanitizePath(path: String): String? {
        if (path.split("/").any { it == ".." }) return null
        return path
    }

    private fun loadDirectory(path: String) {
        sanitizePath(path) ?: run {
            _error.value = "Invalid path"
            _loading.value = false
            return
        }
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val fullPath = if (path.isBlank()) projectPath else "$projectPath/$path"
            runCatching { filesApi.list(fullPath) }
                .onSuccess { resp ->
                    _currentPath.value = resp.path
                    _entries.value = sortEntries(resp.files)
                }
                .onFailure { t ->
                    _error.value = t.message ?: "Failed to load directory"
                    _entries.value = emptyList()
                }
            _loading.value = false
        }
    }

    /**
     * Sort order: directories first (alphabetical), then files (alphabetical).
     * Case-insensitive for both tiers.
     */
    private fun sortEntries(raw: List<FileEntry>): List<FileEntry> {
        val (dirs, files) = raw.partition { it.type == "directory" }
        return dirs.sortedBy { it.name.lowercase() } + files.sortedBy { it.name.lowercase() }
    }
}
