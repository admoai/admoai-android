package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.ContentListSerializer
import com.admoai.sdk.serialization.SlideListSerializer
import com.admoai.sdk.serialization.SlideTrackingOrNullSerializer
import com.admoai.sdk.serialization.StrictIntSerializer
import com.admoai.sdk.serialization.StrictStringSerializer
import com.admoai.sdk.serialization.TrackingDetailListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One slide of a Carousel creative. See [slides].
 *
 * `slideId` and `position` are structurally required: a slide without them cannot be told apart or
 * ordered by a publisher, so a slide missing either (or carrying the wrong type) is dropped on its
 * own. Everything inside a slide is tolerant: [contents] drops malformed entries, a malformed
 * [tracking] becomes `null`.
 *
 * @property slideId The slide's public id (`slide_<ULID>`), stable across requests.
 * @property position The slide's 1-based position as the server numbered it. Informational: the
 *   order of [slides] is the order to render.
 * @property contents This slide's own content entries.
 * @property tracking This slide's own beacons. Absent leaves the slide perfectly renderable.
 */
@Serializable
data class Slide(
    @Serializable(with = StrictStringSerializer::class)
    val slideId: String,
    @Serializable(with = StrictIntSerializer::class)
    val position: Int,
    @Serializable(with = ContentListSerializer::class)
    val contents: List<Content> = emptyList(),
    @Serializable(with = SlideTrackingOrNullSerializer::class)
    val tracking: SlideTracking? = null
)

/** One slide's beacon lists: the slide view and the slide click. */
@Serializable
data class SlideTracking(
    @Serializable(with = TrackingDetailListSerializer::class)
    val views: List<TrackingDetail>? = null,
    @Serializable(with = TrackingDetailListSerializer::class)
    val clicks: List<TrackingDetail>? = null
)

/** The `contents` key the engine puts a Carousel's slides under. */
internal const val SLIDES_CONTENT_KEY = "slides"

// The SDK's own response parser configuration, so a slide reads exactly as the rest of the creative.
private val slideJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * A Carousel's slides, in the order the server sent them (never re-sorted by [Slide.position]).
 * Empty for every creative that is not a Carousel.
 *
 * Resolved from the `slides` entry in [Creative.contents], where the wire format puts it (the entry
 * stays there too, exactly as before), the same way [matchedPoints] is. Tolerant per slide: a
 * malformed slide is dropped and its siblings survive, and nothing here can fail a creative that
 * already decoded.
 *
 * Each slide carries its own contents and its own trackers, and they stay attached to the slide: a
 * slide view or click is reported with that slide's [SlideTracking], not with the creative's. The
 * creative-level [Creative.tracking] of a Carousel has no click.
 *
 * **Rendering the slides is your job.** The SDK draws no carousel and knows nothing about paging,
 * scroll position or which slide is on screen.
 */
val Creative.slides: List<Slide>
    get() {
        val entry = contents.firstOrNull { it.key == SLIDES_CONTENT_KEY } ?: return emptyList()
        return runCatching {
            slideJson.decodeFromJsonElement(SlideListSerializer, entry.value)
        }.getOrDefault(emptyList())
    }
