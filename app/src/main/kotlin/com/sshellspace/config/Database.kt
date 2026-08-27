package com.sshellspace

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.shellspace.generated.Database
import java.io.File
import java.util.Properties
import net.harawata.appdirs.AppDirsFactory

fun createSqliteDriver(): JdbcSqliteDriver {
    val directory = AppDirsFactory.getInstance()
        .getUserDataDir("SSHellSpace", null, null)
    File(directory).mkdirs()

    val databaseFile = File(directory, "sshellspace.db")
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${databaseFile.absolutePath}",
        schema = Database.Schema,
        properties = Properties().apply { put("foreign_keys", "true") },
    )
}
