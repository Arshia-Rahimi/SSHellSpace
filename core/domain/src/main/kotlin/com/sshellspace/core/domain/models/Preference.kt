package com.sshellspace.core.domain.models

interface Preference {
    val qualifiedName: String
        get() = this::class.qualifiedName!!
}
