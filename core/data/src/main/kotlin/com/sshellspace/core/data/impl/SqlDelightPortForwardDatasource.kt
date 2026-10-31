package com.sshellspace.core.data.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sshellspace.core.data.PortForwardDatasource
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Port_forwards
import com.sshellspace.core.domain.models.PortForward
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightPortForwardDatasource(
    private val db: Database,
) : PortForwardDatasource {
    override fun index(): Flow<List<PortForward>> =
        db.port_forwardQueries.index().asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toPortForward() } }

    override suspend fun get(id: Uuid): PortForward? = withContext(Dispatchers.IO) {
        db.port_forwardQueries.get(id.toString()).executeAsOneOrNull()?.toPortForward()
    }

    override suspend fun upsert(forward: PortForward) {
        withContext(Dispatchers.IO) { db.port_forwardQueries.upsert(forward.toRow()) }
    }

    override suspend fun delete(id: Uuid) {
        withContext(Dispatchers.IO) { db.port_forwardQueries.delete(id.toString()) }
    }

    override suspend fun deleteForConnection(connectionId: Uuid) {
        withContext(Dispatchers.IO) { db.port_forwardQueries.deleteForConnection(connectionId.toString()) }
    }

    private fun Port_forwards.toPortForward() = PortForward(
        id = Uuid.parse(id),
        name = name,
        connectionId = Uuid.parse(connection_id),
        type = type,
        bindHost = bind_host,
        bindPort = bind_port.toInt(),
        destinationHost = destination_host,
        destinationPort = destination_port.toInt(),
    )

    private fun PortForward.toRow() = Port_forwards(
        id = id.toString(),
        name = name,
        connection_id = connectionId.toString(),
        type = type,
        bind_host = bindHost,
        bind_port = bindPort.toLong(),
        destination_host = destinationHost,
        destination_port = destinationPort.toLong(),
    )
}
