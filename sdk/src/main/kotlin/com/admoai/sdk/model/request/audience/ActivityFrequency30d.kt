package com.admoai.sdk.model.request.audience

/**
 * How many times the user did your app's main activity (a ride, a parking session, a booking) in the last 30 days ("0" is sent as a JSON string, never a number), sent as the custom target `activity_frequency_30d`.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class ActivityFrequency30d(val wireValue: String) {
    /** None. */
    ZERO("0"),

    /** 1 to 2 times. */
    TIMES_1_2("1_2"),

    /** 3 to 9 times. */
    TIMES_3_9("3_9"),

    /** 10 or more times. */
    TIMES_10_PLUS("10_plus")
}
