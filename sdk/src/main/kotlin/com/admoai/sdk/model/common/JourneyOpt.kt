package com.admoai.sdk.model.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Journey opt-in / opt-out state forwarded on the decision request.
 *
 * Wire values are `"in"` / `"out"` (Kotlin keywords, hence the enum names differ). The request
 * side is strict — only these two values are ever emitted. The response `optStatus` is read with
 * [JourneyOptTolerantSerializer] (open set: unknown → null).
 */
@Serializable(with = JourneyOptSerializer::class)
enum class JourneyOpt {
    OPT_IN,
    OPT_OUT;

    internal val wire: String
        get() = when (this) {
            OPT_IN -> "in"
            OPT_OUT -> "out"
        }

    companion object {
        /** Tolerant parse for response reads: unknown/blank/null → null, never throws. */
        fun fromWire(value: String?): JourneyOpt? = when (value?.trim()?.lowercase()) {
            "in" -> OPT_IN
            "out" -> OPT_OUT
            else -> null
        }
    }
}

/** Strict serializer (request-side default): encodes only `"in"`/`"out"`; throws on unknown decode. */
internal object JourneyOptSerializer : KSerializer<JourneyOpt> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JourneyOpt", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JourneyOpt) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): JourneyOpt =
        JourneyOpt.fromWire(decoder.decodeString())
            ?: throw SerializationException("Unknown JourneyOpt value")
}

/**
 * Tolerant serializer for the response `optStatus` (open set). Unknown, null, or wrong-type
 * values decode to null instead of throwing, so a future engine value can never break decoding.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object JourneyOptTolerantSerializer : KSerializer<JourneyOpt?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JourneyOptTolerant", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: JourneyOpt?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.wire)
    }

    override fun deserialize(decoder: Decoder): JourneyOpt? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return runCatching { decoder.decodeString() }.getOrNull()?.let(JourneyOpt::fromWire)
        val element = jsonDecoder.decodeJsonElement()
        val content = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return JourneyOpt.fromWire(content)
    }
}
