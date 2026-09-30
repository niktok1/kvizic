package io.ntole.kvizic.server.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Prints the DDL the table definitions describe, on H2, lower-cased as the migrations are written: the
 * draft of the next migration. `./gradlew :server:printSchema`.
 */
fun main() {
    val database = Database.connect("jdbc:h2:mem:print-schema;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction(database) {
        SchemaUtils.createStatements(*appTables).forEach { statement -> println("${statement.lowercase()};") }
    }
}
