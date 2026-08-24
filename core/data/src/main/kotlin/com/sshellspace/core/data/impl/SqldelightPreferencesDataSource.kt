package com.sshellspace.core.data.impl

import app.cash.sqldelight.db.SqlDriver
import com.shellspace.core.data.generated.Database
import com.sshellspace.core.data.models.Preferences
import com.sshellspace.core.data.PreferencesDatasource
import kotlinx.coroutines.flow.Flow

class SqldelightPreferencesDataSource(
    driver: SqlDriver,
) : PreferencesDatasource {
//    override fun getPreferences() = db.preferencesQueries
//        .get()
//        .asFlow()
//        .mapToOneOrNull(Dispatchers.IO)
//        .mapNotNull { it?.let { JsonSerializer.decodeFromString<Preferences>(it) } }

    //    override suspend fun updatePreferences(preferences: Preferences) {
//        db.preferencesQueries.update(JsonSerializer.encodeToString(preferences))
//    }
    override fun getPreferences(): Flow<Preferences> {
        TODO("Not yet implemented")
    }

    override suspend fun updatePreferences(preferences: Preferences) {
        TODO("Not yet implemented")
    }
}
