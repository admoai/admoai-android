package com.admoai.sdk.model.request.audience

/**
 * State of the one subscription or pass relevant to this request, sent as the custom target
 * `subscription_status`. When there is no single relevant subscription, leave it out or use a segment.
 *
 * Send a value only when you know the fact; when you do not, call the setter with null so the
 * key is left out. The SDK never infers or computes a value.
 *
 * @property wireValue The exact, case-sensitive string sent on the wire.
 */
enum class SubscriptionStatus(val wireValue: String) {
    /** Known to have no current or previous relevant subscription. Not "unknown". */
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
