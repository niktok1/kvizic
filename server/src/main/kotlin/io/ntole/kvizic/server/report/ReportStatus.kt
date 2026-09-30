package io.ntole.kvizic.server.report

/** Whether a report still waits for the moderator. Server-side only: never on the wire. */
enum class ReportStatus {
    OPEN,
    RESOLVED,
}
