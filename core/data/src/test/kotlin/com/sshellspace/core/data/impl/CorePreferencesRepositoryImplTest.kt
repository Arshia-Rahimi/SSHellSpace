package com.sshellspace.core.data.impl

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.domain.models.CorePreference
import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.ThemePreference
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CorePreferencesRepositoryImplTest {

    private val datasource = SqlDelightPreferencesDatasource(
        db = Database(
            driver = JdbcSqliteDriver(
                url = JdbcSqliteDriver.IN_MEMORY,
                schema = Database.Schema,
                properties = Properties(),
            ),
            device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
            vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
        ),
    )
    private val repository = CorePreferencesRepositoryImpl(datasource)

    @Test
    fun `getTheme defaults to SYSTEM_DEFAULT when nothing stored`() = runTest {
        assertEquals(ThemePreference.SYSTEM_DEFAULT, repository.getTheme().first())
    }

    @Test
    fun `setTheme persists serialized CorePreference keyed by qualified name`() = runTest {
        repository.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, repository.getTheme().first { it == ThemePreference.DARK })

        val stored = datasource.observeAll().first().single()
        assertEquals(CorePreference::class.qualifiedName, stored.key)
        val decoded = JsonSerializer.decodeFromString(CorePreference.serializer(), stored.value)
        assertEquals(CorePreference(themePreference = ThemePreference.DARK), decoded)
    }

    @Test
    fun `getColorScheme defaults to the default scheme when nothing stored`() = runTest {
        assertEquals(DEFAULT_COLOR_SCHEME_ID, repository.getColorScheme().first())
    }

    @Test
    fun `setColorScheme keeps the stored theme preference`() = runTest {
        repository.setTheme(ThemePreference.LIGHT)
        repository.getTheme().first { it == ThemePreference.LIGHT }

        repository.setColorScheme("violet")

        assertEquals("violet", repository.getColorScheme().first { it == "violet" })
        assertEquals(ThemePreference.LIGHT, repository.getTheme().first())
    }

    @Test
    fun `setShortcut stores an override and null restores the default`() = runTest {
        val chord = KeyChord(keyCode = 42L, ctrl = true, shift = true)

        repository.setShortcut("new_tab", chord)
        assertEquals(mapOf("new_tab" to chord), repository.getShortcuts().first { it.isNotEmpty() })

        repository.setShortcut("new_tab", null)
        assertEquals(emptyMap(), repository.getShortcuts().first { it.isEmpty() })
    }
}
