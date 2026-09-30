package io.ntole.kvizic.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.events.Events
import io.ktor.server.application.ApplicationStopped
import io.ntole.kvizic.server.config.ServerConfig
import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseFactory {
    /**
     * Connects and migrates the schema to the latest script before anything reads a table. The pool
     * closes when [monitor] reports the application stopped, so tests that start and stop a server per
     * case never exhaust a real Postgres.
     */
    fun init(
        config: ServerConfig,
        monitor: Events,
    ): Database {
        val dataSource = HikariDataSource(poolConfig(config))
        monitor.subscribe(ApplicationStopped) { dataSource.close() }

        Migrations.migrate(dataSource)
        return Database.connect(dataSource)
    }

    /**
     * READ COMMITTED, PostgreSQL's own default, for every transaction. At this level a write to a row
     * another transaction holds waits for it and then applies to the committed row, so a counter must be
     * an SQL increment and any other read-then-write a compare-and-set.
     */
    internal fun poolConfig(config: ServerConfig): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            config.dbUser?.let { username = it }
            config.dbPassword?.let { password = it }
            maximumPoolSize = if (config.isEphemeralDatabase) 2 else POOL_SIZE
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }

    /** A small pool: the game writes once per finished game, and Render's small Postgres takes few connections. */
    private const val POOL_SIZE = 5
}
