package io.ntole.kvizic.core.network.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.ApiException
import io.ntole.kvizic.core.network.BASE_URL
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.KvizicJson
import io.ntole.kvizic.core.network.PROFILE
import io.ntole.kvizic.core.network.jsonHeaders
import io.ntole.kvizic.core.network.respondErrorDto
import io.ntole.kvizic.core.network.respondProfile
import io.ntole.kvizic.core.network.session
import io.ntole.kvizic.core.network.storeHolding
import io.ntole.kvizic.core.player.SetAvatarRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The session player's own endpoints, each with the session's bearer. */
class PlayerApiTest {
    private val requests = mutableListOf<Sent>()

    @Test
    fun `the profile is read with the session's bearer`() =
        runTest {
            val engine =
                MockEngine { request ->
                    requests +=
                        Sent(request.method, request.url.encodedPath, request.headers[HttpHeaders.Authorization])
                    respondProfile()
                }

            val profile = api(engine).me()

            assertEquals(PROFILE, profile)
            assertEquals(listOf(Sent(HttpMethod.Get, KvizicApi.Paths.ME, "Bearer access-a")), requests)
        }

    @Test
    fun `an avatar is posted by its id and answered with the profile after it`() =
        runTest {
            var sent: SetAvatarRequest? = null
            val engine =
                MockEngine { request ->
                    requests +=
                        Sent(request.method, request.url.encodedPath, request.headers[HttpHeaders.Authorization])
                    sent = KvizicJson.decodeFromString<SetAvatarRequest>(request.body.toByteArray().decodeToString())
                    respond(KvizicJson.encodeToString(PROFILE.copy(avatarId = "owl")), HttpStatusCode.OK, jsonHeaders)
                }

            val profile = api(engine).setAvatar("owl")

            assertEquals("owl", profile.avatarId)
            assertEquals(SetAvatarRequest("owl"), sent)
            assertEquals(listOf(Sent(HttpMethod.Post, KvizicApi.Paths.MY_AVATAR, "Bearer access-a")), requests)
        }

    @Test
    fun `an avatar the server does not have comes back as its code`() =
        runTest {
            val engine = MockEngine { respondErrorDto(HttpStatusCode.BadRequest, ErrorCode.INVALID_AVATAR) }

            val refused = assertFailsWith<ApiException> { api(engine).setAvatar("dragon") }

            assertEquals(ErrorCode.INVALID_AVATAR, refused.code)
        }

    @Test
    fun `a deletion goes with the session's bearer and takes a 204`() =
        runTest {
            val engine =
                MockEngine { request ->
                    requests +=
                        Sent(request.method, request.url.encodedPath, request.headers[HttpHeaders.Authorization])
                    respond("", HttpStatusCode.NoContent)
                }

            api(engine).deleteAccount()

            assertEquals(listOf(Sent(HttpMethod.Post, KvizicApi.Paths.ME_DELETION, "Bearer access-a")), requests)
        }

    private fun api(engine: MockEngine): PlayerApi =
        PlayerApi(KvizicHttpClient.create(BASE_URL, storeHolding(session("a")), engine))

    /** A request as it arrived: its method, its path and its `Authorization` header. */
    private data class Sent(
        val method: HttpMethod,
        val path: String,
        val authorization: String?,
    )
}
