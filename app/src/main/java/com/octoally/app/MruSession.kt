package com.octoally.app

import android.content.Context

/**
 * Most-Recently-Used session id, persisted to SharedPrefs so a cold launch can
 * restore the last-opened session instead of dumping the user on the empty
 * Projects list every time.
 *
 * The id is written by [MainActivity] right before navigating into the terminal
 * route. It is cleared by [com.octoally.feature.session.SessionViewModel] when
 * the server responds 404 on `GET /api/sessions/:id` (cancelled/deleted).
 */
internal const val PREFS_MRU = "octoally_mru"
internal const val KEY_MRU_SESSION = "mru_session_id"
internal const val KEY_MRU_PROJECT_PATH = "mru_project_path"
internal const val KEY_MRU_PROJECT_NAME = "mru_project_name"

internal fun readMruSessionId(ctx: Context): String? =
    ctx.getSharedPreferences(PREFS_MRU, Context.MODE_PRIVATE)
        .getString(KEY_MRU_SESSION, null)
        ?.takeIf { it.isNotBlank() }

internal fun writeMruSessionId(ctx: Context, id: String) {
    ctx.getSharedPreferences(PREFS_MRU, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_MRU_SESSION, id)
        .apply()
}

/** rc16 — track the project path of the MRU session so the in-terminal "+ Terminal"
 *  FAB can spawn a new session in the same project without a sheet. */
internal fun readMruProjectPath(ctx: Context): String? =
    ctx.getSharedPreferences(PREFS_MRU, Context.MODE_PRIVATE)
        .getString(KEY_MRU_PROJECT_PATH, null)
        ?.takeIf { it.isNotBlank() }

internal fun readMruProjectName(ctx: Context): String? =
    ctx.getSharedPreferences(PREFS_MRU, Context.MODE_PRIVATE)
        .getString(KEY_MRU_PROJECT_NAME, null)
        ?.takeIf { it.isNotBlank() }

internal fun writeMruProject(ctx: Context, path: String, name: String) {
    ctx.getSharedPreferences(PREFS_MRU, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_MRU_PROJECT_PATH, path)
        .putString(KEY_MRU_PROJECT_NAME, name)
        .apply()
}
