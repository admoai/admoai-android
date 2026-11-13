package com.admoai.sdk.model.request.audience

/**
 * Subscription state of the user, sent as the custom target `subscription_status`.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class SubscriptionStatus(val wireValue: String) {
    /** No subscription. */
    NONE("none"),

    /** In a trial. */
    TRIAL("trial"),

    /** Active subscription. */
    ACTIVE("active"),

    /** Paused subscription. */
    PAUSED("paused"),

    /** Expired subscription. */
    EXPIRED("expired")
}
