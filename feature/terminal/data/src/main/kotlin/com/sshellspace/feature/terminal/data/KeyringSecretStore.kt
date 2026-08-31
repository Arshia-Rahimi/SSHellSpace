package com.sshellspace.feature.terminal.data

import com.github.javakeyring.Keyring
import com.sshellspace.feature.terminal.domain.SecretStore
import com.sshellspace.feature.terminal.domain.models.Secret
import com.sshellspace.feature.terminal.domain.models.SecretInfo

class KeyringSecretStore : SecretStore {
    companion object {
        private const val SERVICE = "SSHellSpace"
    }

    private val keyring = Keyring.create()

    private fun SecretInfo.accountKey(): String =
        listOf(profileId, secretType).joinToString(separator = ":")

    override fun save(
        info: SecretInfo,
        secret: Secret,
    ) {
        keyring.setPassword(SERVICE, info.accountKey(), secret.value)
    }

    override fun read(secret: SecretInfo): Secret? = runCatching {
        keyring.getPassword(SERVICE, secret.accountKey())
    }.getOrNull()?.let(::Secret)

    override fun delete(info: SecretInfo) {
        runCatching { keyring.deletePassword(SERVICE, info.accountKey()) }
    }
}
