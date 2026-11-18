package com.admoai.sdk.model.request.audience

/**
 * Age of the user's account (days since it was created), not of the app installation, sent as the
 * custom target `account_tenure`. Days are completed 24-hour periods: `floor(seconds / 86400)`, day 0
 * at creation.
 *
 * Send a value only when you know the fact; when you do not, call the setter with null so the
 * key is left out. The SDK never infers or computes a value.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class AccountTenure(val wireValue: String) {
    /** 0 to 30 days. */
    DAYS_0_30("0_30d"),

    /** 31 to 90 days. */
    DAYS_31_90("31_90d"),

    /** 91 to 365 days. */
    DAYS_91_365("91_365d"),

    /** More than 365 days. */
    DAYS_366_PLUS("366d_plus")
}
