package com.octoally.feature.session.palette

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.network.api.SkillsApi
import com.octoally.core.network.model.Skill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the Ctrl+K command palette. */
data class PaletteState(
    val open: Boolean = false,
    val query: String = "",
    val results: List<PaletteResult> = emptyList(),
    val loading: Boolean = false,
)

/** One row in the palette. Either a skill from the server or a built-in action. */
sealed class PaletteResult(open val label: String) {
    data class SkillResult(val skill: Skill) : PaletteResult(skill.command)
    data class ActionResult(
        val id: String,
        override val label: String,
        val icon: ImageVector,
    ) : PaletteResult(label)
}

/** Concrete action the UI layer is asked to perform when a row is tapped. */
sealed class SelectedAction {
    data class InvokeSkill(val skill: Skill) : SelectedAction()
    object OpenThemePicker : SelectedAction()
    object NewSession : SelectedAction()
    object ToggleConnection : SelectedAction()
}

@HiltViewModel
class CommandPaletteViewModel @Inject constructor(
    private val skillsApi: SkillsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(PaletteState())
    val state: StateFlow<PaletteState> = _state.asStateFlow()

    /** One-shot user-facing messages (snackbars) — same contract as SessionViewModel.messages. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Cached full catalog so we don't refetch on every keystroke.
    private var skillCatalog: List<Skill> = emptyList()
    private var loaded: Boolean = false

    // Built-in actions are always present and ranked alongside skills.
    private val builtInActions: List<PaletteResult.ActionResult> = listOf(
        PaletteResult.ActionResult("theme",   "Switch theme",     Icons.Outlined.Palette),
        PaletteResult.ActionResult("new",     "New session",      Icons.Outlined.Add),
        PaletteResult.ActionResult("toggle",  "Reconnect",         Icons.Outlined.Power),
    )

    fun open() {
        _state.update { it.copy(open = true, query = "") }
        refreshResults("")
        if (!loaded) loadSkills()
    }

    fun close() {
        _state.update { it.copy(open = false) }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        refreshResults(q)
    }

    /**
     * Resolve a tapped row to a [SelectedAction] and forward it to the caller.
     * Each action handler owns its own dismissal via scope.launch { hide(); close() }
     * — do NOT call close() here or it races the coroutine and kills it.
     */
    fun onSelect(result: PaletteResult, callback: (SelectedAction) -> Unit) {
        val action: SelectedAction? = when (result) {
            is PaletteResult.SkillResult -> SelectedAction.InvokeSkill(result.skill)
            is PaletteResult.ActionResult -> when (result.id) {
                "theme"  -> SelectedAction.OpenThemePicker
                "new"    -> SelectedAction.NewSession
                "toggle" -> SelectedAction.ToggleConnection
                else     -> null
            }
        }
        if (action != null) callback(action)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadSkills() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { skillsApi.list() }
                .onSuccess { resp ->
                    skillCatalog = resp.skills
                    loaded = true
                    refreshResults(_state.value.query)
                }
                .onFailure {
                    _messages.tryEmit(
                        "Couldn't load skills: ${it.message?.take(120) ?: "unknown error"}"
                    )
                }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun refreshResults(query: String) {
        val skillRows = skillCatalog.map { PaletteResult.SkillResult(it) }
        val allRows: List<PaletteResult> = builtInActions + skillRows
        val ranked = if (query.isBlank()) {
            // Default: actions first, then first 30 skills.
            (builtInActions + skillRows.take(30)).toList()
        } else {
            allRows
                .mapNotNull { row ->
                    val score = fuzzyScore(query, rowHaystack(row))
                    if (score <= 0f) null else row to score
                }
                .sortedByDescending { it.second }
                .take(30)
                .map { it.first }
        }
        _state.update { it.copy(results = ranked) }
    }

    private fun rowHaystack(row: PaletteResult): String = when (row) {
        is PaletteResult.SkillResult -> buildString {
            append(row.skill.command); append(' ')
            append(row.skill.name); append(' ')
            append(row.skill.category)
            row.skill.description?.let { append(' '); append(it) }
        }
        is PaletteResult.ActionResult -> row.label
    }

    /**
     * Tiny subsequence fuzzy ranker.
     * Returns 0 if `query` chars don't appear in order inside `target`.
     * Otherwise: (matchedChars / queryLen) * (1 / (1 + spread)) — prefix/tight
     * matches score higher than scattered ones. Case-insensitive. <40 LOC.
     */
    private fun fuzzyScore(query: String, target: String): Float {
        if (query.isEmpty()) return 1f
        val q = query.lowercase()
        val t = target.lowercase()
        // Exact prefix on primary token -> high boost.
        if (t.startsWith(q)) return 2f
        if (t.contains(q)) return 1.5f
        var qi = 0
        var firstMatch = -1
        var lastMatch = -1
        for (i in t.indices) {
            if (qi < q.length && t[i] == q[qi]) {
                if (firstMatch < 0) firstMatch = i
                lastMatch = i
                qi++
            }
            if (qi == q.length) break
        }
        if (qi < q.length) return 0f
        val spread = (lastMatch - firstMatch).coerceAtLeast(0)
        val matchedRatio = qi.toFloat() / q.length
        return matchedRatio * (1f / (1f + spread * 0.05f))
    }
}
