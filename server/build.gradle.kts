plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "io.ntole.kvizic"
version = "0.1.0"

application {
    mainClass = "io.ntole.kvizic.server.ApplicationKt"
}

dependencies {
    // Shares the wire contract and the realtime protocol with the client.
    api(project(":core"))

    implementation(libs.logback)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.serializationJson)

    // The server's own calls to Google, for Play Games sign-in. The JSON is read by hand.
    implementation(libs.ktor.clientCio)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    // Schema migrations. H2 support is built into flyway-core; PostgreSQL's lives in its own module.
    implementation(libs.flyway.core)
    implementation(libs.flyway.databasePostgresql)

    // The dev and test database, so the server runs with no external Postgres. Never production.
    implementation(libs.h2)

    // Compares a migrated schema with the table definitions (SchemaDriftTest). Test scope only.
    testImplementation(libs.exposed.migrationJdbc)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.ktor.clientMock)
    testImplementation(libs.ktor.clientCio)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.kotlin.testJunit)
}

// Both the H2 and Postgres drivers register through META-INF/services/java.sql.Driver, and Flyway finds
// the databases it supports the same way: merge every copy, or the fat jar knows only one of them.
tasks.shadowJar {
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
}

// Prints the DDL the table definitions describe, the draft of the next migration, on H2.
tasks.register<JavaExec>("printSchema") {
    group = "help"
    description = "Prints the statements the table definitions need beyond the committed migrations."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "io.ntole.kvizic.server.db.PrintSchemaKt"
}

tasks.test {
    // The flow and schema tests switch to this database when it is set, so an H2 result never satisfies
    // a Postgres run.
    inputs.property("testJdbcUrl", providers.environmentVariable("KVIZIC_TEST_JDBC_URL").orElse(""))
}
