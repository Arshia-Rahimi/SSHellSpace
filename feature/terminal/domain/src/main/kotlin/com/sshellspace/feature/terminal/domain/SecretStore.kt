package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.Secret
import com.sshellspace.feature.terminal.domain.models.SecretInfo

interface SecretStore {
    fun save(info: SecretInfo, secret: Secret)
    fun read(secret: SecretInfo): Secret?
    fun delete(info: SecretInfo)
}
