package com.github.arshia_rahimi.sshellspace.core.common

import kotlinx.serialization.json.Json

val JsonSerializer = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}
