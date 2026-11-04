package com.sshellspace.core.data.vault

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * The driver behind every vault datasource, passing calls on to whichever vault is unlocked. Datasources are singletons,
 * so this lets them outlive a vault without holding on to its data.
 *
 * Listeners stay here rather than on the vault's own driver so that a long-lived query survives a lock and refreshes when
 * the next vault unlocks.
 */
class VaultSqlDriver : SqlDriver {
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()

    @Volatile
    private var delegate: SqlDriver? = null

    @Volatile
    private var persist: () -> Unit = {}

    internal fun attach(driver: SqlDriver, persist: () -> Unit) {
        this.persist = persist
        delegate = driver
        synchronized(listeners) { listeners.values.flatten().toSet() }.forEach(Query.Listener::queryResultsChanged)
    }

    internal fun detach() {
        delegate = null
        persist = {}
    }

    private fun current() = checkNotNull(delegate) { "No vault is unlocked." }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ) = current().executeQuery(identifier, sql, mapper, parameters, binders)

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ) = current().execute(identifier, sql, parameters, binders)

    override fun newTransaction(): QueryResult<Transacter.Transaction> = current().newTransaction()

    override fun currentTransaction(): Transacter.Transaction? = current().currentTransaction()

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners.getOrPut(it) { linkedSetOf() }.add(listener) } }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners[it]?.remove(listener) } }
    }

    /** SQLDelight calls this after every committed write, which makes it the point to save the vault. */
    override fun notifyListeners(vararg queryKeys: String) {
        persist()
        synchronized(listeners) { queryKeys.flatMap { listeners[it].orEmpty() }.toSet() }
            .forEach(Query.Listener::queryResultsChanged)
    }

    override fun close() = Unit
}
