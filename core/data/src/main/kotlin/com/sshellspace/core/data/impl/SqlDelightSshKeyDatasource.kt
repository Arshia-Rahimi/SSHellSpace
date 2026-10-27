package com.sshellspace.core.data.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.data.generateUuid
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.SshKeySecret
import com.sshellspace.core.domain.models.SshKeyType
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SqlDelightSshKeyDatasource(
    private val db: Database,
) : SshKeyDatasource {
    override fun index(): Flow<List<SshKeyInfo>> = combine(
        db.device_ssh_keyQueries.index().asFlow().mapToList(Dispatchers.IO),
        db.vault_ssh_keyQueries.index().asFlow().mapToList(Dispatchers.IO),
    ) { deviceKeys, vaultKeys ->
        deviceKeys.map { it.toSshKeyInfo() } + vaultKeys.map { it.toSshKeyInfo() }
    }

    override suspend fun get(id: Uuid): SshKeyInfo? =
        db.device_ssh_keyQueries.get(id.toString()).executeAsOneOrNull()?.toSshKeyInfo()
            ?: db.vault_ssh_keyQueries.get(id.toString()).executeAsOneOrNull()?.toSshKeyInfo()

    override suspend fun getSecret(id: Uuid): SshKeySecret? =
        db.device_ssh_keyQueries.get(id.toString()).executeAsOneOrNull()
            ?.let { SshKeySecret.Device(it.path, it.passphrase) }
            ?: db.vault_ssh_keyQueries.get(id.toString()).executeAsOneOrNull()
                ?.let { SshKeySecret.Vault(it.private_key) }

    override suspend fun insert(
        name: String,
        algorithm: Algorithm,
        secret: SshKeySecret,
    ): SshKeyInfo = when (secret) {
        is SshKeySecret.Device -> Device_ssh_keys(
            id = generateUuid().toString(),
            name = name,
            algorithm = algorithm,
            path = secret.path,
            passphrase = secret.passphrase,
        ).also(db.device_ssh_keyQueries::insert)
            .toSshKeyInfo()

        is SshKeySecret.Vault -> Vault_ssh_keys(
            id = generateUuid().toString(),
            name = name,
            algorithm = algorithm,
            private_key = secret.privateKey,
        ).also(db.vault_ssh_keyQueries::insert)
            .toSshKeyInfo()
    }

    override suspend fun updateName(id: Uuid, name: String): SshKeyInfo? {
        db.vault_ssh_keyQueries.update(name, id.toString())
        db.device_ssh_keyQueries.update(name, id.toString())
        return get(id)
    }

    override suspend fun delete(id: Uuid) {
        db.vault_ssh_keyQueries.delete(id.toString())
        db.device_ssh_keyQueries.delete(id.toString())
    }

    private fun Device_ssh_keys.toSshKeyInfo() = SshKeyInfo(
        id = Uuid.parse(id),
        name = name,
        algorithm = algorithm,
        type = SshKeyType.DEVICE,
    )

    private fun Vault_ssh_keys.toSshKeyInfo() = SshKeyInfo(
        id = Uuid.parse(id),
        name = name,
        algorithm = algorithm,
        type = SshKeyType.VAULT,
    )
}
