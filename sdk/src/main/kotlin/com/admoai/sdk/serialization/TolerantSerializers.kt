package com.admoai.sdk.serialization

import com.admoai.sdk.interactive.StandardAttributeKey
import com.admoai.sdk.model.common.Error
import com.admoai.sdk.model.common.Warning
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Advertiser
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.CreativeJourney
import com.admoai.sdk.model.response.CreativeMetadata
import com.admoai.sdk.model.response.InteractiveExperience
import com.admoai.sdk.model.response.MatchedPoint
import com.admoai.sdk.model.response.Slide
import com.admoai.sdk.model.response.SlideTracking
import com.admoai.sdk.model.response.TemplateInfo
import com.admoai.sdk.model.response.ThirdPartyTracker
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.VastData
import com.admoai.sdk.model.response.VerificationScriptResource
import com.admoai.sdk.model.response.contentTypeFromWire
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Decodes a JSON array element-by-element, dropping any element that fails to decode; null/non-array
 * → empty list. The Tolerant Reader "drop malformed list entries" primitive — one bad element never
 * aborts the whole response.
 */
internal open class DropMalformedListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
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
internal open class DefaultOnErrorSerializer<T>(private val delegate: KSerializer<T>, private val default: T) :
    KSerializer<T> {
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
 * A string, or `null` for anything that is not a JSON string (absent, `null`, number, boolean,
 * object, array). The lenient parser the SDK reads responses with would otherwise accept `3` or
 * `false` as the text "3" / "false", which a publisher would then read as a real value.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object StringOrNullSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrNull", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeString() }.getOrNull()
        return (jsonDecoder.decodeJsonElement() as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}

/**
 * The whole number a JSON number carries (`1` and `1.0` are both 1), or `null` for anything else: a
 * string, a boolean, a fractional or out-of-range number, `null`, an object.
 */
private fun JsonElement.wholeIntOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    primitive.content.toIntOrNull()?.let { return it }
    val number = primitive.content.toDoubleOrNull() ?: return null
    return number.takeIf { it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
}

/** An integer that tolerates a whole JSON number (`1.0`); a wrong type is `null`, never a throw. */
@OptIn(ExperimentalSerializationApi::class)
internal object WholeIntOrNullSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WholeIntOrNull", PrimitiveKind.INT).nullable

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeInt() }.getOrNull()
        return jsonDecoder.decodeJsonElement().wholeIntOrNull()
    }
}

/**
 * A required string that stays strict under the lenient parser: a number or boolean throws instead
 * of being read as text, so the slide that carries it is dropped by [DropMalformedListSerializer].
 */
internal object StrictStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StrictString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive
        return primitive?.takeIf { it.isString }?.content ?: throw SerializationException("expected a JSON string")
    }
}

/** A required whole number: a string (`"2"`), boolean or fractional value throws, dropping the slide. */
internal object StrictIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StrictInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        return jsonDecoder.decodeJsonElement().wholeIntOrNull()
            ?: throw SerializationException("expected a JSON integer")
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
    private data class ContentSurrogate(val key: String, val value: JsonElement = JsonNull, val type: String)
}

/**
 * Decodes an optional object, returning null on any failure — a malformed optional field degrades
 * to null instead of dropping the whole (renderable) creative. Nullable counterpart of [DefaultOnErrorSerializer].
 */
@OptIn(ExperimentalSerializationApi::class)
internal open class NullOnErrorSerializer<T : Any>(private val delegate: KSerializer<T>) : KSerializer<T?> {
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
internal object ThirdPartyTrackerListSerializer :
    DropMalformedListSerializer<ThirdPartyTracker>(ThirdPartyTracker.serializer())
internal object MatchedPointListSerializer :
    DropMalformedListSerializer<MatchedPoint>(MatchedPoint.serializer())
internal object SlideListSerializer : DropMalformedListSerializer<Slide>(Slide.serializer())
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
internal object SlideTrackingOrNullSerializer :
    NullOnErrorSerializer<SlideTracking>(SlideTracking.serializer())
internal object VastDataOrNullSerializer : NullOnErrorSerializer<VastData>(VastData.serializer())
internal object CreativeJourneyOrNullSerializer :
    NullOnErrorSerializer<CreativeJourney>(CreativeJourney.serializer())

private val RFC3339 =
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d{1,9})?(Z|[+-]\\d{2}:\\d{2})$")

/**
 * Parses an RFC 3339 timestamp (`Z` or a numeric offset, optional fraction) to epoch milliseconds,
 * or null when it is not one. Strict: an impossible date or time is rejected, never rolled over.
 */
internal fun parseRfc3339ToEpochMillis(s: String): Long? {
    val m = RFC3339.matchEntire(s) ?: return null
    val g = m.groupValues
    val year = g[1].toInt()
    val month = g[2].toInt()
    val day = g[3].toInt()
    val hour = g[4].toInt()
    val minute = g[5].toInt()
    val second = g[6].toInt()
    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    val millis = g[7].removePrefix(".").padEnd(3, '0').take(3).ifEmpty { "0" }.toInt()
    val offsetMillis = when (val z = g[8]) {
        "Z" -> 0L

        else -> {
            val oh = z.substring(1, 3).toInt()
            val om = z.substring(4, 6).toInt()
            if (oh > 23 || om > 59) return null
            (if (z[0] == '-') -1L else 1L) * (oh * 60L + om) * 60_000L
        }
    }
    return try {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.isLenient = false
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(java.util.Calendar.MILLISECOND, millis)
        cal.timeInMillis - offsetMillis
    } catch (_: Exception) {
        null
    }
}

/**
 * Decodes a creative's `interactiveExperience`: `null` when absent or JSON null, a Lead Generation
 * experience when the descriptor is complete and safe, and `Unsupported` for everything else. It
 * never throws, so one bad descriptor never costs the whole response.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object InteractiveExperienceSerializer : KSerializer<InteractiveExperience?> {
    private const val LEAD_GENERATION = "lead_generation"

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InteractiveExperience", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: InteractiveExperience?) {
        val jsonEncoder = encoder as? JsonEncoder
        if (value == null) {
            encoder.encodeNull()
            return
        }
        jsonEncoder ?: throw SerializationException("InteractiveExperience encodes to JSON only")
        val obj = when (value) {
            is InteractiveExperience.LeadGeneration -> buildJsonObject {
                put("type", LEAD_GENERATION)
                put("formId", value.formId)
                put("preparedVersionId", value.preparedVersionId)
                put("formInstanceId", value.formInstanceId)
                put("launchUrl", value.launchUrl)
                put("preparationToken", value.preparationToken)
                put("expiresAt", formatUtc(value.expiresAtEpochMillis))
                put("prefillAttributeKeys", JsonArray(value.prefillAttributeKeys.map { JsonPrimitive(it.wireName) }))
            }

            is InteractiveExperience.Unsupported -> buildJsonObject {
                value.type?.let { put("type", it) }
            }
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): InteractiveExperience? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return try {
            decode(jsonDecoder.decodeJsonElement())
        } catch (_: Exception) {
            InteractiveExperience.Unsupported(null)
        }
    }

    private fun decode(element: JsonElement): InteractiveExperience? {
        if (element is JsonNull) return null
        val obj = element as? JsonObject ?: return InteractiveExperience.Unsupported(null)
        val type = obj.string("type")
        if (type != LEAD_GENERATION) return InteractiveExperience.Unsupported(type)
        val unsupported = InteractiveExperience.Unsupported(LEAD_GENERATION)
        val formId = obj.nonBlank("formId") ?: return unsupported
        val preparedVersionId = obj.nonBlank("preparedVersionId") ?: return unsupported
        val formInstanceId = obj.nonBlank("formInstanceId") ?: return unsupported
        val token = obj.nonBlank("preparationToken") ?: return unsupported
        val launchUrl = obj.string("launchUrl")?.takeIf { isHttpsUrl(it) } ?: return unsupported
        val expires = obj.string("expiresAt")?.let { parseRfc3339ToEpochMillis(it) } ?: return unsupported
        val keys = when (val raw = obj["prefillAttributeKeys"]) {
            null, is JsonNull -> emptyList()

            is JsonArray -> raw.mapNotNull { el ->
                (el as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(StandardAttributeKey::fromWireName)
            }.distinct()

            else -> return unsupported
        }
        return InteractiveExperience.LeadGeneration(
            formId = formId,
            formInstanceId = formInstanceId,
            preparedVersionId = preparedVersionId,
            expiresAtEpochMillis = expires,
            prefillAttributeKeys = keys,
            launchUrl = launchUrl,
            preparationToken = token
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.nonBlank(key: String): String? = string(key)?.takeIf { it.isNotBlank() }

    private fun isHttpsUrl(s: String): Boolean = try {
        val uri = java.net.URI(s)
        uri.isAbsolute && uri.scheme == "https" && !uri.host.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    private fun formatUtc(epochMillis: Long): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(epochMillis))
    }
}
