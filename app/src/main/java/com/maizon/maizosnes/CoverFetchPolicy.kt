package com.maizon.maizosnes

/** Explicit consent only: displaying cached covers is never a network action. */
internal object CoverFetchPolicy {
    fun networkAllowed(userRequested: Boolean, inGame: Boolean,
        multiplayerActive: Boolean): Boolean =
        userRequested && !inGame && !multiplayerActive

    fun pendingCoverNames(imported: List<String>, existing: Set<String>): List<String> =
        imported.distinct().filterNot { it in existing }
}
