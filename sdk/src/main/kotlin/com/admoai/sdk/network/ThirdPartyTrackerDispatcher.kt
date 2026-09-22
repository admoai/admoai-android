package com.admoai.sdk.network

import com.admoai.sdk.Admoai
import com.admoai.sdk.model.response.ThirdPartyTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * The event a `fireImpression`/`fireClick` invocation reports, used to select which third-party
 * trackers fan out. Internal: publishers never address trackers directly.
 */
internal sealed interface ThirdPartyTrackerEvent {
    data object Impression : ThirdPartyTrackerEvent
    data class Click(val key: String) : ThirdPartyTrackerEvent
}

/**
 * Credential-isolated dispatcher for third-party event trackers (E06 of the Third-party Event
 * Trackers mission).
 *
 * Agencies count these GETs on their own ad servers, so the dispatch contract is strict:
 * - **Own [HttpClient]**, fully separate from [AdMoaiApiServiceImpl]'s: no Admoai `User-Agent`,
 *   no `X-Decision-Version`/`X-Tracking-Version`, no `Accept-Language`, no auth, no base-url
 *   resolution, and no cookie storage (the `HttpCookies` plugin is never installed).
 * - **GET only**, requesting the stored URL byte-for-byte. A URL Ktor cannot parse or cannot
 *   round-trip byte-identically (e.g. a raw `%%MACRO%%`) is discarded at validation — firing
 *   normalized/re-encoded bytes would corrupt what the agency counts.
 * - **3xx terminal**: `followRedirects = false` — a redirect target runs logic outside our
 *   contract, so it is never requested.
 * - **Cache bypass**: no `HttpCache` plugin — a cached hit would be an unmeasured impression on
 *   the agency side.
 * - **Failure isolation**: every dispatch is an independent fire-and-forget coroutine; a slow or
 *   failing tracker never delays canonical tracking, never affects sibling trackers, and never
 *   surfaces an error to the publisher. No retries — exactly one attempt per matching tracker
 *   per helper invocation, so agency counts reconcile.
 * - **Sanitized logging**: outcomes reference `trackerId` only; tracker URLs never reach any log
 *   sink (they can carry campaign-identifying query data). The Ktor `Logging` plugin is
 *   deliberately never installed here, even when `SDKConfig.enableLogging` is on — its request
 *   dump would print the URLs.
 */
internal class ThirdPartyTrackerDispatcher(
    engine: HttpClientEngine?,
    private val scopeProvider: () -> CoroutineScope,
    private val log: (String, Admoai.LogLevel) -> Unit
) : Closeable {

    /**
     * The engine this dispatcher created and therefore owns. Production always passes
     * `engine = null` so the dispatcher gets its OWN engine — reusing
     * `SDKConfig.networkClientEngine` would silently void every isolation guarantee here:
     * a publisher-preconfigured OkHttp engine keeps its cookie jar, HTTP cache and logging
     * interceptors, which would persist tracker cookies, serve cached hits and print
     * tracker URLs to Logcat. The `engine` parameter is a test seam only.
     *
     * Ktor never closes an engine passed by instance (`manageEngine = false`), so the
     * owned engine must be closed explicitly in [close] or every SDK re-configure leaks
     * a CIO selector/thread pool.
     */
    private val ownedEngine: HttpClientEngine? = if (engine == null) CIO.create() else null

    private val httpClient: HttpClient = HttpClient(engine ?: ownedEngine!!) {
        followRedirects = false // 3xx terminal
        expectSuccess = false // a non-2xx response is a completed attempt, never an exception
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        // Deliberately NO ContentNegotiation, NO Logging, NO defaultRequest, NO cookies, NO cache,
        // NO default headers — see the class KDoc.
    }

    /**
     * Dispatches every tracker matching [event], exactly once each per invocation.
     *
     * Order of operations mirrors the E06 spec: semantic validation drops invalid entries
     * individually; the defensive limit then applies to the count of VALID entries; matching and
     * per-invocation exact-URL dedupe decide what actually fires.
     */
    fun dispatch(trackers: List<ThirdPartyTracker>, event: ThirdPartyTrackerEvent) {
        val valid = trackers.filter { tracker ->
            val reason = rejectionReason(tracker) ?: return@filter true
            log(
                "Discarding third-party tracker '${tracker.trackerId}': $reason",
                Admoai.LogLevel.DEBUG
            )
            false
        }
        if (valid.isEmpty()) return
        if (valid.size > MAX_TRACKERS) {
            log(
                "Discarding ALL third-party trackers for this creative: ${valid.size} valid entries exceed the limit of $MAX_TRACKERS.",
                Admoai.LogLevel.WARNING
            )
            return
        }

        val dispatchedUrls = mutableSetOf<String>()
        for (tracker in valid) {
            if (!matches(tracker, event)) continue
            if (!dispatchedUrls.add(tracker.url)) {
                log(
                    "Skipping third-party tracker '${tracker.trackerId}': duplicate URL already dispatched in this invocation.",
                    Admoai.LogLevel.DEBUG
                )
                continue
            }
            // Resolved per dispatch (not captured at construction) so the @VisibleForTesting
            // sdkScope seam keeps working for this component like it does everywhere else.
            scopeProvider().launch {
                try {
                    httpClient.get(tracker.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Failure isolation: a failed tracker is a completed attempt. Nothing is
                    // retried, nothing propagates, and the URL is never logged.
                }
            }
        }
    }

    override fun close() {
        httpClient.close()
        ownedEngine?.close()
    }

    companion object {
        /**
         * Mirror of the engine-side limit. More than this many valid entries can only mean a
         * serving bug or a tampered response; firing a partial subset would make the agency's
         * numbers quietly disagree with ours, so the whole collection is discarded instead.
         */
        const val MAX_TRACKERS: Int = 10

        const val REQUEST_TIMEOUT_MS: Long = 10_000L

        /**
         * Why an entry cannot be served, or `null` when it is valid. Reasons are stable,
         * URL-free strings — they go straight into logs.
         */
        fun rejectionReason(tracker: ThirdPartyTracker): String? {
            if (tracker.trackerId.isEmpty()) return "empty trackerId"
            when (tracker.eventType) {
                "impression" -> Unit
                "click" -> when (tracker.matchType) {
                    "any" -> Unit
                    "specific" ->
                        if (tracker.eventKey.isNullOrEmpty()) {
                            return "specific click tracker without an eventKey"
                        }
                    else -> return "unknown matchType"
                }
                else -> return "unknown eventType"
            }
            // Validate with the SAME parser that builds the wire request (Ktor's), so
            // what passes here is exactly what fires. A URL Ktor cannot parse (e.g. a raw
            // %%MACRO%% — invalid percent-sequence) or cannot round-trip byte-identically
            // (it normalizes trailing separators, default ports, …) is unservable:
            // firing normalized bytes would corrupt what the agency counts.
            val parsed = try {
                URLBuilder(tracker.url).build()
            } catch (_: Exception) {
                null
            } ?: return "url cannot be parsed as a URL"
            if (parsed.protocol.name != "https" || parsed.host.isEmpty()) {
                return "url is not an absolute https URL"
            }
            // Defense in depth (the Ad Manager already rejects these at creation): a
            // tracker URL must carry no embedded credentials — they would travel in
            // cleartext through proxies and land in the agency server's access logs —
            // and no fragment, which is client-side-only and never part of a fixed
            // measurement URL.
            if (!parsed.user.isNullOrEmpty() || !parsed.password.isNullOrEmpty()) {
                return "url embeds credentials (userinfo)"
            }
            if (parsed.fragment.isNotEmpty()) {
                return "url carries a fragment"
            }
            if (parsed.toString() != tracker.url) {
                return "url does not round-trip verbatim through the URL parser"
            }
            return null
        }

        /**
         * E06 matching: impressions fire on `fireImpression`; any-click trackers fire on every
         * valid click; specific-click trackers fire only when the reported key equals theirs.
         */
        fun matches(tracker: ThirdPartyTracker, event: ThirdPartyTrackerEvent): Boolean =
            when (event) {
                is ThirdPartyTrackerEvent.Impression -> tracker.eventType == "impression"
                is ThirdPartyTrackerEvent.Click ->
                    tracker.eventType == "click" &&
                        (tracker.matchType == "any" ||
                            (tracker.matchType == "specific" && tracker.eventKey == event.key))
            }
    }
}
