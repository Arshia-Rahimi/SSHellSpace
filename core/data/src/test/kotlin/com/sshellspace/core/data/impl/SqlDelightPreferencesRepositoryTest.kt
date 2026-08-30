package com.sshellspace.core.data.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.data.PreferencesDatasource
import com.sshellspace.core.domain.models.Preference
import io.mockk.clearMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

class SqlDelightPreferencesRepositoryTest {

    private val datasource = mockk<PreferencesDatasource>()

    @AfterTest
    fun tearDown() {
        clearMocks(datasource)
    }

    private fun repository(preferences: List<Preference>): SqlDelightPreferencesRepository {
        every { datasource.get() } returns MutableStateFlow(preferences)
        coJustRun { datasource.insert(any()) }
        coJustRun { datasource.update(any()) }
        return SqlDelightPreferencesRepository(datasource)
    }

    @Test
    fun `get emits matching preference from populated list`() = runTest {
        val foo = FooPreference("a")
        val repository = repository(listOf(foo))

        val result = repository.get(FooPreference::class).first()

        assertEquals(foo, result)
    }

    @Test
    fun `get filters out other preference types`() = runTest {
        val foo = FooPreference("x")
        val repository = repository(listOf(foo, BarPreference("y")))

        val result = repository.get(FooPreference::class).first()

        assertEquals(foo, result)
    }

    @Test
    fun `get suspends when type is absent`() = runTest {
        val repository = repository(listOf(BarPreference("y")))

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1.seconds) { repository.get(FooPreference::class).first() }
        }
    }

    @Test
    fun `set inserts when no preference of type exists`() = runTest {
        val repository = repository(emptyList())
        val value = FooPreference("new")

        repository.set(value)

        coVerify(exactly = 1) { datasource.insert(value) }
        coVerify(exactly = 0) { datasource.update(any()) }
    }

    @Test
    fun `set updates when preference of type exists`() = runTest {
        val repository = repository(listOf(FooPreference("old")))
        val value = FooPreference("new")

        repository.set(value)

        coVerify(exactly = 1) { datasource.update(value) }
        coVerify(exactly = 0) { datasource.insert(any()) }
    }
}
