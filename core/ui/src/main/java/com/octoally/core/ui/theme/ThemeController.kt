package com.octoally.core.ui.theme

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists and exposes the user's current colour theme.
 *
 * Storage file (`octoally_prefs`) and key (`octoally_theme`) deliberately
 * mirror the web dashboard's localStorage keys so that a future cross-device
 * sync layer can share a single pref name.
 */
@Singleton
class ThemeController @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val prefs: SharedPreferences =
        ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val _current: MutableStateFlow<OctoAllyTheme> =
        MutableStateFlow(loadInitial())

    val current: StateFlow<OctoAllyTheme> = _current.asStateFlow()

    /**
     * Apply [theme] immediately and persist it. NOT `suspend` — there are no
     * suspension points, and marking it `suspend` forces callers to wrap in a
     * coroutine. If the caller's scope (e.g. `rememberCoroutineScope` inside a
     * dialog) is cancelled between `launch` and the first instruction running,
     * the theme update silently disappears. That's exactly how the tick-doesn't-
     * move bug landed: the picker's onClick launched setTheme then immediately
     * called onDismiss, cancelling the scope before the coroutine body ran.
     */
    fun setTheme(theme: OctoAllyTheme) {
        prefs.edit().putString(PREF_KEY, theme.id).apply()
        _current.value = theme
    }

    private fun loadInitial(): OctoAllyTheme {
        val id = prefs.getString(PREF_KEY, null) ?: return OctoAllyTheme.Operator
        return OctoAllyTheme.fromId(id)
    }

    companion object {
        const val PREFS_FILE = "octoally_prefs"
        const val PREF_KEY   = "octoally_theme"
    }
}
