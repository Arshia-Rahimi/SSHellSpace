package com.sshellspace.core.data.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.shellspace.core.data.generated.Database
import com.sshellspace.core.domain.models.Preference
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class SqlDelightPreferencesDatasourceTest {

    private val datasource = SqlDelightPreferencesDatasource(
        driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = Database.Schema,
            properties = Properties(),
        ),
        json = Json {
            serializersModule = SerializersModule {
                polymorphic(Preference::class) {
                    subclass(FooPreference::class)
                    subclass(BarPreference::class)
                }
            }
        },
    )

    @Test
    fun `get emits empty list initially`() = runTest {
        val result = datasource.get().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `get emits inserted preference`() = runTest {
        val preference = FooPreference("hello")

        datasource.insert(preference)

        val result = datasource.get().first()

        assertEquals(listOf(preference), result)
    }

    @Test
    fun `get emits preferences of different types`() = runTest {
        val foo = FooPreference("foo")
        val bar = BarPreference("bar")

        datasource.insert(foo)
        datasource.insert(bar)

        val result = datasource.get().first()

        assertTrue(result.contains(foo))
        assertTrue(result.contains(bar))
    }

    @Test
    fun `get emits updated preference after update`() = runTest {
        datasource.insert(FooPreference("old"))
        val updated = FooPreference("new")

        datasource.update(updated)

        val result = datasource.get().first()

        assertEquals(listOf(updated), result)
    }

    @Test
    fun `get re-emits when a preference is inserted`() = runTest {
        val emissions = Channel<List<Preference>>(Channel.UNLIMITED)
        val collection = launch { datasource.get().collect { emissions.trySend(it) } }

        assertTrue(emissions.receive().isEmpty())

        val inserted = FooPreference("new")
        datasource.insert(inserted)

        assertEquals(listOf(inserted), emissions.receive())

        collection.cancel()
    }
}
