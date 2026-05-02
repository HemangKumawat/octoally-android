package com.octoally.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.data.ProjectEntity
import com.octoally.core.data.ProjectRepository
import com.octoally.core.network.api.ProjectsApi
import com.octoally.core.network.model.CreateSessionRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Surfaces all projects + their live sessions in the drawer rail.
 *
 * On init we fetch `/api/projects` and `/api/sessions` from the server, upsert
 * projects into Room so they persist offline, then surface sessions grouped
 * under each project. Only "running" sessions are shown — completed/failed ones
 * clutter the drawer without providing actionable value.
 *
 * The Room flow drives the project list (offline-capable); sessions come from
 * the in-memory [sessionsByProject] that refreshes on every [refresh] call.
 */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val projectsApi: ProjectsApi,
) : ViewModel() {

    private val expanded = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private val sessionsByProject = MutableStateFlow<Map<String, List<SessionSummary>>>(emptyMap())
    private val _loading = MutableStateFlow(true)

    /**
     * One-shot user-facing messages (snackbars). Buffered so a failure that
     * fires before the UI subscribes isn't dropped silently. Same shape as
     * SessionViewModel.messages — the hosting screen collects and forwards
     * to its own SnackbarHostState.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val state: StateFlow<ProjectsState> = combine(
        projectRepository.observeAll(),
        expanded,
        query,
        sessionsByProject,
        _loading
    ) { projects, expandedIds, q, sessionMap, loading ->
        val filtered = if (q.isBlank()) {
            projects
        } else {
            val needle = q.trim().lowercase()
            projects.filter { p ->
                p.label.lowercase().contains(needle) ||
                    p.cwd.lowercase().contains(needle)
            }
        }
        ProjectsState(
            projects = filtered.map { p ->
                ProjectWithSessions(
                    project = p,
                    sessions = sessionMap[p.cwd].orEmpty(),
                    expanded = p.cwd in expandedIds
                )
            },
            query = q,
            loading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProjectsState(loading = true)
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                // 1. Fetch and cache projects; build the id→path join key for step 2.
                val projectResp = projectsApi.projects()
                val now = System.currentTimeMillis()
                val idToCwd = projectResp.projects.associate { dto ->
                    projectRepository.upsert(
                        ProjectEntity(
                            cwd = dto.path,
                            label = dto.name,
                            pinned = false,
                            lastSeenAt = now,
                            createdAt = now
                        )
                    )
                    dto.id to dto.path
                }

                // 2. Fetch sessions, keep running + detached (not completed/failed/cancelled).
                // "detached" = tmux process alive but agent not currently streaming — user
                // can still reconnect to it. "running" = agent actively producing output.
                val grouped = projectsApi.sessions().sessions
                    .filter { it.status == "running" || it.status == "detached" }
                    .groupBy { idToCwd[it.projectId] ?: "" }
                    .filterKeys { it.isNotBlank() }
                    .mapValues { (_, sessions) ->
                        sessions.map { dto ->
                            SessionSummary(
                                id = dto.id,
                                title = dto.task.ifBlank { "Terminal" },
                                lastActiveAt = parseServerTime(dto.updatedAt),
                                status = dto.status
                            )
                        }
                    }

                sessionsByProject.value = grouped
            }.onFailure {
                _messages.tryEmit("Couldn't load projects: ${it.shortReason()}")
            }
            _loading.value = false
        }
    }

    fun toggleExpanded(projectId: String) {
        expanded.update { current ->
            if (projectId in current) current - projectId else current + projectId
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    /**
     * Create a new session on the server for the given project.
     * Returns the real session ID on success, null on failure.
     */
    suspend fun createSession(
        projectPath: String,
        task: String = "Terminal",
        mode: String = "session",
        cliType: String = "claude",
    ): String? = runCatching {
        val resp = projectsApi.createSession(
            CreateSessionRequest(
                projectPath = projectPath,
                task = task,
                mode = mode,
                cliType = cliType,
            )
        )
        resp.session?.id
    }.onFailure {
        _messages.tryEmit("Couldn't create session: ${it.shortReason()}")
    }.getOrNull()

    private fun Throwable.shortReason(): String =
        message?.take(120) ?: this::class.simpleName ?: "unknown error"

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val serverTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseServerTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching { serverTimeFmt.parse(raw)?.time ?: 0L }.getOrDefault(0L)
    }
}

data class ProjectsState(
    val projects: List<ProjectWithSessions> = emptyList(),
    val query: String = "",
    val loading: Boolean = true
)

data class ProjectWithSessions(
    val project: ProjectEntity,
    val sessions: List<SessionSummary>,
    val expanded: Boolean
)

data class SessionSummary(
    val id: String,
    val title: String,
    val lastActiveAt: Long,
    val status: String = "detached"
)
