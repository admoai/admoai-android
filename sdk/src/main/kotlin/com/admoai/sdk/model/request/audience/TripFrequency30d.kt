package com.admoai.sdk.model.request.audience

/**
 * Number of trips the user took in the last 30 days ("0" is sent as a JSON string, never a number), sent as the custom target `trip_frequency_30d`.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class TripFrequency30d(val wireValue: String) {
    /** No trips. */
    ZERO("0"),

    /** 1 to 2 trips. */
    TRIPS_1_2("1_2"),

    /** 3 to 9 trips. */
    TRIPS_3_9("3_9"),

    /** 10 or more trips. */
    TRIPS_10_PLUS("10_plus")
}
