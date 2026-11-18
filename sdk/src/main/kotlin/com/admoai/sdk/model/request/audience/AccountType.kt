package com.admoai.sdk.model.request.audience

/**
 * Type of the account or profile the request is made for (the profile selected now, not every
 * membership the user has), sent as the custom target `account_type`.
 *
 * Send a value only when you know the fact; when you do not, call the setter with null so the
 * key is left out. The SDK never infers or computes a value.
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

    /** A known account type outside the four above. Not "unknown": leave the key out then. */
    OTHER("other")
}
