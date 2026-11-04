package com.sshellspace.core.domain.models

import kotlin.math.log2
import kotlin.uuid.Uuid

const val VAULT_FILE_EXTENSION = "sshv"

/** Below this, the app warns that a password is easy to guess. It doesn't refuse it. */
const val RECOMMENDED_VAULT_PASSWORD_LENGTH = 12

data class VaultInfo(val id: Uuid, val name: String, val hasPassword: Boolean = true)

/** What proves the user may open a vault. Biometric unlock would be another case here, with its own key slot. */
sealed interface VaultCredential {
    data class Password(val value: String) : VaultCredential {
        override fun toString() = "Password(***)"
    }

    /** For a vault with no password, which opens for anyone who has the file. */
    data object None : VaultCredential
}

enum class PasswordStrength { Weak, Fair, Strong }

private const val MIN_UNIQUE_CHARACTERS = 6
private const val FAIR_BITS = 50.0
private const val STRONG_BITS = 80.0
private val COMMON_PASSWORD_PARTS = listOf("password", "qwerty", "123456", "letmein", "admin", "sshellspace")

/** A rough estimate from length and character variety. It can't spot every guessable phrase, only the obvious ones. */
fun vaultPasswordStrength(password: String): PasswordStrength {
    val lowered = password.lowercase()
    if (password.toSet().size < MIN_UNIQUE_CHARACTERS || COMMON_PASSWORD_PARTS.any { it in lowered }) {
        return PasswordStrength.Weak
    }
    val pool = listOf(
        26.takeIf { password.any(Char::isLowerCase) },
        26.takeIf { password.any(Char::isUpperCase) },
        10.takeIf { password.any(Char::isDigit) },
        33.takeIf { password.any { !it.isLetterOrDigit() } },
    ).sumOf { it ?: 0 }
    val bits = password.length * log2(pool.toDouble())
    return when {
        bits < FAIR_BITS -> PasswordStrength.Weak
        bits < STRONG_BITS -> PasswordStrength.Fair
        else -> PasswordStrength.Strong
    }
}

sealed class VaultException(message: String) : Exception(message) {
    class WrongCredential : VaultException("Wrong password.")

    /** The vault can't be used: a damaged or foreign file, a name already taken, a version this app can't read. */
    class Invalid(message: String) : VaultException(message)
}
