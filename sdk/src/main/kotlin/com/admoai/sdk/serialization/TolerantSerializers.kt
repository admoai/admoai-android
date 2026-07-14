package com.admoai.sdk.serialization

import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Advertiser
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
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
 * Decodes a JSON array element-by-element, silently dropping any element that fails to decode.
 * A JSON null or a non-array input yields an empty list. Encoding is unchanged.
 *
 * This is the Tolerant Reader "drop malformed list entries" primitive (the analogue of the iOS
 * `SafelyDecodable<T>`): one bad element never aborts the whole response. It requires the [Decoder]
 * to be a [JsonDecoder] (always true on the SDK's JSON transport); otherwise it delegates.
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
 * Decodes a single object, returning [default] on ANY failure (null, wrong type, malformed).
 * Lets a required sub-object degrade to a safe default instead of taking down its parent — the
 * analogue of iOS's `try?`-per-field. Keeps a renderable creative alive when one nested field is bad.
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
 * Tolerant, non-breaking decode for OM `verificationParameters` (engine type is `any`). A JSON
 * string is used verbatim; a number/bool primitive uses its literal; an object/array is preserved
 * as compact JSON text; null/absent → null. Never throws, never a lossy `[object]` placeholder,
 * and keeps the public field type `String?` (source-compatible for consumers).
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

// Concrete drop-malformed list serializers (one no-arg type per element, for @Serializable(with=)).
internal object AdDataListSerializer : DropMalformedListSerializer<AdData>(AdData.serializer())
internal object ContentListSerializer : DropMalformedListSerializer<Content>(Content.serializer())
internal object CreativeListSerializer : DropMalformedListSerializer<Creative>(Creative.serializer())
internal object TrackingDetailListSerializer :
    DropMalformedListSerializer<TrackingDetail>(TrackingDetail.serializer())
internal object VerificationResourceListSerializer :
    DropMalformedListSerializer<VerificationScriptResource>(VerificationScriptResource.serializer())

// Required sub-objects that degrade to an empty default instead of dropping the whole creative.
internal object AdvertiserOrDefaultSerializer :
    DefaultOnErrorSerializer<Advertiser>(Advertiser.serializer(), Advertiser())
internal object TrackingInfoOrDefaultSerializer :
    DefaultOnErrorSerializer<TrackingInfo>(TrackingInfo.serializer(), TrackingInfo())
