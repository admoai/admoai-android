package com.admoai.sdk

/**
 * Remembers which Sponsored Pin view URLs the SDK has already fired, so a map re-rendering the
 * same decision response does not fire them again.
 *
 * Keyed on the view URL itself, never on the point id. The engine mints every view URL with its
 * own event id, one per `(request, creative, location)`, so "this URL was fired" is exactly
 * "this pin was reported for this decision response" — the key E10 dedups `pin_view` on
 * server-side. A pin id repeats across responses; keying on it would swallow the view a new
 * response is owed.
 *
 * Bounded: it holds the last [capacity] URLs and evicts the least recently claimed. A URL that
 * was evicted and is claimed again simply fires again, which the engine already drops (`SETNX`
 * on the event id), so forgetting is never wrong — only a wasted request.
 *
 * [claim] is synchronized: publishers call the point helpers from any thread, and the
 * check-and-record must be one step or two racing renders would both fire the same pin.
 */
internal class PointViewLedger(capacity: Int = DEFAULT_CAPACITY) {

    val capacity: Int = capacity.coerceAtLeast(1)

    /** Access-ordered, so a pin claimed again moves to the tail and is not the next evicted. */
    private val urls = object : LinkedHashMap<String, Unit>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > this@PointViewLedger.capacity
    }

    /**
     * Records [url] as fired and returns `true` the first time it is seen. Returns `false` for a
     * URL already fired, and refreshes it so a pin still on screen is not the one evicted next.
     */
    @Synchronized
    fun claim(url: String): Boolean = urls.put(url, Unit) == null

    /** How many URLs are remembered right now — the observable side of the bound. */
    val size: Int
        @Synchronized get() = urls.size

    @Synchronized
    fun clear() = urls.clear()

    companion object {
        /**
         * Ten full decision responses at the engine's 50-point ceiling, one view URL per point on
         * the standard template.
         */
        const val DEFAULT_CAPACITY = 500
    }
}
