package com.admoai.sdk.model.request.audience

/**
 * Age of the user in completed years at the time of the request, sent as the custom target `age_group`.
 * `UNDER_18` is below 18, each numbered bucket includes both end ages, and `AGE_65_PLUS` starts at 65.
 *
 * Send a value only when you know the fact; when you do not, call the setter with null so the
 * key is left out. The SDK never infers or computes a value.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class AgeGroup(val wireValue: String) {
    /** Under 18. */
    UNDER_18("under_18"),

    /** 18 to 24. */
    AGE_18_24("18_24"),

    /** 25 to 34. */
    AGE_25_34("25_34"),

    /** 35 to 44. */
    AGE_35_44("35_44"),

    /** 45 to 54. */
    AGE_45_54("45_54"),

    /** 55 to 64. */
    AGE_55_64("55_64"),

    /** 65 and over. */
    AGE_65_PLUS("65_plus")
}
