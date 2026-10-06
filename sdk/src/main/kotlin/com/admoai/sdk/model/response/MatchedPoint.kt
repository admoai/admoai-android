package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.MatchedPointListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One Matched Point's three beacon lists.
 *
 * `views` and `taps` are analytics only and cost nothing. `clicks` is a standard billable click
 * at the campaign's CPC, attributed to that location — which is why [com.admoai.sdk.Admoai.trackPointClick]
 * replaces the creative-level click rather than joining it.
 */
@Serializable
data class MatchedPointTracking(
    val views: List<TrackingDetail>? = null,
    val taps: List<TrackingDetail>? = null,
    val clicks: List<TrackingDetail>? = null
)

/**
 * One Advertiser Location a Sponsored Pin creative is promoting.
 *
 * Read [clickUrl] verbatim. The engine has already applied the precedence — the location's own
 * URL, unless the campaign overrides every pin with the creative's default — so falling back to
 * the creative's URL would silently defeat a campaign that deliberately points each shop at its
 * own page.
 *
 * @property id The Advertiser Location's public id. The server's internal integer never leaves it.
 * @property address Absent when the location has no address — never an empty string.
 * @property distance Metres from the point the request searched around.
 * @property clickUrl Where a click on this pin goes, already resolved. Absent when nothing
 *   supplies one: a pin with no destination is still a pin on the map.
 * @property tracking This point's own beacons. Absent leaves the point perfectly renderable — it
 *   simply has nothing to report.
 */
@Serializable
data class MatchedPoint(
    val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val distance: Int,
    val clickUrl: String? = null,
    val tracking: MatchedPointTracking? = null
)

/** The `contents` key the engine puts Matched Points under. */
internal const val MATCHED_POINTS_CONTENT_KEY = "matched_points"

private val matchedPointJson = Json { ignoreUnknownKeys = true }

/**
 * The Advertiser Locations this creative is promoting, nearest first (Sponsored Pin Locations,
 * epic #3138). Empty for every creative that is not a Sponsored Pin creative.
 *
 * Resolved from the `matched_points` entry in [Creative.contents], where the wire format puts it —
 * mirroring it onto the creative as well would give two places to read the same thing and one of
 * them to forget. Tolerant per entry: a malformed point is dropped and its siblings survive, and
 * nothing here can fail a creative that already decoded.
 *
 * **Rendering these is your job.** The SDK draws no map and knows nothing about clustering,
 * sheets or scroll position, which is exactly why it never fires a point event on your behalf.
 * See [com.admoai.sdk.Admoai.trackPointView].
 */
val Creative.matchedPoints: List<MatchedPoint>
    get() {
        val entry = contents.firstOrNull { it.key == MATCHED_POINTS_CONTENT_KEY } ?: return emptyList()
        return runCatching {
            matchedPointJson.decodeFromJsonElement(MatchedPointListSerializer, entry.value)
        }.getOrDefault(emptyList())
    }
