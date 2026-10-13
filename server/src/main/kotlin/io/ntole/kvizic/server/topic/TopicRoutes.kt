package io.ntole.kvizic.server.topic

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.core.topic.CreateTopicRequest
import io.ntole.kvizic.core.topic.RenameTopicRequest
import io.ntole.kvizic.core.topic.TopicListDto
import io.ntole.kvizic.server.admin.AdminToken
import io.ntole.kvizic.server.admin.logAdmin
import io.ntole.kvizic.server.admin.requireAdmin
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject

/**
 * The topics, for anyone: a client can have them before it has a player. Limited per address. Each topic
 * counts the questions the caller's country could be asked solo, by [KvizicApi.Headers.COUNTRY]: none (a 1.0.x
 * client) is Serbia, a name this build does not know [Country.UNKNOWN]. Five queries, whatever the topics.
 */
fun Route.topicRoutes(db: Db) {
    rateLimit(RouteLimit.TOPICS) {
        get(KvizicApi.Paths.TOPICS) {
            val country = Country.parse(call.request.headers[KvizicApi.Headers.COUNTRY]) ?: Country.RS
            call.response.header(HttpHeaders.Vary, KvizicApi.Headers.COUNTRY)
            call.respond(db.query { TopicListDto(TopicStore.all(country), TopicStore.groups()) })
        }
    }
}

/**
 * The moderator adding topics and putting their names right, in Serbian and English alone: a topic's names in
 * the other languages come from a migration until a topics tab can write them. Inside `adminRoutes`.
 */
fun Route.topicAdminRoutes(
    db: Db,
    adminToken: AdminToken,
    catalog: TopicCatalog,
) {
    post(KvizicApi.Paths.ADMIN_TOPICS) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<CreateTopicRequest>("topic")
        val topic = checkedTopic(request.id, request.nameSr, request.nameEn)
        val created = db.query { TopicStore.create(topic) }
        catalog.refresh()
        call.logAdmin("added topic", created.id)
        call.respond(HttpStatusCode.Created, created)
    }

    post(KvizicApi.Paths.ADMIN_TOPIC_RENAMES) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<RenameTopicRequest>("topic renaming")
        val topic = checkedTopic(request.id, request.nameSr, request.nameEn)
        val renamed = db.query { TopicStore.rename(topic) }
        call.logAdmin("renamed topic", renamed.id)
        call.respond(renamed)
    }
}
