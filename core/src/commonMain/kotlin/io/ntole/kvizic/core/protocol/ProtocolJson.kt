package io.ntole.kvizic.core.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The one `Json` both sides of the socket use, so the discriminator and the fallbacks cannot drift apart.
 *
 * The wire rule, extended to the socket: every sealed type on the wire has an `Unknown` member that a
 * type this build does not know decodes to, registered below; every enum that can grow has `UNKNOWN`
 * as its default, which [Json.configuration]'s `coerceInputValues` lands an unknown value on; new
 * fields come with defaults, and `encodeDefaults` sends them so the other side can coerce. A newer
 * server then never breaks an older client, and the other way round.
 */
public val ProtocolJson: Json =
    Json {
        classDiscriminator = "t"
        encodeDefaults = true
        coerceInputValues = true
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule =
            SerializersModule {
                polymorphic(ClientMessage::class) { defaultDeserializer { ClientMessage.Unknown.serializer() } }
                polymorphic(ServerMessage::class) { defaultDeserializer { ServerMessage.Unknown.serializer() } }
                polymorphic(PhaseView::class) { defaultDeserializer { PhaseView.Unknown.serializer() } }
            }
    }
