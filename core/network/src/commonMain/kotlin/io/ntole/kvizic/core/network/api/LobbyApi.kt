package io.ntole.kvizic.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.lobby.CreateLobbyRequest
import io.ntole.kvizic.core.lobby.JoinLobbyRequest
import io.ntole.kvizic.core.lobby.LobbyListDto
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.PublicLobbyDto
import io.ntole.kvizic.core.lobby.TicketDto

/**
 * Lobbies over REST: every way to a seat answers a one-time ticket for the realtime socket. A rejoin is
 * a join by the same code, which keeps the seat.
 */
public class LobbyApi(
    private val client: HttpClient,
) {
    public suspend fun create(settings: LobbySettingsDto): TicketDto =
        client.post(KvizicApi.Paths.LOBBIES) { setBody(CreateLobbyRequest(settings)) }.body()

    public suspend fun join(code: String): TicketDto =
        client.post(KvizicApi.Paths.LOBBY_JOINS) { setBody(JoinLobbyRequest(code)) }.body()

    public suspend fun rejoin(): TicketDto = client.post(KvizicApi.Paths.LOBBY_REJOINS).body()

    public suspend fun quickPlay(): TicketDto = client.post(KvizicApi.Paths.QUICK_PLAY).body()

    public suspend fun solo(): TicketDto = client.post(KvizicApi.Paths.SOLO_RUNS).body()

    public suspend fun publicLobbies(): LobbyListDto = client.get(KvizicApi.Paths.LOBBIES).body()

    /** The lobby with [code] as the list shows it, public or private, before a seat is taken in it. */
    public suspend fun preview(code: String): PublicLobbyDto =
        client.get(KvizicApi.Paths.LOBBY_PREVIEWS.replace("{code}", code)).body()
}
