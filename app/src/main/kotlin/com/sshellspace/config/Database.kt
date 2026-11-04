package com.sshellspace.config

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.common.createOwnerOnlyFile
import com.sshellspace.core.common.restrictToOwner
import com.sshellspace.core.data.generated.Database
import java.io.File
import java.util.Properties
import net.harawata.appdirs.AppDirsFactory

private fun userDataDirectory() = File(AppDirsFactory.getInstance().getUserDataDir("SSHellSpace", null, null))

fun vaultsDirectory() = File(userDataDirectory(), "vaults")

fun createSqliteDriver(): JdbcSqliteDriver {
    val directory = userDataDirectory()
    directory.mkdirs()
    restrictToOwner(directory.toPath())

    // Until its data moves into a vault, this file can hold saved passwords and keys.
    val databaseFile = File(directory, "sshellspace.db")
    if (databaseFile.exists()) restrictToOwner(databaseFile.toPath()) else createOwnerOnlyFile(databaseFile.toPath())
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${databaseFile.absolutePath}",
        schema = Database.Schema,
        properties = Properties().apply { put("foreign_keys", "true") },
    )
}
