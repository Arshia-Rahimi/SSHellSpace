package com.sshellspace.feature.vault.domain

/** Copies hosts out of `~/.ssh/config` once, when the user asks. The app never reads that file while connecting. */
interface SshConfigImporter {
    /** The hosts in the config file. Empty when there is no file or it names no concrete hosts. */
    suspend fun preview(): List<SshConfigHost>

    /** Saves [hosts] as connections, along with any jump hosts between them. */
    suspend fun import(hosts: List<SshConfigHost>): SshConfigImportResult
}

data class SshConfigHost(
    val alias: String,
    val hostName: String,
    val user: String?,
    val port: Int,
    val identityFile: String?,
    val proxyJump: String?,
    val compression: Boolean,
    val keepAliveSeconds: Int?,
    val connectTimeoutSeconds: Int?,
    /** Settings in the file this app can't reproduce, so the user isn't surprised later. */
    val warnings: List<String> = emptyList(),
    /** A connection with this name exists, so importing would be a duplicate. */
    val alreadySaved: Boolean = false,
)

data class SshConfigImportResult(
    val imported: Int,
    val problems: List<String>,
)
