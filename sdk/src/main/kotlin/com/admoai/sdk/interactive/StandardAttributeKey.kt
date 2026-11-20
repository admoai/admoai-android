package com.admoai.sdk.interactive

/**
 * The standard attributes a Lead Generation form can ask for. The declaration order is the catalog
 * order of the shared contract's `standard-fields.json`; keys are always listed in this order.
 *
 * @property wireName The key as it appears on the wire.
 */
enum class StandardAttributeKey(val wireName: String) {
    FIRST_NAME("firstName"),
    LAST_NAME("lastName"),
    FULL_NAME("fullName"),
    EMAIL("email"),
    PHONE("phone"),
    AGE("age"),
    GENDER("gender");

    internal companion object {
        /** The key named [name] (exact, case-sensitive), or null when this SDK does not know it. */
        fun fromWireName(name: String): StandardAttributeKey? = entries.firstOrNull { it.wireName == name }
    }
}
