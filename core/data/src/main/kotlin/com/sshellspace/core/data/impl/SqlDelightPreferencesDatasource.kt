package com.sshellspace.core.data.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.shellspace.core.data.generated.Database
import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.data.PreferencesDatasource
import com.sshellspace.core.domain.models.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class SqlDelightPreferencesDatasource(
    driver: SqlDriver,
    private val json: Json = JsonSerializer,
) : PreferencesDatasource {
    private val db = Database(driver)

    override fun get() = db.preferencesQueries
        .index()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { result ->
            result.map { json.decodeFromString<Preference>(it.value_) }
        }

    override suspend fun insert(value: Preference) {
        db.preferencesQueries.insert(
            name = value.qualifiedName,
            value_ = json.encodeToString(value)
        )
    }

    override suspend fun update(value: Preference) {
        db.preferencesQueries.update(
            name = value.qualifiedName,
            value_ = json.encodeToString(value)
        )
    }
}
