package com.admoai.sdk.serialization

import com.admoai.sdk.model.response.Creative
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

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
 * Drop-malformed list of [Creative]. A JSON `null` (ordinary no-fill) or non-array decodes to an
 * empty list instead of throwing; non-object entries are dropped.
 */
internal object CreativeListSerializer : DropMalformedListSerializer<Creative>(Creative.serializer())
