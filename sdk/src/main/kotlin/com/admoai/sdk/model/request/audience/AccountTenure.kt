package com.admoai.sdk.model.request.audience

/**
 * How long the account has existed, sent as the custom target `account_tenure`.
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
