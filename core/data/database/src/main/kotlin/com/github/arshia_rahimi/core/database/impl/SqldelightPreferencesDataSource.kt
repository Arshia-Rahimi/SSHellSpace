package com.github.arshia_rahimi.core.database.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.arshia_rahimi.core.database.PreferencesDatasource
import com.github.arshia_rahimi.core.database.generated.SSHellSpaceDatabase
import com.github.arshia_rahimi.core.model.Preferences
import com.github.arshia_rahimi.sshellspace.core.common.JsonSerializer
import com.github.arshia_rahimi.sshellspace.core.common.onFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull

class SqldelightPreferencesDataSource(
    private val db: SSHellSpaceDatabase,
) : PreferencesDatasource {
    override val preferences = db.preferencesQueries
        .get()
        .asFlow()
        .mapToOneOrNull(Dispatchers.IO)
        .onFirst {
            if (it == null) {
                db.preferencesQueries
                    .create(JsonSerializer.encodeToString(Preferences))
            }
        }
        .mapNotNull {
            it?.let {
                JsonSerializer.decodeFromString<Preferences>(it)
            }
        }

    override suspend fun updatePreferences(preferences: Preferences) {
        db.preferencesQueries.update(JsonSerializer.encodeToString(preferences))
    }
}
