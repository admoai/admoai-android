package com.admoai.sdk.interactive

import com.admoai.sdk.serialization.parseRfc3339ToEpochMillis
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The bridge rules of the shared contract pack, read as data from the two JSON resources under
 * `com/admoai/sdk/interactive/contract/`: no limit, pattern or message shape is written in code.
 * Pure Kotlin, so it runs in JVM tests.
 */
internal object BridgeProtocol {
    private const val RESOURCE_DIR = "/com/admoai/sdk/interactive/contract/"

    internal class MessageSpec(
        val type: String,
        val direction: String,
        val after: List<String>,
        val fields: Map<String, JsonObject>
    )

    private fun load(name: String): JsonObject {
        val stream = BridgeProtocol::class.java.getResourceAsStream(RESOURCE_DIR + name)
            ?: throw IllegalStateException("missing contract resource $name")
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return Json.parseToJsonElement(text).jsonObject
    }

    internal val bridge: JsonObject by lazy { load("bridge-messages.json") }
    internal val standardFields: JsonObject by lazy { load("standard-fields.json") }

    val maxMessageBytes: Int by lazy { bridge.getValue("maxMessageBytes").jsonPrimitive.int() }

    private val envelopeSpec: JsonObject by lazy { bridge.getValue("envelope").jsonObject }

    private val instancePattern: Regex by lazy { Regex(envelopeSpec.pattern("instanceId")) }

    private val channelPattern: Regex by lazy { Regex(envelopeSpec.pattern("channel")) }

    private val messages: Map<String, MessageSpec> by lazy {
        bridge.getValue("messages").jsonArray.associate { el ->
            val m = el.jsonObject
            val type = m.getValue("type").jsonPrimitive.content
            type to MessageSpec(
                type = type,
                direction = m.getValue("direction").jsonPrimitive.content,
                after = m.getValue("after").jsonArray.map { it.jsonPrimitive.content },
                fields = m.getValue("payload").jsonObject.mapValues { it.value.jsonObject }
            )
        }
    }

    private val prefillKeys: Set<String> by lazy {
        standardFields.getValue("prefillKeys").jsonArray.map { it.jsonPrimitive.content }.toSet()
    }

    private val genderValues: Set<String> by lazy {
        standardFields.getValue("genderOptions").jsonArray.map {
            it.jsonObject.getValue("value").jsonPrimitive.content
        }.toSet()
    }

    /** The contract's value lists a field spec may name (`itemsFrom`, `keysFrom`). */
    private fun list(name: String): Set<String>? = when (name) {
        "prefillKeys" -> prefillKeys
        else -> null
    }

    fun spec(type: String): MessageSpec? = messages[type]

    private fun JsonObject.pattern(key: String): String =
        getValue(key).jsonObject.getValue("pattern").jsonPrimitive.content

    private fun JsonPrimitive.int(): Int = intOrNull ?: throw IllegalStateException("not an int")

    /**
     * The first payload field of [spec] that [payload] breaks (a contract field name, never a value
     * the sender chose), or null when the payload is valid. An unknown field is reported as
     * [UNKNOWN_FIELD], not by its name.
     */
    internal fun firstPayloadFault(spec: MessageSpec, payload: JsonObject): String? {
        for (key in payload.keys) {
            if (key !in spec.fields) return UNKNOWN_FIELD
        }
        for ((name, fieldSpec) in spec.fields) {
            val value = payload[name]
            if (value == null) {
                if (fieldSpec["required"]?.jsonPrimitive?.booleanOrNull == true) return name
                continue
            }
            if (!valid(fieldSpec, value, name)) return name
        }
        return null
    }

    private fun valid(spec: JsonObject, value: JsonElement, field: String): Boolean =
        when (spec.getValue("type").jsonPrimitive.content) {
            "string" -> value.stringOrNull()?.let { validString(spec, it) } ?: false
            "boolean" -> (value as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull != null } ?: false
            "string_list" -> validStringList(spec, value)
            "string_map" -> validStringMap(spec, value, field)
            "https_url" -> value.stringOrNull()?.let { isHttpsUrl(it) } ?: false
            "timestamp" -> value.stringOrNull()?.let { parseRfc3339ToEpochMillis(it) != null } ?: false
            "object_list" -> validObjectList(spec, value)
            else -> false
        }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun codePoints(s: String): Int = s.codePointCount(0, s.length)

    private fun validString(spec: JsonObject, s: String): Boolean {
        spec["maxLength"]?.jsonPrimitive?.intOrNull?.let { if (codePoints(s) > it) return false }
        spec["pattern"]?.jsonPrimitive?.contentOrNull?.let { if (!Regex(it).containsMatchIn(s)) return false }
        spec["enum"]?.jsonArray?.let { allowed -> if (allowed.none { it.jsonPrimitive.content == s }) return false }
        return true
    }

    private fun validStringList(spec: JsonObject, value: JsonElement): Boolean {
        val array = value as? JsonArray ?: return false
        val allowed = spec["itemsFrom"]?.jsonPrimitive?.contentOrNull?.let { list(it) ?: return false }
        return array.all { item ->
            val s = item.stringOrNull() ?: return@all false
            allowed == null || s in allowed
        }
    }

    private fun validStringMap(spec: JsonObject, value: JsonElement, field: String): Boolean {
        val map = value as? JsonObject ?: return false
        val allowed = spec["keysFrom"]?.jsonPrimitive?.contentOrNull?.let { list(it) ?: return false }
        val maxLength = spec["maxLength"]?.jsonPrimitive?.intOrNull
        return map.all { (key, v) ->
            val s = v.stringOrNull() ?: return@all false
            if (allowed != null && key !in allowed) return@all false
            if (maxLength != null && codePoints(s) > maxLength) return@all false
            // The one value with a closed set: gender is one of the catalog's options.
            !(field == "values" && key == "gender" && s !in genderValues)
        }
    }

    private fun validObjectList(spec: JsonObject, value: JsonElement): Boolean {
        val array = value as? JsonArray ?: return false
        val items = spec["items"]?.jsonObject ?: return false
        val seen = HashSet<String>()
        val uniqueBy = spec["uniqueBy"]?.jsonPrimitive?.contentOrNull
        for (el in array) {
            val obj = el as? JsonObject ?: return false
            if (obj.keys.any { it !in items }) return false
            for ((name, itemSpec) in items) {
                val v = obj[name]
                if (v == null) {
                    if (itemSpec.jsonObject["required"]?.jsonPrimitive?.booleanOrNull == true) return false
                    continue
                }
                if (!valid(itemSpec.jsonObject, v, name)) return false
            }
            if (uniqueBy != null) {
                val key = obj[uniqueBy]?.stringOrNull() ?: return false
                if (!seen.add(key)) return false
            }
        }
        return true
    }

    private fun isHttpsUrl(s: String): Boolean = try {
        val uri = java.net.URI(s)
        uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    internal fun validInstanceId(s: String): Boolean = instancePattern.matches(s)

    internal fun validChannel(s: String): Boolean = channelPattern.matches(s)

    internal const val UNKNOWN_FIELD = "unknown_field"

    private const val CHANNEL_BYTES = 24

    private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** A fresh channel: 24 random bytes, base64url without padding (32 characters). */
    internal fun newChannel(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(CHANNEL_BYTES).also { random.nextBytes(it) }
        val out = StringBuilder(CHANNEL_BYTES / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val n = (bytes[i].toInt() and 0xFF shl 16) or
                (bytes[i + 1].toInt() and 0xFF shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            out.append(BASE64_URL[n shr 18 and 0x3F])
            out.append(BASE64_URL[n shr 12 and 0x3F])
            out.append(BASE64_URL[n shr 6 and 0x3F])
            out.append(BASE64_URL[n and 0x3F])
            i += 3
        }
        return out.toString()
    }
}

/**
 * The numbers of the shared contract the SDK acts on, read from the vendored JSON and never written
 * in code. An interface so a test can supply other numbers; the companion is the real contract.
 */
internal interface BridgeContract {
    /** How many forms may be loaded and hidden at once. */
    val maxHiddenPreparedForms: Int

    /** How long the app has to answer a value lookup, in milliseconds. */
    val prefillLookupTimeoutMs: Long

    /** The longest prefill value, in Unicode code points. */
    val prefillValueMaxLength: Int

    /** The `value` of each gender option of the catalog. */
    val genderOptions: Set<String>

    companion object : BridgeContract {
        override val maxHiddenPreparedForms: Int by lazy {
            BridgeProtocol.bridge.getValue("maxHiddenPreparedForms").jsonPrimitive.int()
        }

        override val prefillLookupTimeoutMs: Long by lazy {
            BridgeProtocol.bridge.getValue("prefillLookupTimeoutMs").jsonPrimitive.content.toLong()
        }

        override val prefillValueMaxLength: Int by lazy {
            val spec = checkNotNull(BridgeProtocol.spec("prefill")) { "missing prefill message" }
            spec.fields.getValue("values").getValue("maxLength").jsonPrimitive.int()
        }

        override val genderOptions: Set<String> by lazy {
            BridgeProtocol.standardFields.getValue("genderOptions").jsonArray.map {
                it.jsonObject.getValue("value").jsonPrimitive.content
            }.toSet()
        }

        private fun JsonPrimitive.int(): Int = intOrNull ?: throw IllegalStateException("not an int")
    }
}

/** A fresh bridge channel for one controller. */
internal fun newChannel(): String = BridgeProtocol.newChannel()

/** The outcome of [BridgeSession.receive]. */
internal sealed class Received {
    class Accepted(val type: String, val payload: JsonObject) : Received()

    /** [reason] is a fixed string; it never carries any part of the refused message. */
    class Refused(val reason: String) : Received()
}

/**
 * One bridge instance: the identity every message must carry and the set of message types
 * exchanged so far (sent and received), which the contract's `after` lists are checked against.
 * Not thread-safe; the controller drives it from one thread.
 */
internal class BridgeSession(private val instanceId: String, private val channel: String) {
    private val exchanged = HashSet<String>()

    /**
     * The contract's rule no `after` list can express: each `submitted` or `submit_failed` answers
     * one unanswered `submitting`, so a retry starts with a new `submitting`.
     */
    private var submissionUnanswered = false

    fun hasExchanged(type: String): Boolean = type in exchanged

    fun receive(text: String): Received {
        if (text.length > BridgeProtocol.maxMessageBytes ||
            text.toByteArray(Charsets.UTF_8).size > BridgeProtocol.maxMessageBytes
        ) {
            return Received.Refused("too_large")
        }
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            return Received.Refused("not_json")
        }
        val envelope = root as? JsonObject ?: return Received.Refused("not_json")
        if (envelope.keys != ENVELOPE_KEYS) return Received.Refused("bad_envelope")
        val type = envelope["type"].asString() ?: return Received.Refused("bad_envelope")
        val id = envelope["instanceId"].asString()
        val chan = envelope["channel"].asString()
        val payload = envelope["payload"] as? JsonObject
        if (id == null || chan == null || payload == null ||
            !BridgeProtocol.validInstanceId(id) || !BridgeProtocol.validChannel(chan)
        ) {
            return Received.Refused("bad_envelope")
        }
        if (id != instanceId) return Received.Refused("wrong_instance")
        if (chan != channel) return Received.Refused("wrong_channel")
        val spec = BridgeProtocol.spec(type)
        if (spec == null || spec.direction != RUNTIME_TO_APP) return Received.Refused("unknown_type")
        BridgeProtocol.firstPayloadFault(spec, payload)?.let { return Received.Refused("bad_payload:$it") }
        if (spec.after.isNotEmpty() && spec.after.none { it in exchanged }) return Received.Refused("out_of_order")
        when (type) {
            SUBMITTING -> submissionUnanswered = true

            SUBMITTED, SUBMIT_FAILED -> {
                if (!submissionUnanswered) return Received.Refused("out_of_order")
                submissionUnanswered = false
            }
        }
        exchanged += type
        return Received.Accepted(type, payload)
    }

    /**
     * The envelope for an app-to-runtime message, as JSON text. Values are JSON-escaped data. Throws
     * [IllegalArgumentException] naming the reason (never a value) when the type or payload breaks
     * the contract, or when the message would go out before a message its `after` list names.
     */
    fun encode(type: String, payload: JsonObject): String {
        val spec = BridgeProtocol.spec(type)
        require(spec != null && spec.direction == APP_TO_RUNTIME) { "unknown_type" }
        BridgeProtocol.firstPayloadFault(spec, payload)?.let { throw IllegalArgumentException("bad_payload:$it") }
        require(spec.after.isEmpty() || spec.after.any { it in exchanged }) { "out_of_order" }
        val text = buildJsonObject {
            put("type", type)
            put("instanceId", instanceId)
            put("channel", channel)
            put("payload", payload)
        }.toString()
        exchanged += type
        return text
    }

    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val RUNTIME_TO_APP = "runtime_to_app"
        const val APP_TO_RUNTIME = "app_to_runtime"
        const val SUBMITTING = "submitting"
        const val SUBMITTED = "submitted"
        const val SUBMIT_FAILED = "submit_failed"
        val ENVELOPE_KEYS = setOf("type", "instanceId", "channel", "payload")
    }
}
