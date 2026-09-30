package io.ntole.kvizic.server.admin

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.AdminOverviewDto
import io.ntole.kvizic.core.question.StatusCountDto
import io.ntole.kvizic.core.question.TopicCountDto
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Matches
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.topic.TopicStore
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Duration.Companion.days

/** What is live on this instance right now. */
data class LiveCounts(
    val lobbies: Int = 0,
    val games: Int = 0,
    val connectedPlayers: Int = 0,
)

/** The bank and the server at a glance, for the moderator. Inside `adminRoutes`. */
fun Route.overviewRoutes(
    db: Db,
    adminToken: AdminToken,
    live: () -> LiveCounts,
) {
    get(KvizicApi.Paths.ADMIN_OVERVIEW) {
        call.requireAdmin(adminToken)
        val now = System.currentTimeMillis()
        val overview =
            db.query {
                val count = Questions.id.count()
                val byStatus =
                    Questions
                        .select(Questions.status, count)
                        .groupBy(Questions.status)
                        .map { row -> StatusCountDto(row[Questions.status], row[count].toInt()) }
                        .sortedBy { it.status.ordinal }
                val gamesToday =
                    Matches
                        .select(Matches.id)
                        .where { Matches.endedAt greaterEq now - 1.days.inWholeMilliseconds }
                        .count()
                        .toInt()
                Triple(byStatus, TopicStore.all().map { TopicCountDto(it.id, it.questionCount) }, gamesToday)
            }
        val counts = live()
        call.respond(
            AdminOverviewDto(
                byStatus = overview.first,
                approvedByTopic = overview.second,
                liveLobbies = counts.lobbies,
                liveGames = counts.games,
                connectedPlayers = counts.connectedPlayers,
                gamesToday = overview.third,
            ),
        )
    }
}
