package com.sshellspace.core.domain.models

/** One entry of a known hosts list. */
data class KnownHost(
    /** The whole line in the OpenSSH known_hosts format, which also identifies it. */
    val line: String,
    /** The names it applies to, such as `example.com:2222`. Empty when hashed, which hides them even from this app. */
    val hosts: List<String>,
    val keyType: String,
    /** In the `SHA256:...` form a host key prompt shows, so the two can be compared by eye. */
    val fingerprint: String,
    val isRevoked: Boolean = false,
    val isCertificateAuthority: Boolean = false,
)
