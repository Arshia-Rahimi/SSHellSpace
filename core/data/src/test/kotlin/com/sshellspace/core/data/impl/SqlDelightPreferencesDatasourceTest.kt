package com.sshellspace.core.data.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.shellspace.core.data.generated.Database
import com.sshellspace.core.domain.models.StoredPreference
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class SqlDelightPreferencesDatasourceTest {

    private val datasource = SqlDelightPreferencesDatasource(
        driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = Database.Schema,
            properties = Properties(),
        ),
    )

    @Test
    fun `observeAll emits empty list initially`() = runTest {
        assertTrue(datasource.observeAll().first().isEmpty())
    }

    @Test
    fun `set stores key value pair`() = runTest {
        datasource.set("key", "value")

        val result = datasource.observeAll().first()

        assertEquals(listOf(StoredPreference(key = "key", value = "value")), result)
    }

    @Test
    fun `set stores independent keys`() = runTest {
        datasource.set("a", "1")
        datasource.set("b", "2")

        val result = datasource.observeAll().first()

        assertTrue(result.contains(StoredPreference(key = "a", value = "1")))
        assertTrue(result.contains(StoredPreference(key = "b", value = "2")))
    }

    @Test
    fun `set overwrites existing key`() = runTest {
        datasource.set("key", "old")
        datasource.set("key", "new")

        val result = datasource.observeAll().first()

        assertEquals(listOf(StoredPreference(key = "key", value = "new")), result)
    }

    @Test
    fun `observeAll re-emits when a key is set`() = runTest {
        val emissions = Channel<List<StoredPreference>>(Channel.UNLIMITED)
        val collection = launch { datasource.observeAll().collect { emissions.trySend(it) } }

        assertTrue(emissions.receive().isEmpty())

        datasource.set("key", "value")

        assertEquals(listOf(StoredPreference(key = "key", value = "value")), emissions.receive())

        collection.cancel()
    }
}
