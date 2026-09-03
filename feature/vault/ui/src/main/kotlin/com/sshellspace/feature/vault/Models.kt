package com.sshellspace.feature.vault

import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.SshKeyInfo
import kotlin.uuid.Uuid

data class SshKey(
    val id: Uuid,
    val name: String,
    val algorithm: Algorithm,
)

fun SshKeyInfo.toSshKey() = SshKey(
    id = this.id,
    name = this.name,
    algorithm = this.algorithm,
)
