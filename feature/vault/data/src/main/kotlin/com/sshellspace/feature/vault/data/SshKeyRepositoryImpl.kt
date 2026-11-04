package com.sshellspace.feature.vault.data

import com.sshellspace.core.common.createOwnerOnlyFile
import com.sshellspace.core.common.restrictToOwner
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.PrivateKey
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.SshKeySecret
import com.sshellspace.feature.vault.domain.CreateSshKeyResult
import com.sshellspace.feature.vault.domain.SshKeyDetails
import com.sshellspace.feature.vault.domain.SshKeyRepository
import java.io.File
import java.io.IOException
import java.nio.file.Files
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
        passphrase: String?,
    ): CreateSshKeyResult = withContext(Dispatchers.IO) {
        val fileName = if (fileName.isNullOrBlank()) "SSHellSpace_${name}_$algorithm" else fileName
        if (File(fileName).name != fileName) {
            return@withContext CreateSshKeyResult.Error("The file name can't contain a folder")
        }
        val privateFile = File(path, fileName)
        val publicFile = File(path, "$fileName.pub")
        if (privateFile.exists()) {
            return@withContext CreateSshKeyResult.Error("$privateFile exists")
        }
        if (publicFile.exists()) {
            return@withContext CreateSshKeyResult.Error("$publicFile exists")
        }

        try {
            val keyPair = sshKeyService.generate(comment, passphrase, algorithm)
            val directory = File(path)
            if (!directory.exists()) {
                Files.createDirectories(directory.toPath())
                restrictToOwner(directory.toPath())
            }
            // Created owner-only before anything secret goes in. OpenSSH also refuses keys others can read.
            createOwnerOnlyFile(privateFile.toPath())
            privateFile.writeText(keyPair.first)
            publicFile.createNewFile()
            publicFile.writeText(keyPair.second)

            sshKeyDatasource.insert(name, algorithm, SshKeySecret.Device(privateFile.path, passphrase))
            CreateSshKeyResult.Success
        } catch (e: IOException) {
            CreateSshKeyResult.Error(e.message ?: "Couldn't write the key files")
        }
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
                    is SshKeyValidationResult.InvalidSshKeyFormat -> CreateSshKeyResult.Error(validationResult.errorMessage)
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

    override suspend fun importVaultKey(name: String, privateKey: PrivateKey, passphrase: String?): CreateSshKeyResult =
        withContext(Dispatchers.IO) {
            try {
                when (val validationResult = validate(privateKey, passphrase)) {
                    SshKeyValidationResult.InvalidPassphrase -> CreateSshKeyResult.Error("Invalid Passphrase")
                    is SshKeyValidationResult.InvalidSshKeyFormat -> CreateSshKeyResult.Error(validationResult.errorMessage)
                    is SshKeyValidationResult.Success -> {
                        val decrypted = sshKeyService.load(privateKey, passphrase).first
                        sshKeyDatasource.insert(name, validationResult.algorithm, SshKeySecret.Vault(decrypted))
                        CreateSshKeyResult.Success
                    }
                }
            } catch (e: Exception) {
                CreateSshKeyResult.Error(e.message.toString())
            }
        }

    override suspend fun getDetails(id: Uuid): SshKeyDetails? = withContext(Dispatchers.IO) {
        val info = sshKeyDatasource.get(id) ?: return@withContext null
        val secret = sshKeyDatasource.getSecret(id) ?: return@withContext null
        val path = (secret as? SshKeySecret.Device)?.path
        val passphrase = (secret as? SshKeySecret.Device)?.passphrase
        try {
            SshKeyDetails(info, sshKeyService.load(secret.privateKey, passphrase).second, path)
        } catch (e: Exception) {
            SshKeyDetails(info, publicKey = null, path = path, error = e.message ?: "Failed to read key")
        }
    }

    override suspend fun updateName(id: Uuid, name: String) {
        sshKeyDatasource.updateName(id, name)
    }

    override suspend fun delete(id: Uuid) {
        sshKeyDatasource.delete(id)
    }
}
