package com.sshellspace.core.data.impl

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Port_forwards
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.domain.models.CorePreference
import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.ThemePreference
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
            port_forwardsAdapter = Port_forwards.Adapter(EnumColumnAdapter()),
        ),
    )
    private val repository = CorePreferencesRepositoryImpl(datasource)
    private val exampleTheme = checkNotNull(javaClass.getResource("/custom-theme.json")).readText()

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
    fun `importUserTheme saves the theme and makes it active`() = runTest {
        val theme = repository.importUserTheme(exampleTheme)

        assertEquals(theme, repository.getActiveUserTheme().first { it != null })
        assertEquals(listOf(theme), repository.getUserThemes().first())
    }

    @Test
    fun `importing the same name replaces the saved theme`() = runTest {
        repository.importUserTheme(exampleTheme)
        repository.getUserThemes().first { it.isNotEmpty() }

        repository.importUserTheme(exampleTheme.replace("#2DD4BF", "#123456"))

        val themes = repository.getUserThemes().first { it.single().dark.primary == "0xFF123456" }
        assertEquals(1, themes.size)
    }

    @Test
    fun `setColorScheme leaves the active imported theme but keeps it saved`() = runTest {
        repository.importUserTheme(exampleTheme)
        repository.getActiveUserTheme().first { it != null }

        repository.setColorScheme("violet")

        assertNull(repository.getActiveUserTheme().first { it == null })
        assertEquals(1, repository.getUserThemes().first().size)
    }

    @Test
    fun `deleteUserTheme removes the theme and deactivates it`() = runTest {
        val theme = repository.importUserTheme(exampleTheme)
        repository.getActiveUserTheme().first { it != null }

        repository.deleteUserTheme(theme.name)

        assertNull(repository.getActiveUserTheme().first { it == null })
        assertEquals(emptyList(), repository.getUserThemes().first())
    }

    @Test
    fun `setShortcut stores an override and null restores the default`() = runTest {
        val chord = KeyChord(keyCode = 42L, ctrl = true, shift = true)

        repository.setShortcut("new_tab", chord)
        assertEquals(mapOf("new_tab" to chord), repository.getShortcuts().first { it.isNotEmpty() })

        repository.setShortcut("new_tab", null)
        assertEquals(emptyMap(), repository.getShortcuts().first { it.isEmpty() })
    }

    @Test
    fun `updates default to on and daily, with no check yet`() = runTest {
        assertEquals(true, repository.getAutoUpdate().first())
        assertEquals(24, repository.getUpdateCheckIntervalHours().first())
        assertNull(repository.getLastUpdateCheck().first())
    }

    @Test
    fun `update settings are stored`() = runTest {
        repository.setAutoUpdate(false)
        withContext(Dispatchers.Default) { withTimeout(5_000) { repository.getAutoUpdate().first { !it } } }

        repository.setUpdateCheckIntervalHours(6)

        withContext(Dispatchers.Default) { withTimeout(5_000) { repository.getUpdateCheckIntervalHours().first { it == 6 } } }
        assertEquals(false, repository.getAutoUpdate().first())
    }

    @Test
    fun `recording a check leaves the other preferences' row alone`() = runTest {
        repository.setLastUpdateCheck(1_234L)

        withContext(Dispatchers.Default) { withTimeout(5_000) { repository.getLastUpdateCheck().first { it == 1_234L } } }
        assertEquals(emptyList(), datasource.observeAll().first().filter { it.key == CorePreference::class.qualifiedName })
    }

    private suspend fun awaitTerminalThemeNames(vararg names: String) = withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            repository.getTerminalThemes().first { themes -> themes.map { it.name } == names.toList() }
        }
    }

    @Test
    fun `terminal themes are saved, replaced by name and deleted`() = runTest {
        repository.importTerminalTheme(exampleTheme, "file")
        awaitTerminalThemeNames("Example")

        val changed = exampleTheme.replace("\"foreground\": \"#E6EDF3\"", "\"foreground\": \"#FFFFFF\"")
        repository.importTerminalTheme(changed, "file")
        val replaced = withContext(Dispatchers.Default) {
            withTimeout(5_000) { repository.getTerminalThemes().first { it.singleOrNull()?.foreground == "0xFFFFFFFF" } }
        }
        assertEquals(1, replaced.size)

        repository.deleteTerminalTheme("Example")
        awaitTerminalThemeNames()
    }

    @Test
    fun `an invalid terminal theme saves nothing`() = runTest {
        kotlin.test.assertFailsWith<IllegalArgumentException> { repository.importTerminalTheme("{}", "file") }

        assertEquals(emptyList(), repository.getTerminalThemes().first())
    }
}
