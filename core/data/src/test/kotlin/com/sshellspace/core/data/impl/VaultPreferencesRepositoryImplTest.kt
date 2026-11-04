package com.sshellspace.core.data.impl

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.models.StoredPreference
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.models.VaultPreference
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.domain.repositories.VaultPreferencesDatasource
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

class VaultPreferencesRepositoryImplTest {

    private val key = VaultPreference::class.qualifiedName!!
    private val activeVault = MutableStateFlow<VaultInfo?>(VaultInfo(Uuid.random(), "Work"))
    private val inherited = MutableStateFlow(15)
    private val rows = MutableStateFlow(emptyList<StoredPreference>())

    private inner class FakeDatasource(private val source: Flow<List<StoredPreference>> = rows) : VaultPreferencesDatasource {
        override fun observeAll() = source
        override suspend fun set(key: String, value: String) {
            rows.value = rows.value.filterNot { it.key == key } + StoredPreference(key, value)
        }
    }

    private fun repository(datasource: VaultPreferencesDatasource = FakeDatasource()) = VaultPreferencesRepositoryImpl(
        datasource,
        mockk<VaultManager> { every { activeVault } returns this@VaultPreferencesRepositoryImplTest.activeVault },
        mockk<CorePreferencesRepository> { every { getAutoLockMinutes() } returns inherited.map { it } },
    )

    private fun stored(json: String) {
        rows.value = listOf(StoredPreference(key, json))
    }

    @Test
    fun `a vault with no setting uses the existing timeout`() = runTest {
        inherited.value = 5
        assertEquals(5, repository().getAutoLockMinutes().first())
    }

    @Test
    fun `the default stays 15 minutes`() = runTest {
        assertEquals(15, repository().getAutoLockMinutes().first())
    }

    @Test
    fun `a saved setting wins, including never`() = runTest {
        val repository = repository()

        repository.setAutoLockMinutes(60)
        assertEquals(60, repository.getAutoLockMinutes().first())

        repository.setAutoLockMinutes(0)
        assertEquals(0, repository.getAutoLockMinutes().first())
    }

    @Test
    fun `a stored value that makes no sense is ignored`() = runTest {
        stored(JsonSerializer.encodeToString(VaultPreference.serializer(), VaultPreference(autoLockMinutes = -1)))
        assertEquals(15, repository().getAutoLockMinutes().first())

        stored("not json")
        assertEquals(15, repository().getAutoLockMinutes().first())
    }

    @Test
    fun `with no vault unlocked it doesn't read the vault`() = runTest {
        activeVault.value = null
        val untouched = flow<List<StoredPreference>> { error("No vault is unlocked.") }
        assertEquals(15, repository(FakeDatasource(untouched)).getAutoLockMinutes().first())
    }

    @Test
    fun `a read that fails because the vault locked falls back instead of failing`() = runTest {
        val failing = flow<List<StoredPreference>> { error("No vault is unlocked.") }
        assertEquals(15, repository(FakeDatasource(failing)).getAutoLockMinutes().first())
    }
}
