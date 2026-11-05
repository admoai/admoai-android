package com.admoai.sdk.model.response

/**
 * One slide of a Carousel creative. See [slides].
 *
 * @property slideId The slide's public id (`slide_<ULID>`), stable across requests.
 * @property position The slide's 1-based position as the server numbered it. Informational: the
 *   order of [slides] is the order to render.
 * @property contents This slide's own content entries.
 * @property tracking This slide's own beacons. Absent leaves the slide perfectly renderable.
 */
data class Slide(
    val slideId: String,
    val position: Int,
    val contents: List<Content> = emptyList(),
    val tracking: SlideTracking? = null
)

/** One slide's beacon lists: the slide view and the slide click. */
data class SlideTracking(val views: List<TrackingDetail>? = null, val clicks: List<TrackingDetail>? = null)

/**
 * A Carousel's slides, in the order the server sent them (never re-sorted by [Slide.position]).
 * Empty for every creative that is not a Carousel.
 *
 * Each slide carries its own contents and its own trackers, and they stay attached to the slide: a
 * slide view or click is reported with that slide's [SlideTracking], not with the creative's. The
 * creative-level [Creative.tracking] of a Carousel has no click.
 *
 * **Rendering the slides is your job.** The SDK draws no carousel and knows nothing about paging,
 * scroll position or which slide is on screen.
 */
val Creative.slides: List<Slide>
    get() = emptyList()
