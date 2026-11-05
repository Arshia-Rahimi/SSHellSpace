package com.sshellspace.core.data

import kotlinx.coroutines.flow.Flow

/** The current vault's own list of trusted host keys, as lines in the OpenSSH known_hosts format. */
interface KnownHostDatasource {
    fun index(): Flow<List<String>>
    suspend fun lines(): List<String>

    /** Adds [lines] all at once, leaving out any that are already here. */
    suspend fun add(lines: List<String>)
    suspend fun replace(old: String, new: String)
    suspend fun delete(line: String)
}
