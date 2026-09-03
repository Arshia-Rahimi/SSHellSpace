package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.PrivateKey
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.SshKeySecret
import com.sshellspace.feature.vault.domain.CreateSshKeyResult
import com.sshellspace.feature.vault.domain.SshKeyRepository
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SshKeyRepositoryImpl(
    private val sshKeyDatasource: SshKeyDatasource,
    private val sshKeyService: SshKeyService,
) : SshKeyRepository {
    override fun getKeys(): Flow<List<SshKeyInfo>> =
        sshKeyDatasource.index()

    override fun validate(privateKey: PrivateKey, passphrase: String?) =
        sshKeyService.validate(privateKey, passphrase)

    override suspend fun createVaultKey(name: String, comment: String, algorithm: Algorithm) =
        try {
            val privateKey = sshKeyService.generate(comment, null, algorithm).first
            sshKeyDatasource.insert(name, algorithm, SshKeySecret.Vault(privateKey))
            CreateSshKeyResult.Success
        } catch (e: Exception) {
            CreateSshKeyResult.Error(e.message.toString())
        }

    override suspend fun createDeviceKey(
        name: String,
        comment: String,
        fileName: String?,
        path: String,
        algorithm: Algorithm,
    ): CreateSshKeyResult = withContext(Dispatchers.IO) {
        val fileName = if (fileName.isNullOrBlank()) "SSHellSpace_${name}_$algorithm" else fileName
        val privateFile = File(path, fileName)
        val publicFile = File(path, "$fileName.pub")
        if (privateFile.exists()) {
            return@withContext CreateSshKeyResult.Error("$privateFile exists")
        }
        if (publicFile.exists()) {
            return@withContext CreateSshKeyResult.Error("$publicFile exists")
        }

        val keyPair = sshKeyService.generate(comment, null, algorithm)
        File(path).mkdirs()
        privateFile.createNewFile()
        privateFile.writeText(keyPair.first)
        publicFile.createNewFile()
        publicFile.writeText(keyPair.second)

        sshKeyDatasource.insert(name, algorithm, SshKeySecret.Device(privateFile.path, null))
        CreateSshKeyResult.Success
    }

    override suspend fun importDeviceKey(name: String, path: String, passphrase: String?): CreateSshKeyResult =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) {
                    return@withContext CreateSshKeyResult.Error("File $path does not exist")
                }
                val fileContents = file.readText()
                return@withContext when (val validationResult = validate(fileContents, passphrase)) {
                    SshKeyValidationResult.InvalidPassphrase -> CreateSshKeyResult.Error("Invalid Passphrase")
                    is SshKeyValidationResult.InvalidSshKeyFormat -> CreateSshKeyResult.Error("Key format is not correct")
                    is SshKeyValidationResult.Success -> {
                        sshKeyDatasource.insert(
                            name,
                            algorithm = validationResult.algorithm,
                            secret = SshKeySecret.Device(path, passphrase),
                        )
                        CreateSshKeyResult.Success
                    }
                }
            } catch (e: Exception) {
                return@withContext CreateSshKeyResult.Error(e.message.toString())
            }
        }

    override suspend fun updateName(id: Uuid, name: String) {
        sshKeyDatasource.updateName(id, name)
    }

    override suspend fun delete(id: Uuid) {
        sshKeyDatasource.delete(id)
    }
}
