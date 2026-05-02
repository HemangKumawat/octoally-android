package com.octoally.feature.session.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.data.skills.SkillUsageRepository
import com.octoally.core.network.api.SkillsApi
import com.octoally.core.network.api.SystemApi
import com.octoally.core.network.model.FuzzyMatcher
import com.octoally.core.network.model.Skill
import com.octoally.core.network.model.SkillIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Badge variants for the Ollama GPU/CPU indicator. */
enum class GpuBadge { GPU, CPU_ONLY, UNKNOWN }

/** One pill in the suggest bar with enough metadata to render + rank it. */
data class SuggestedSkill(
    val skill: Skill,
    val source: SuggestionSource,
    val score: Double,
)

enum class SuggestionSource { PINNED, RECENT, SUGGESTED }

/** Full UI state observed by [com.octoally.feature.session.ui.SkillSuggestBar]. */
data class SkillSuggestState(
    val pinned: List<Skill> = emptyList(),
    val recent: List<Skill> = emptyList(),
    val suggestions: List<SuggestedSkill> = emptyList(),
    val gpu: GpuBadge = GpuBadge.UNKNOWN,
    val expanded: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * Client-side skill-suggest ranker.
 *
 * The server only exposes the intent catalog (`GET /api/skills/intents`) — all
 * ranking is done in this VM. Inputs:
 *   1. `SkillsApi.list()` + `SkillsApi.intents()` loaded once at startup,
 *   2. `SkillUsageRepository` flows for pinned + recent + frequency-weighted
 *      suggestions (persisted across sessions),
 *   3. `onInputChange(input)` from the owning screen — this mutates the
 *      visible ranked list using [FuzzyMatcher].
 *
 * The VM owns a 30s poll loop against `SystemApi.status()` to update the
 * GPU/CPU/UNKNOWN badge.
 */
@HiltViewModel
class SkillSuggestViewModel @Inject constructor(
    private val skillsApi: SkillsApi,
    private val systemApi: SystemApi,
    private val usageRepository: SkillUsageRepository,
) : ViewModel() {

    // ── UI state ──────────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(SkillSuggestState())
    val state: StateFlow<SkillSuggestState> = _state.asStateFlow()

    // Side-effects that the parent screen should perform (e.g. execute a
    // skill as a slash command on the command bar).
    private val _effects = Channel<SkillSuggestEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Server catalogs, loaded once.
    private var allSkills: List<Skill> = emptyList()
    private var intents: List<SkillIntent> = emptyList()

    // Latest text from the command bar; recomputed into suggestions lazily.
    private var currentInput: String = ""

    // Scope key for persistence (per-project in the future; for now, one slot).
    private val projectId: String = DEFAULT_PROJECT_ID

    init {
        loadCatalogs()
        pollGpuStatus()
        observeUsage()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called on every command-bar keystroke. Input is the full text. */
    fun onInputChange(input: String) {
        currentInput = input
        recomputeSuggestions()
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    /** User tapped a pill: record usage + emit a side-effect for execution. */
    fun invoke(skill: Skill) {
        viewModelScope.launch {
            usageRepository.recordUse(projectId, skill.name)
            _effects.send(SkillSuggestEffect.Execute(skill))
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun loadCatalogs() {
        viewModelScope.launch {
            runCatching {
                val skillsResponse = skillsApi.list()
                val intentsResponse = skillsApi.intents()
                allSkills = skillsResponse.skills
                intents = intentsResponse.intents
            }
            _state.update { it.copy(loaded = true) }
            recomputeSuggestions()
        }
    }

    private fun pollGpuStatus() {
        viewModelScope.launch {
            while (isActive) {
                val next = runCatching { systemApi.status() }
                    .fold(
                        onSuccess = { body ->
                            val ollama = body.integrations.firstOrNull { it.id == "ollama" }
                            when {
                                ollama == null || ollama.connected != true -> GpuBadge.UNKNOWN
                                ollama.details?.toString()?.contains("\"gpu\":true") == true -> GpuBadge.GPU
                                else -> GpuBadge.CPU_ONLY
                            }
                        },
                        onFailure = { GpuBadge.UNKNOWN },
                    )
                _state.update { it.copy(gpu = next) }
                delay(GPU_POLL_MS)
            }
        }
    }

    private fun observeUsage() {
        viewModelScope.launch {
            usageRepository.pinnedSkills(projectId).collect { pinnedNames ->
                val bySkillName = allSkills.associateBy { it.name }
                val pinned = pinnedNames.mapNotNull { bySkillName[it] }
                _state.update { it.copy(pinned = pinned) }
                recomputeSuggestions()
            }
        }
        viewModelScope.launch {
            usageRepository.recentSkills(projectId).collect { recentNames ->
                val bySkillName = allSkills.associateBy { it.name }
                val recent = recentNames.mapNotNull { bySkillName[it] }
                _state.update { it.copy(recent = recent) }
                recomputeSuggestions()
            }
        }
    }

    /**
     * Merge pinned + recent + fuzzy-ranked suggestions into one deduplicated
     * list capped at [VISIBLE_CAP] collapsed or [EXPANDED_CAP] expanded.
     *
     * Priority:
     *   1. Pinned (all),
     *   2. Recent (excluding already-pinned),
     *   3. Fuzzy/intent ranking (excluding above) — gated by [currentInput].
     */
    private fun recomputeSuggestions() {
        val input = currentInput.trim()
        val isSlash = input.startsWith("/")
        val bySkillId = allSkills.associateBy { it.id }

        val seen = linkedSetOf<String>()
        val merged = mutableListOf<SuggestedSkill>()

        for (s in _state.value.pinned) {
            if (seen.add(s.id)) merged += SuggestedSkill(s, SuggestionSource.PINNED, score = Double.MAX_VALUE)
        }
        for (s in _state.value.recent) {
            if (seen.add(s.id)) merged += SuggestedSkill(s, SuggestionSource.RECENT, score = Double.MAX_VALUE - 1)
        }

        // Fuzzy layer.
        val ranked: List<FuzzyMatcher.ScoredCandidate> = when {
            isSlash -> {
                val raw = input.removePrefix("/")
                val spaceIdx = raw.indexOf(' ')
                val query = if (spaceIdx >= 0) raw.substring(0, spaceIdx) else raw
                if (query.isEmpty()) emptyList() else FuzzyMatcher.slashMatch(query, allSkills)
            }
            input.length >= 3 && intents.isNotEmpty() ->
                FuzzyMatcher.intentMatch(input, intents, allSkills)
            else -> emptyList()
        }

        for (c in ranked) {
            if (seen.add(c.skillId)) {
                val skill = bySkillId[c.skillId] ?: continue
                merged += SuggestedSkill(skill, SuggestionSource.SUGGESTED, c.score)
            }
        }

        val cap = if (_state.value.expanded) EXPANDED_CAP else VISIBLE_CAP
        _state.update { it.copy(suggestions = merged.take(cap)) }
    }

    companion object {
        /** Suggestion cap when the bar is collapsed (one row of pills). */
        const val VISIBLE_CAP: Int = 5

        /** Suggestion cap inside the expanded bottom sheet. */
        const val EXPANDED_CAP: Int = 20

        /** Matches `pollMs = 30000` in the web SkillSuggestBar. */
        private const val GPU_POLL_MS: Long = 30_000L

        /** Single-project scope for now — task #10 will thread real project IDs. */
        private const val DEFAULT_PROJECT_ID: String = "default"
    }
}

/** One-shot events emitted to the UI layer. */
sealed class SkillSuggestEffect {
    /** Parent screen should push the slash command into its command bar. */
    data class Execute(val skill: Skill) : SkillSuggestEffect()
}
