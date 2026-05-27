package com.admoai.sdk.model.response

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Priority level of a creative, as returned by the decision engine.
 *
 * Unknown future values from the server decode to [UNKNOWN] rather than
 * throwing a SerializationException, so new priority tiers added server-side
 * never crash the SDK.
 */
@Serializable(with = MetadataPrioritySerializer::class)
enum class MetadataPriority {
    SPONSORSHIP,
    STANDARD,
    HOUSE,

    /** Fallback for any server value not recognised by this SDK version. */
    UNKNOWN
}

internal object MetadataPrioritySerializer : KSerializer<MetadataPriority> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MetadataPriority", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MetadataPriority) {
        encoder.encodeString(
            when (value) {
                MetadataPriority.SPONSORSHIP -> "sponsorship"
                MetadataPriority.STANDARD    -> "standard"
                MetadataPriority.HOUSE       -> "house"
                MetadataPriority.UNKNOWN     -> "unknown"
            }
        )
    }

    override fun deserialize(decoder: Decoder): MetadataPriority {
        return when (decoder.decodeString()) {
            "sponsorship" -> MetadataPriority.SPONSORSHIP
            "standard"    -> MetadataPriority.STANDARD
            "house"       -> MetadataPriority.HOUSE
            else          -> MetadataPriority.UNKNOWN
        }
    }
}

@Serializable
data class CreativeMetadata(
    val adId: String,
    val creativeId: String,
    val advertiserId: String? = null,
    val placementId: String,
    val templateId: String,
    val priority: MetadataPriority,
    val language: String? = null,
    val style: String? = null,
    // Video-specific metadata (2025-11-01+)
    val format: String? = null,
    val duration: Int? = null,
    val aspectRatio: String? = null,
    val isSkippable: Boolean? = null
)
