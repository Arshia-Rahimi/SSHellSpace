package com.sshellspace.core.domain.repositories

/** Key-value storage inside the unlocked vault, for state that shouldn't sit in the unencrypted app database. */
interface VaultPreferencesDatasource : PreferencesDatasource
