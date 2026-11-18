package com.admoai.sdk.model.request.audience

/**
 * How many activities the user completed in the last 30 days (30 × 24 hours up to the request), sent
 * as the custom target `activity_frequency_30d`. An activity is one completed use of your service: a
 * ride, a parking session, a ticket, a rental. Booked, cancelled or failed ones do not count. `"0"` is
 * sent as a JSON string, never a number.
 *
 * Send a value only when you know the fact; when you do not, call the setter with null so the
 * key is left out. The SDK never infers or computes a value.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class ActivityFrequency30d(val wireValue: String) {
    /** Known to have completed no activity in the window. Not "unknown". */
    ZERO("0"),

    /** 1 to 2 times. */
    TIMES_1_2("1_2"),

    /** 3 to 9 times. */
    TIMES_3_9("3_9"),

    /** 10 or more times. */
    TIMES_10_PLUS("10_plus")
}
