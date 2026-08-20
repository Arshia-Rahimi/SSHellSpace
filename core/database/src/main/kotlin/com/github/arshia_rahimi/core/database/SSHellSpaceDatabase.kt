package com.github.arshia_rahimi.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.arshia_rahimi.core.database.generated.SSHellSpaceDatabase
import java.io.File
import java.util.Properties
import net.harawata.appdirs.AppDirsFactory

fun createSSHellSpaceDatabase(): SSHellSpaceDatabase {
    val directory = AppDirsFactory.getInstance()
        .getUserDataDir("Singularity", null, null)
    File(directory).mkdirs()

    val databaseFile = File(directory, "singularity.db")
    val driver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${databaseFile.absolutePath}",
        schema = SSHellSpaceDatabase.Schema,
        properties = Properties().apply { put("foreign_keys", "true") },
    )

    if (!databaseFile.exists()) {
        SSHellSpaceDatabase.Schema.create(driver)
    }

    return SSHellSpaceDatabase(driver)
}
