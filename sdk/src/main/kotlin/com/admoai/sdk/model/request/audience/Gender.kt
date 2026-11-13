package com.admoai.sdk.model.request.audience

/**
 * Gender of the user, sent as the custom target `gender`.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class Gender(val wireValue: String) {
    /** Female. */
    FEMALE("female"),

    /** Male. */
    MALE("male"),

    /** Other. */
    OTHER("other"),

    /** Prefers not to say. */
    PREFER_NOT_TO_SAY("prefer_not_to_say")
}
