package com.sshellspace.core.data.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.shellspace.core.data.generated.Database
import com.sshellspace.core.domain.models.StoredPreference
import com.sshellspace.core.domain.repositories.PreferencesDatasource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

internal class SqlDelightPreferencesDatasource(
    driver: SqlDriver,
) : PreferencesDatasource {
    private val db = Database(driver)

    override fun observeAll() = db.preferencesQueries
        .index()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { rows ->
            rows.map { StoredPreference(key = it.name, value = it.value_) }
        }

    override suspend fun set(key: String, value: String) {
        db.preferencesQueries.upsert(name = key, value_ = value)
    }
}
