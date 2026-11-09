package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.StringOrNullSerializer
import com.admoai.sdk.serialization.WholeIntOrNullSerializer
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
                MetadataPriority.STANDARD -> "standard"
                MetadataPriority.HOUSE -> "house"
                MetadataPriority.UNKNOWN -> "unknown"
            }
        )
    }

    override fun deserialize(decoder: Decoder): MetadataPriority {
        // Tolerant of a non-string too (a retyped `priority` used to throw here, which nulled the
        // entire metadata block via CreativeMetadataOrNullSerializer).
        val raw = runCatching { decoder.decodeString() }.getOrNull()
        return when (raw) {
            "sponsorship" -> MetadataPriority.SPONSORSHIP
            "standard" -> MetadataPriority.STANDARD
            "house" -> MetadataPriority.HOUSE
            else -> MetadataPriority.UNKNOWN
        }
    }
}

/**
 * Creative metadata.
 *
 * Every field has a default. That is deliberate: these were previously non-null with no default,
 * so a single missing or retyped id made the whole object fail to decode, and
 * `CreativeMetadataOrNullSerializer` turned that into `metadata = null`. The block would then take
 * `impId`, `isSkippable`, `skipOffsetSeconds` and `endCardMode` down with it — and `isSkippable()`
 * would silently fall through to the content fields and could contradict what the engine
 * explicitly marked.
 *
 * Degrading per-field instead matches iOS and Flutter, which both default the id fields to "".
 */
@Serializable
data class CreativeMetadata(
    val adId: String = "",
    val creativeId: String = "",
    val advertiserId: String? = null,
    val placementId: String = "",
    val templateId: String = "",
    val priority: MetadataPriority = MetadataPriority.UNKNOWN,
    val language: String? = null,
    val style: String? = null,
    // Video-specific metadata (2025-11-01+)
    val format: String? = null,
    val duration: Int? = null,
    val aspectRatio: String? = null,
    val isSkippable: Boolean? = null,
    // Journey / render-attribution metadata (2025-11-01+)
    val impId: String? = null,
    val skipOffsetSeconds: Int? = null,
    val endCardMode: String? = null,
    // Standard native formats (the engine omits all three while the standard formats are off)
    /**
     * The standard native format this creative is served as (`card`, `carousel`, `expandable_banner`,
     * `branded_assets`, `standard_video`, `sponsored_pin_location`). Absent when the engine does not
     * send it, never an empty string. Open-set: an unknown value is kept verbatim.
     */
    @Serializable(with = StringOrNullSerializer::class)
    val effectiveStandardFormatKey: String? = null,
    /** The version of that standard format. Absent when not sent or not a number. */
    @Serializable(with = WholeIntOrNullSerializer::class)
    val effectiveStandardFormatVersion: Int? = null,
    /**
     * Where the creative's template comes from: `admoai_standard` (the standard itself) or
     * `based_on_standard` (an operator's template built on it). Open-set: an unknown value is kept
     * verbatim, and absent is `null`.
     */
    @Serializable(with = StringOrNullSerializer::class)
    val templateSource: String? = null,
    /**
     * What the creative's assets stand for, named by the format that has one: Branded Assets answers
     * `vehicle_marker`. Absent (`null`) on every other creative, and when the engine does not send it.
     * Open-set: an unknown value is kept verbatim.
     */
    @Serializable(with = StringOrNullSerializer::class)
    val assetContext: String? = null
) {
    /**
     * The constructor of the last release, 1.7.2, kept so apps and precompiled libraries built
     * against it keep linking: its 15 parameters and the same default values, which also keeps the
     * synthetic default-arguments constructor a Kotlin caller compiled against 1.7.2 calls. The
     * standard-format fields and [assetContext] are left absent (`null`).
     *
     * HIDDEN so Kotlin source never resolves to it (a call naming some fields would otherwise be
     * ambiguous with the primary constructor). It is therefore synthetic on the JVM: compiled
     * callers keep working, Java source written against 1.7.2 has to pass the four new arguments.
     */
    @Deprecated("Use the primary constructor", level = DeprecationLevel.HIDDEN)
    constructor(
        adId: String = "",
        creativeId: String = "",
        advertiserId: String? = null,
        placementId: String = "",
        templateId: String = "",
        priority: MetadataPriority = MetadataPriority.UNKNOWN,
        language: String? = null,
        style: String? = null,
        format: String? = null,
        duration: Int? = null,
        aspectRatio: String? = null,
        isSkippable: Boolean? = null,
        impId: String? = null,
        skipOffsetSeconds: Int? = null,
        endCardMode: String? = null
    ) : this(
        adId, creativeId, advertiserId, placementId, templateId, priority, language, style, format,
        duration, aspectRatio, isSkippable, impId, skipOffsetSeconds, endCardMode,
        null, null, null, null
    )
}
