package com.sshellspace.feature.terminal.data

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.repositories.VaultPreferencesDatasource
import com.sshellspace.feature.terminal.domain.SavedSessionRepository
import com.sshellspace.feature.terminal.domain.models.SavedSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException

class SavedSessionRepositoryImpl(
    private val datasource: VaultPreferencesDatasource,
) : SavedSessionRepository {
    private val key = SavedSession::class.qualifiedName!!

    override suspend fun load(): SavedSession {
        val stored = datasource.observeAll().first().firstOrNull { it.key == key }?.value ?: return SavedSession()
        return try {
            JsonSerializer.decodeFromString(SavedSession.serializer(), stored)
        } catch (_: SerializationException) {
            // Only a convenience, so a value from another app version shouldn't stop the vault from opening.
            SavedSession()
        }
    }

    override suspend fun save(session: SavedSession) {
        datasource.set(key, JsonSerializer.encodeToString(SavedSession.serializer(), session))
    }
}
