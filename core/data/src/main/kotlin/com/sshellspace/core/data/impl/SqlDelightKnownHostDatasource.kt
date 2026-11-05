package com.sshellspace.core.data.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sshellspace.core.data.KnownHostDatasource
import com.sshellspace.core.data.generated.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlDelightKnownHostDatasource(
    private val db: Database,
) : KnownHostDatasource {
    override fun index(): Flow<List<String>> = db.known_hostQueries.index().asFlow().mapToList(Dispatchers.IO)

    override suspend fun lines(): List<String> = withContext(Dispatchers.IO) {
        db.known_hostQueries.index().executeAsList()
    }

    override suspend fun add(lines: List<String>) {
        withContext(Dispatchers.IO) {
            db.known_hostQueries.transaction { lines.forEach(db.known_hostQueries::insert) }
        }
    }

    override suspend fun replace(old: String, new: String) {
        withContext(Dispatchers.IO) {
            db.known_hostQueries.transaction {
                db.known_hostQueries.delete(old)
                db.known_hostQueries.insert(new)
            }
        }
    }

    override suspend fun delete(line: String) {
        withContext(Dispatchers.IO) { db.known_hostQueries.delete(line) }
    }
}
