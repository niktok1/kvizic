package io.ntole.kvizic.server.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.MigrateResult
import javax.sql.DataSource

/**
 * The schema's history, which Flyway applies at every boot. Every change to the schema is a script in
 * `server/src/main/resources/db/migration`, `V<n>__<what_it_does>.sql`, one set for H2 and PostgreSQL
 * alike, lower case and unquoted. A script that has run never changes: Flyway refuses a changed checksum.
 * `Tables.kt` must describe what the scripts build; `SchemaDriftTest` holds the two together.
 *
 * Every migration leaves a schema the build before it still runs on: add before use, drop only once no
 * build a rollback could return to reads it.
 */
object Migrations {
    const val LOCATION: String = "classpath:db/migration"

    /**
     * Brings the database behind [dataSource] up to the latest script. Safe for several servers booting
     * at once on PostgreSQL, as a Render deploy does: each script runs under Flyway's advisory lock.
     */
    fun migrate(dataSource: DataSource): MigrateResult = configuration().dataSource(dataSource).load().migrate()

    /**
     * The one Flyway configuration, without a database. Clean, which drops everything, is refused
     * explicitly; a script whose name Flyway cannot parse fails the boot instead of being skipped.
     */
    internal fun configuration(): FluentConfiguration =
        Flyway
            .configure()
            .locations(LOCATION)
            .baselineOnMigrate(false)
            .cleanDisabled(true)
            .validateMigrationNaming(true)
}
