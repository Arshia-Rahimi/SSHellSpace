package com.sshellspace.core.data.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.domain.models.CorePreference
import com.sshellspace.core.domain.models.ThemePreference
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CorePreferencesRepositoryImplTest {

    private val datasource = SqlDelightPreferencesDatasource(
        driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = Database.Schema,
            properties = Properties(),
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
}
