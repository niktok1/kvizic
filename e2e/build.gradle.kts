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
