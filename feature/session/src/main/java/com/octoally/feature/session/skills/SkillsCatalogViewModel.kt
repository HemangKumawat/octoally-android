package com.octoally.feature.session.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.octoally.core.network.api.SkillsApi
import com.octoally.core.network.model.Skill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillsCatalogState(
    val skills: List<Skill> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val loading: Boolean = false,
) {
    val filteredSkills: List<Skill>
        get() {
            var result = skills
            if (selectedCategory != null) {
                result = result.filter { it.category == selectedCategory }
            }
            val q = searchQuery.trim().lowercase()
            if (q.isNotEmpty()) {
                result = result.filter { skill ->
                    skill.name.lowercase().contains(q) ||
                        skill.command.lowercase().contains(q) ||
                        skill.description?.lowercase()?.contains(q) == true
                }
            }
            return result
        }
}

@HiltViewModel
class SkillsCatalogViewModel @Inject constructor(
    private val skillsApi: SkillsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SkillsCatalogState())
    val state: StateFlow<SkillsCatalogState> = _state.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { skillsApi.list() }
                .onSuccess { resp ->
                    _state.update { it.copy(
                        skills = resp.skills,
                        categories = resp.categories.ifEmpty {
                            resp.skills.map { s -> s.category }.distinct().sorted()
                        },
                    ) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun setCategory(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }
}
