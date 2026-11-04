package com.sshellspace.core.data.impl

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.models.StoredPreference
import com.sshellspace.core.domain.models.VaultPreference
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.domain.repositories.VaultPreferencesDatasource
import com.sshellspace.core.domain.repositories.VaultPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

internal class VaultPreferencesRepositoryImpl(
    private val datasource: VaultPreferencesDatasource,
    private val vaultManager: VaultManager,
    private val corePreferences: CorePreferencesRepository,
) : VaultPreferencesRepository {
    private val key = VaultPreference::class.qualifiedName!!

    @OptIn(ExperimentalCoroutinesApi::class)
    private val storedAutoLockMinutes: Flow<Int?> = vaultManager.activeVault
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                datasource.observeAll()
                    .map { rows -> rows.storedPreference()?.autoLockMinutes?.takeIf { it >= 0 } }
                    // The vault can lock mid-read. This flow keeps auto-lock running, so it must never end in a failure.
                    .catch { emit(null) }
            }
        }

    override fun getAutoLockMinutes(): Flow<Int> =
        combine(storedAutoLockMinutes, corePreferences.getAutoLockMinutes()) { stored, inherited -> stored ?: inherited }
            .distinctUntilChanged()

    override suspend fun setAutoLockMinutes(minutes: Int) {
        val value = VaultPreference(autoLockMinutes = minutes)
        datasource.set(key, JsonSerializer.encodeToString(VaultPreference.serializer(), value))
    }

    private fun List<StoredPreference>.storedPreference(): VaultPreference? {
        val stored = firstOrNull { it.key == key }?.value ?: return null
        return try {
            JsonSerializer.decodeFromString(VaultPreference.serializer(), stored)
        } catch (_: SerializationException) {
            // A value from another app version shouldn't turn into a failure here.
            null
        }
    }
}
