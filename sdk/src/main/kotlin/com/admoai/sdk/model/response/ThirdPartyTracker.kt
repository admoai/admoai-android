package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * One fixed third-party tracker entry, exactly as the engine serialized it
 * (`tracking.thirdPartyTrackers[]`, served additively under `X-Decision-Version: 2025-11-01`).
 *
 * Fields stay raw [String]s so decoding is tolerant of values this SDK version does not know;
 * semantic validation (event/match shape, HTTPS) happens at fire time in
 * `ThirdPartyTrackerDispatcher`, where invalid entries are dropped individually with a sanitized
 * log. The [url] is stored and dispatched verbatim — never normalized, re-encoded, or logged.
 *
 * @property trackerId Public tracker id (`tpt_<ULID>`) — the only identifier ever allowed in logs.
 * @property eventType `"impression"` or `"click"`.
 * @property matchType `"any"` or `"specific"`; present on click trackers only.
 * @property eventKey Template click-event key; present on `specific` click trackers only.
 * @property url Fixed HTTPS tracking URL, byte-identical to what the operator stored.
 */
@Serializable
data class ThirdPartyTracker(
    val trackerId: String,
    val eventType: String,
    val matchType: String? = null,
    val eventKey: String? = null,
    val url: String
)
