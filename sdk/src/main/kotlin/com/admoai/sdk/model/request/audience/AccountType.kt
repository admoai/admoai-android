package com.admoai.sdk.model.request.audience

/**
 * Type of the user's account, sent as the custom target `account_type`.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class AccountType(val wireValue: String) {
    /** Personal account. */
    PERSONAL("personal"),

    /** Business account. */
    BUSINESS("business"),

    /** Family account. */
    FAMILY("family"),

    /** Student account. */
    STUDENT("student"),

    /** Any other account type. */
    OTHER("other")
}
