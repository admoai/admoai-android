package com.admoai.sdk.model.request.audience

/**
 * Days since the user's latest completed activity (see [ActivityFrequency30d] for what counts), sent
 * as the custom target `last_activity_recency`. Days are completed 24-hour periods:
 * `floor(seconds / 86400)`, day 0 at completion.
 *
 * Send a value only when you know the fact; when you do not, call the setter with null so the
 * key is left out. The SDK never infers or computes a value.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class LastActivityRecency(val wireValue: String) {
    /** Known to have never completed an activity. Not "unknown". */
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
