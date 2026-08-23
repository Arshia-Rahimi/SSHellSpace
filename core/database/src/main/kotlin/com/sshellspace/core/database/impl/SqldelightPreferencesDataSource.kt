package com.sshellspace.core.database.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.sshellspace.core.data.models.Preferences
import com.sshellspace.core.database.PreferencesDatasource
import com.github.arshia_rahimi.core.database.generated.SSHellSpaceDatabase
import com.sshellspace.core.common.JsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull

class SqldelightPreferencesDataSource(
    private val db: SSHellSpaceDatabase,
) : PreferencesDatasource {
    override fun getPreferences() = db.preferencesQueries
        .get()
        .asFlow()
        .mapToOneOrNull(Dispatchers.IO)
        .mapNotNull { it?.let { JsonSerializer.decodeFromString<Preferences>(it) } }

    override suspend fun updatePreferences(preferences: Preferences) {
        db.preferencesQueries.update(JsonSerializer.encodeToString(preferences))
    }
}
