package com.admoai.sdk.model.request

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Rules for the audience signals a publisher sends with a decision request. Behaviour is specified
 * in `AudienceTargeting.feature` and proved by `AudienceTargetingTest`.
 */
internal object AudienceTargeting {

    /** A publisher segment code: letters, digits and underscore only, never empty. */
    val PUBLISHER_SEGMENT_CODE = Regex("^[a-zA-Z0-9_]+$")

    /**
     * Validates every code, then returns them as a JSON array of distinct codes (first-seen order,
     * case-sensitive), or null when there is nothing to send.
     *
     * Validation completes before anything is returned, so a bad code can never leave partial state.
     *
     * @throws IllegalArgumentException naming the offending index, never echoing the value.
     */
    fun publisherSegmentsValue(codes: Collection<String>?): JsonArray? {
        codes?.forEachIndexed { index, code ->
            require(PUBLISHER_SEGMENT_CODE.matches(code)) {
                "publisher segment at index $index must match ^[a-zA-Z0-9_]+\$ " +
                    "(letters, digits and underscore only; no spaces, no empty codes)"
            }
        }
        val cleaned = codes.orEmpty().distinct()
        return if (cleaned.isEmpty()) null else JsonArray(cleaned.map { JsonPrimitive(it) })
    }
}
