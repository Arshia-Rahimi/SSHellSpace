package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.SavedSession

/** Remembers each vault's open tabs, inside that vault. Only use while a vault is unlocked. */
interface SavedSessionRepository {
    suspend fun load(): SavedSession
    suspend fun save(session: SavedSession)
}
