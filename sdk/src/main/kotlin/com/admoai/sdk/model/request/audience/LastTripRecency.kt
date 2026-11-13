package com.admoai.sdk.model.request.audience

/**
 * How long ago the user's last trip was, sent as the custom target `last_trip_recency`.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class LastTripRecency(val wireValue: String) {
    /** Never took a trip. */
    NEVER("never"),

    /** 0 to 7 days ago. */
    DAYS_0_7("0_7d"),

    /** 8 to 30 days ago. */
    DAYS_8_30("8_30d"),

    /** 31 to 90 days ago. */
    DAYS_31_90("31_90d"),

    /** More than 90 days ago. */
    DAYS_91_PLUS("91d_plus")
}
