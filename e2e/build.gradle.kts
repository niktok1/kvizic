plugins {
    alias(libs.plugins.kotlinJvm)
}

// Real clients against the real server, in one process: the server on Netty, each player's lobby session
// over CIO's WebSockets, behind a proxy that can slow a connection or cut it. Nothing here ships.
dependencies {
    testImplementation(project(":server"))
    testImplementation(project(":core:domain"))
    testImplementation(project(":core:network"))
    testImplementation(project(":core:data"))
    testImplementation(libs.ktor.serverCore)
    testImplementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.clientCio)
    // Android's engine, whose socket refused a client frame limit the desktop's took (KtorPlayTransport).
    testImplementation(libs.ktor.clientOkhttp)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.kotlin.testJunit)
}

// The load test is no check of a commit: it takes minutes and measures this machine. `test` leaves it out,
// and `./gradlew :e2e:loadTest` runs it alone (`-Pkvizic.load.players=96` for fewer players).
tasks.test {
    filter {
        excludeTestsMatching("*LoadTest")
        excludeTestsMatching("*ProdSmokeTest")
    }
}

// The check after a prod deploy (deploy-prod.yml): two guests on the server -Pkvizic.smoke.baseUrl names.
val smokeTest by tasks.registering(Test::class) {
    description = "Seats two guests in a room on a deployed server and starts a game they leave at once."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*ProdSmokeTest") }
    providers.gradleProperty("kvizic.smoke.baseUrl").orNull?.let { systemProperty("kvizic.smoke.baseUrl", it) }
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}

val loadTest by tasks.registering(Test::class) {
    description = "Plays a game in every room of 480 real clients at once and reports the fan-out."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*LoadTest") }
    providers.gradleProperty("kvizic.load.players").orNull?.let { systemProperty("kvizic.load.players", it) }
    maxHeapSize = "2g"
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}
