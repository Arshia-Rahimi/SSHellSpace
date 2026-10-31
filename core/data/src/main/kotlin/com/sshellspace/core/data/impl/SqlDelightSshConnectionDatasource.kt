package com.sshellspace.core.data.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Ssh_connections
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightSshConnectionDatasource(
    private val db: Database,
) : SshConnectionDatasource {
    override fun index(): Flow<List<SshConnection>> =
        db.ssh_connectionQueries.index().asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toSshConnection() } }

    override suspend fun get(id: Uuid): SshConnection? = withContext(Dispatchers.IO) {
        db.ssh_connectionQueries.get(id.toString()).executeAsOneOrNull()?.toSshConnection()
    }

    override suspend fun upsert(connection: SshConnection) {
        withContext(Dispatchers.IO) { db.ssh_connectionQueries.upsert(connection.toRow()) }
    }

    override suspend fun delete(id: Uuid) {
        withContext(Dispatchers.IO) { db.ssh_connectionQueries.delete(id.toString()) }
    }

    private fun Ssh_connections.toSshConnection() = SshConnection(
        id = Uuid.parse(id),
        name = name,
        host = host,
        port = port.toInt(),
        username = username,
        auth = JsonSerializer.decodeFromString(SshConnectionAuth.serializer(), auth),
        options = JsonSerializer.decodeFromString(SshConnectionOptions.serializer(), options),
    )

    private fun SshConnection.toRow() = Ssh_connections(
        id = id.toString(),
        name = name,
        host = host,
        port = port.toLong(),
        username = username,
        auth = JsonSerializer.encodeToString(SshConnectionAuth.serializer(), auth),
        options = JsonSerializer.encodeToString(SshConnectionOptions.serializer(), options),
    )
}
