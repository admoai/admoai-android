package com.admoai.sdk.serialization

import com.admoai.sdk.model.common.Error
import com.admoai.sdk.model.common.Warning
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Advertiser
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.contentTypeFromWire
import com.admoai.sdk.model.response.CreativeJourney
import com.admoai.sdk.model.response.CreativeMetadata
import com.admoai.sdk.model.response.TemplateInfo
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.VastData
import com.admoai.sdk.model.response.VerificationScriptResource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Decodes a JSON array element-by-element, dropping any element that fails to decode; null/non-array
 * → empty list. The Tolerant Reader "drop malformed list entries" primitive — one bad element never
 * aborts the whole response.
 */
internal open class DropMalformedListSerializer<T>(
    private val element: KSerializer<T>
) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            runCatching { jsonDecoder.json.decodeFromJsonElement(element, el) }.getOrNull()
        }
    }
}

/**
 * Decodes a single object, returning [default] on any failure. Lets a required sub-object degrade
 * to a safe default instead of dropping its parent (keeps a renderable creative alive).
 */
internal open class DefaultOnErrorSerializer<T>(
    private val delegate: KSerializer<T>,
    private val default: T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: T) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): T {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return runCatching { delegate.deserialize(decoder) }.getOrDefault(default)
        val element = jsonDecoder.decodeJsonElement()
        return runCatching { jsonDecoder.json.decodeFromJsonElement(delegate, element) }.getOrDefault(default)
    }
}

/**
 * Tolerant decode for OM `verificationParameters` (engine type `any`): string verbatim; object/array
 * preserved as compact JSON text; null → null. Keeps the public `String?` type (non-breaking).
 */
@OptIn(ExperimentalSerializationApi::class)
internal object VerificationParametersSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VerificationParameters", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return runCatching { decoder.decodeString() }.getOrNull()
        return when (val el = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.content
            else -> el.toString() // object/array → compact JSON text, data preserved
        }
    }
}

/**
 * Decodes a `contents[]` entry, keeping the raw wire `type` alongside the parsed enum.
 *
 * `type` used to deserialize straight into the closed [ContentType] enum. `coerceInputValues` does
 * NOT rescue an unknown enum string (it only maps null to a declared default), so an unrecognised
 * type threw and [DropMalformedListSerializer] discarded the whole entry — the content field simply
 * vanished from the creative. That was live, not theoretical: `dropdown` is allowed by the engine's
 * `template_fields_valid_type` CHECK and was absent from the enum, so Android lost every dropdown
 * field while iOS and Flutter rendered it.
 *
 * Unknown types now decode to [ContentType.UNKNOWN] with the real string preserved in
 * `rawType`, so the entry survives and a publisher can still handle a type newer than the SDK.
 *
 * `value` also defaults to [JsonNull] rather than being required — the same drop-the-entry failure
 * mode, and iOS and Flutter both tolerate an absent value.
 */
internal object ContentSerializer : KSerializer<Content> {
    override val descriptor: SerialDescriptor = ContentSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Content) {
        ContentSurrogate.serializer().serialize(
            encoder,
            ContentSurrogate(key = value.key, value = value.value, type = value.rawType)
        )
    }

    override fun deserialize(decoder: Decoder): Content {
        val surrogate = ContentSurrogate.serializer().deserialize(decoder)
        return Content(
            key = surrogate.key,
            value = surrogate.value,
            type = contentTypeFromWire(surrogate.type),
            rawType = surrogate.type
        )
    }

    /** Wire shape of a content entry: `type` stays a String so an unknown value cannot throw. */
    @Serializable
    private data class ContentSurrogate(
        val key: String,
        val value: JsonElement = JsonNull,
        val type: String
    )
}

/**
 * Decodes an optional object, returning null on any failure — a malformed optional field degrades
 * to null instead of dropping the whole (renderable) creative. Nullable counterpart of [DefaultOnErrorSerializer].
 */
@OptIn(ExperimentalSerializationApi::class)
internal open class NullOnErrorSerializer<T : Any>(
    private val delegate: KSerializer<T>
) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = delegate.descriptor.nullable

    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) encoder.encodeNull() else delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): T? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return runCatching { delegate.deserialize(decoder) }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        return runCatching { jsonDecoder.json.decodeFromJsonElement(delegate, element) }.getOrNull()
    }
}

// Concrete drop-malformed list serializers (one no-arg type per element, for @Serializable(with=)).
internal object AdDataListSerializer : DropMalformedListSerializer<AdData>(AdData.serializer())
internal object ContentListSerializer : DropMalformedListSerializer<Content>(Content.serializer())
internal object CreativeListSerializer : DropMalformedListSerializer<Creative>(Creative.serializer())
internal object TrackingDetailListSerializer :
    DropMalformedListSerializer<TrackingDetail>(TrackingDetail.serializer())
internal object VerificationResourceListSerializer :
    DropMalformedListSerializer<VerificationScriptResource>(VerificationScriptResource.serializer())
internal object ErrorListSerializer : DropMalformedListSerializer<Error>(Error.serializer())
internal object WarningListSerializer : DropMalformedListSerializer<Warning>(Warning.serializer())

// Required sub-objects that degrade to an empty default instead of dropping the whole creative.
internal object AdvertiserOrDefaultSerializer :
    DefaultOnErrorSerializer<Advertiser>(Advertiser.serializer(), Advertiser())
internal object TrackingInfoOrDefaultSerializer :
    DefaultOnErrorSerializer<TrackingInfo>(TrackingInfo.serializer(), TrackingInfo())

// Optional sub-objects that degrade to null (never drop the creative) on a malformed value.
internal object CreativeMetadataOrNullSerializer :
    NullOnErrorSerializer<CreativeMetadata>(CreativeMetadata.serializer())
internal object TemplateInfoOrNullSerializer :
    NullOnErrorSerializer<TemplateInfo>(TemplateInfo.serializer())
internal object VastDataOrNullSerializer : NullOnErrorSerializer<VastData>(VastData.serializer())
internal object CreativeJourneyOrNullSerializer :
    NullOnErrorSerializer<CreativeJourney>(CreativeJourney.serializer())
