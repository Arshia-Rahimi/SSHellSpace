package com.sshellspace.core.common

import kotlinx.serialization.json.Json

val JsonSerializer = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    isLenient = true
    prettyPrint = true
}
