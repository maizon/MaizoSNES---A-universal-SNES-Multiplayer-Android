package com.maizon.maizosnes

import android.content.Context

/**
 * Local-only breadcrumb for process death. It does not intercept native signals
 * and never uploads ROM names, IP addresses or private crash information.
 *
 * A stale marker is evidence of an interrupted session, NOT proof of a crash:
 * Android can kill an application for memory pressure while backgrounded.
 */
internal object CrashJournal {
    private const val FILE = "maizo_session_diagnostics"
    private const val ACTIVE = "session_open"
    private const val PHASE = "session_phase"
    private const val LAST = "last_interrupted"

    fun recover(context: Context): String? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val wasActive = p.getBoolean(ACTIVE, false)
        val description = if (wasActive) p.getString(PHASE, "jogo") ?: "jogo" else null
        if (wasActive) p.edit().putBoolean(ACTIVE, false)
            .putString(LAST, description).commit()
        return description
    }

    fun mark(context: Context, phase: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(PHASE, phase.take(90))
            .putBoolean(ACTIVE, true).commit()
    }

    fun clean(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(ACTIVE, false).commit()
    }

    fun lastInterrupted(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(LAST, null)
}
