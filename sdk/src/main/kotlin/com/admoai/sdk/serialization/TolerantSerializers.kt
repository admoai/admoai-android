package com.admoai.sdk.serialization

import com.admoai.sdk.model.common.Error
import com.admoai.sdk.model.common.Warning
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Advertiser
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.Creative
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
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
