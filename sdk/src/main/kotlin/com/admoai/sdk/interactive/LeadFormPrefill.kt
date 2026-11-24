package com.admoai.sdk.interactive

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns what the app answered into the `values` of the prefill message: only the keys the form
 * asked for, trimmed, never blank, within the contract's length and, for gender, its options. The
 * form validates everything else. Pure; it never logs a value.
 */
internal object LeadFormPrefill {
    fun values(
        raw: Map<StandardAttributeKey, String>?,
        requested: Set<StandardAttributeKey>,
        contract: BridgeContract
    ): JsonObject {
        if (raw == null) return JsonObject(emptyMap())
        val out = LinkedHashMap<String, JsonElement>()
        for (key in StandardAttributeKey.entries) {
            if (key !in requested) continue
            val value = raw[key]?.trim() ?: continue
            if (value.isEmpty()) continue
            if (value.codePointCount(0, value.length) > contract.prefillValueMaxLength) continue
            if (key == StandardAttributeKey.GENDER && value !in contract.genderOptions) continue
            out[key.wireName] = JsonPrimitive(value)
        }
        return JsonObject(out)
    }
}
