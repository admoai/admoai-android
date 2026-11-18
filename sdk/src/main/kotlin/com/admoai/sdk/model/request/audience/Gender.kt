package com.admoai.sdk.model.request.audience

/**
 * Gender the user stated in their profile, sent as the custom target `gender`. Never inferred.
 *
 * Send a value only when you know the fact; when you do not, call the setter with null so the
 * key is left out. The SDK never infers or computes a value.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class Gender(val wireValue: String) {
    /** Female. */
    FEMALE("female"),

    /** Male. */
    MALE("male"),

    /** A gender the user stated other than female or male. */
    OTHER("other"),

    /** The user chose "prefer not to say". A recorded choice, never a default for a missing value. */
    PREFER_NOT_TO_SAY("prefer_not_to_say")
}
