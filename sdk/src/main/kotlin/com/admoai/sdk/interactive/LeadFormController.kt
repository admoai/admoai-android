package com.admoai.sdk.interactive

import android.os.Handler
import android.os.Looper
import android.view.View
import com.admoai.sdk.model.response.InteractiveExperience
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * What the controller needs from the page it drives: a view, a way to load the launch URL, to send
 * one bridge text and to hear one. The SDK's real implementation is [SecureFormWebView].
 */
internal interface BridgeTransport {
    val view: View?

    fun load(url: String)

    fun send(text: String)

    fun setOnMessage(listener: (String) -> Unit)

    fun setOnRendererGone(listener: () -> Unit)

    /** Called once when the view refused a navigation the form tried (a POST or a second page). */
    fun setOnNavigationBlocked(listener: () -> Unit)

    /**
     * Called once when the form's entry page could not be loaded: with the HTTP status the server
     * answered (400 or more), or `null` when the page could not be reached at all.
     */
    fun setOnLoadFailed(listener: (httpStatus: Int?) -> Unit)

    fun destroy()
}

/** Runs work on the main thread: now-or-soon ([post]) and later ([postDelayed], cancellable). */
internal interface LeadFormScheduler {
    fun post(block: () -> Unit)

    /** Runs [block] after [delayMs]; the returned function cancels it. */
    fun postDelayed(delayMs: Long, block: () -> Unit): () -> Unit
}

/** The real scheduler: the main looper. [post] runs at once when already on the main thread. */
internal object MainLooperScheduler : LeadFormScheduler {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    override fun postDelayed(delayMs: Long, block: () -> Unit): () -> Unit {
        val task = Runnable(block)
        handler.postDelayed(task, delayMs)
        return { handler.removeCallbacks(task) }
    }
}

/**
 * Prepares and opens one Lead Generation form. Create one per served creative with
 * [com.admoai.sdk.Admoai.createLeadFormController].
 *
 * The app still renders the creative, fires its impression and its single click. The controller
 * never fires a click, never fetches another ad and never shows a stale form.
 *
 * Typical use: [prepare] while the creative is on screen, [open] when the viewer taps, add [view] to
 * the screen, call [setVisible] with `true` once it is actually shown (and with `false` when it is
 * hidden again), and [dispose] when the form goes away, whatever the reason.
 *
 * Not thread-safe: call every method from the main thread. The form's messages are delivered on the
 * main thread too.
 */
class LeadFormController internal constructor(
    private val descriptor: InteractiveExperience.LeadGeneration,
    /** How the app shows the form; told to the form so it draws its own close button only full screen. */
    val presentation: FormPresentation,
    private val host: LeadFormHost,
    private val transportFactory: () -> BridgeTransport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val channel: String = newChannel(),
    private val log: (String) -> Unit = {},
    private val valueProvider: LeadFormValueProvider,
    private val fireTracking: (String) -> Unit = {},
    private val scheduler: LeadFormScheduler,
    private val registry: LeadFormRegistry? = null,
    private val generation: Long = 0L,
    private val contract: BridgeContract = BridgeContract
) {
    private val session = BridgeSession(descriptor.formInstanceId, channel)
    private val mutableState = MutableStateFlow<LeadFormState>(LeadFormState.Idle)
    private var transport: BridgeTransport? = null

    private var pendingOpen = false
    private var visibleRequested = false
    private var visibleOnWire = false
    private var bridgeReadyCount = 0
    private var activateSent = false
    private var activatedReceived = false
    private var submittedReceived = false
    private var pageMessageReceived = false
    private var disposed = false
    private var rendererGone = false
    private var pageLoadFailed = false
    private var deferred = false
    private var loadStarted = false
    private var everVisible = false

    private var requested: Set<StandardAttributeKey> = emptySet()
    private var events: Map<String, String> = emptyMap()
    private val fired = HashSet<String>()
    private var lookupId = 0
    private var prefillDone = true
    private var cancelLookupTimer: (() -> Unit)? = null
    private var lookupDeadline = 0L

    /** True once a user change or opt-out invalidated this controller: it must release no value. */
    private fun stale(): Boolean = registry?.isCurrent(generation) == false

    internal val isDisposed: Boolean
        get() = disposed

    /**
     * True while this form holds a loaded hidden view that has never been shown: it counts toward
     * the hidden bound. A form that ended (failed for good or expired) has lost its view and frees its place.
     */
    internal val countsAsHiddenPrepared: Boolean
        get() = !disposed && loadStarted && !everVisible && !ended

    /** Where the form is now. */
    val state: StateFlow<LeadFormState> = mutableState.asStateFlow()

    /** The form instance this controller drives, as the decision response named it. */
    val formInstanceId: String = descriptor.formInstanceId

    /**
     * The view that shows the form. The same view for the controller's whole life. It loads nothing
     * by itself: [prepare] or [open] does. Throws [IllegalStateException] after [dispose].
     */
    fun view(): View {
        check(!disposed) { "The controller was disposed" }
        return ensureTransport().view ?: throw IllegalStateException("The form has no view")
    }

    /** Loads the form hidden and prepares it, so a later [open] is quick. Safe to repeat. */
    fun prepare() = prepare(force = false)

    private fun prepare(force: Boolean) {
        if (disposed || mutableState.value != LeadFormState.Idle) return
        if (expired()) {
            mutableState.value = LeadFormState.Expired
            return
        }
        if (!force && registry?.mayPrepare(this) == false) {
            // Too many hidden forms are loaded: stay idle; the viewer's tap loads this one anyway.
            deferred = true
            return
        }
        deferred = false
        loadStarted = true
        val t = ensureTransport()
        mutableState.value = LeadFormState.Preparing
        t.load(launchUrlWithIdentity())
    }

    /** Opens the form: activates it as soon as it is prepared. Safe to repeat. */
    fun open() {
        if (disposed) return
        when (mutableState.value) {
            LeadFormState.Idle, LeadFormState.Preparing, LeadFormState.Prepared -> if (expired()) {
                mutableState.value = LeadFormState.Expired
                return
            }

            else -> Unit
        }
        when (mutableState.value) {
            LeadFormState.Idle -> {
                pendingOpen = true
                prepare(force = true)
            }

            LeadFormState.Preparing -> pendingOpen = true

            LeadFormState.Prepared -> activate()

            else -> Unit
        }
    }

    /** Tells the form whether the app is showing it right now. Repeating the same value does nothing. */
    fun setVisible(visible: Boolean) {
        if (disposed || visible == visibleRequested) return
        visibleRequested = visible
        when (mutableState.value) {
            LeadFormState.ReadyToShow -> if (visible) becomeVisible()
            LeadFormState.Visible, LeadFormState.Submitting, is LeadFormState.Submitted -> syncVisibility()
            else -> Unit
        }
    }

    /**
     * Call it from the app's back handling. Returns `true` when the SDK consumed the press: the form
     * is on screen, so the form goes back one step itself (or asks the app to close through
     * [LeadFormHost.onRequestClose] on its first step and on its confirmation). Returns `false` when
     * the form is not on screen: the press belongs to the app. While a submission is in progress the
     * press is consumed and nothing happens. The SDK never closes the form on back by itself.
     */
    fun handleBack(): Boolean {
        if (disposed || !visibleOnWire) return false
        return when (mutableState.value) {
            LeadFormState.Visible, is LeadFormState.Submitted -> {
                // The press is consumed even when the transport is down: the form is still on screen.
                send("back")
                true
            }

            LeadFormState.Submitting -> true

            else -> false
        }
    }

    /** Retries after a failure that reported `retryable`. Does nothing in any other state. */
    fun retry() {
        if (disposed) return
        val failed = mutableState.value as? LeadFormState.Failed ?: return
        if (!failed.retryable) return
        if (pageLoadFailed) {
            // The page never arrived, so there is no bridge to retry over: load it again, same view.
            pageLoadFailed = false
            if (expired()) {
                mutableState.value = LeadFormState.Expired
                return
            }
            val t = transport ?: return
            mutableState.value = LeadFormState.Preparing
            t.load(launchUrlWithIdentity())
            return
        }
        if (activateSent) {
            activate()
        } else if (send("retry_prepare")) {
            mutableState.value = LeadFormState.Preparing
        }
    }

    /**
     * Ends the form: tells it to drop what it holds, destroys the view and ignores everything after.
     * Safe to repeat. Call it whenever the form goes away.
     */
    fun dispose() {
        if (disposed) return
        disposed = true
        lookupId++
        cancelLookupTimer?.invoke()
        cancelLookupTimer = null
        requested = emptySet()
        events = emptyMap()
        val t = transport
        if (t != null) {
            if (pageMessageReceived && !rendererGone) {
                try {
                    t.send(session.encode("dispose", EMPTY))
                } catch (_: Throwable) {
                    // Best effort: the page may already be gone.
                }
            }
            try {
                t.setOnMessage { }
                t.setOnRendererGone { }
                t.setOnNavigationBlocked { }
                t.setOnLoadFailed { }
                t.destroy()
            } catch (_: Throwable) {
                // Nothing more to release.
            }
        }
        transport = null
        mutableState.value = LeadFormState.Disposed
    }

    override fun toString(): String =
        "LeadFormController(formInstanceId=$formInstanceId, presentation=$presentation, state=${mutableState.value})"

    private fun expired(): Boolean = clock() >= descriptor.expiresAtEpochMillis

    private fun ensureTransport(): BridgeTransport {
        transport?.let { return it }
        val t = transportFactory()
        t.setOnMessage { text -> onMessage(text) }
        t.setOnRendererGone { onRendererGone() }
        t.setOnNavigationBlocked { onNavigationBlocked() }
        t.setOnLoadFailed { status -> onLoadFailed(status) }
        transport = t
        return t
    }

    private fun launchUrlWithIdentity(): String {
        val base = descriptor.launchUrl.substringBefore('#')
        return "$base#instanceId=${encode(formInstanceId)}&channel=${encode(channel)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private val ended: Boolean
        get() = when (val s = mutableState.value) {
            LeadFormState.Expired, LeadFormState.Disposed -> true
            is LeadFormState.Failed -> !s.retryable
            else -> false
        }

    private fun onRendererGone() {
        if (disposed) return
        rendererGone = true
        mutableState.value = LeadFormState.Failed("renderer_gone", false)
        try {
            transport?.destroy()
        } catch (_: Throwable) {
            // Already gone.
        }
    }

    private fun onNavigationBlocked() {
        if (disposed) return
        mutableState.value = LeadFormState.Failed("navigation_blocked", false)
        try {
            transport?.destroy()
        } catch (_: Throwable) {
            // Already gone.
        }
    }

    /**
     * The form's entry page never arrived. Only the entry page counts: once the bridge is up, an
     * error on anything the page loads afterwards is not the form failing to load.
     */
    private fun onLoadFailed(httpStatus: Int?) {
        if (disposed || ended || bridgeReadyCount > 0) return
        if (httpStatus == null || httpStatus >= 500 || httpStatus == 429 || httpStatus == 408) {
            // A server error, rate limiting (429), a timeout (408) or a network blip: retry() loads
            // the page again in the same view.
            pageLoadFailed = true
            mutableState.value = LeadFormState.Failed("temporary_failure", true)
            return
        }
        mutableState.value = LeadFormState.Failed("form_not_available", false)
        try {
            transport?.destroy()
        } catch (_: Throwable) {
            // Already gone.
        }
    }

    private fun onMessage(text: String) {
        if (disposed) return
        when (val received = session.receive(text)) {
            is Received.Refused -> log("Lead form message refused: ${received.reason}")

            is Received.Accepted -> {
                pageMessageReceived = true
                handle(received.type, received.payload)
            }
        }
    }

    private fun handle(type: String, payload: JsonObject) {
        if (type == "request_close") {
            host.onRequestClose(this)
            return
        }
        if (ended) return
        when (type) {
            "bridge_ready" -> onBridgeReady()

            "prepared" -> onPrepared()

            "activated" -> onActivated(payload)

            "prefill_applied" -> onPrefillApplied()

            "prepare_failed" -> onPrepareFailed(payload)

            "fatal_error" -> fail(payload.string("errorCode"))

            "submitting" -> if (isVisibleState()) mutableState.value = LeadFormState.Submitting

            "submit_failed" -> if (mutableState.value == LeadFormState.Submitting) {
                mutableState.value = LeadFormState.Visible
            }

            "submitted" -> onSubmitted(payload)

            "request_external_url" -> onExternalUrl(payload)

            "event" -> onEvent(payload)

            else -> Unit
        }
    }

    private fun isVisibleState(): Boolean =
        mutableState.value == LeadFormState.Visible || mutableState.value == LeadFormState.Submitting

    private fun onBridgeReady() {
        bridgeReadyCount++
        if (activatedReceived) {
            mutableState.value = LeadFormState.Failed("reloaded", false)
            return
        }
        if (bridgeReadyCount > 1) {
            // The page reloaded before activation and lost its state: start the preparation over.
            if (mutableState.value == LeadFormState.Activating) pendingOpen = true
            activateSent = false
            mutableState.value = LeadFormState.Preparing
            // A collector may dispose (or otherwise move) the controller inline on `Preparing`.
            if (mutableState.value != LeadFormState.Preparing) return
        }
        val initialized = send(
            "initialize",
            buildJsonObject {
                put("preparationToken", descriptor.preparationToken)
                put("presentation", presentation.wire)
            }
        )
        if (!initialized) mutableState.value = LeadFormState.Failed("bridge_error", false)
    }

    private fun onPrepared() {
        if (mutableState.value != LeadFormState.Preparing) return
        // Take the queued tap before publishing: a collector may open() inline on `Prepared`, and that
        // call already activates. Only activate here when the state is still `Prepared` afterwards.
        val tapQueued = pendingOpen
        pendingOpen = false
        mutableState.value = LeadFormState.Prepared
        if (tapQueued && mutableState.value == LeadFormState.Prepared) activate()
    }

    private fun activate() {
        if (expired()) {
            // A tap queued before the deadline, or a retry after it: the preparation is no longer valid.
            mutableState.value = LeadFormState.Expired
            return
        }
        if (send("activate")) {
            activateSent = true
            mutableState.value = LeadFormState.Activating
        }
    }

    private fun onActivated(payload: JsonObject) {
        if (mutableState.value != LeadFormState.Activating) return
        activatedReceived = true
        requested = payload.getValue("requestedAttributeKeys").jsonArray
            .mapNotNull { StandardAttributeKey.fromWireName((it as JsonPrimitive).content) }
            .toSet()
        events = payload.getValue("events").jsonArray.associate {
            val item = it.jsonObject
            item.string("name") to item.string("url")
        }
        startPrefill()
    }

    /** Asks the app for the requested values, at most once per activation, within the contract's time. */
    private fun startPrefill() {
        val id = ++lookupId
        cancelLookupTimer?.invoke()
        cancelLookupTimer = null
        prefillDone = false
        if (stale()) {
            dispose()
            return
        }
        if (requested.isEmpty()) {
            finishPrefill(id, null)
            return
        }
        lookupDeadline = clock() + contract.prefillLookupTimeoutMs
        cancelLookupTimer = scheduler.postDelayed(contract.prefillLookupTimeoutMs) { finishPrefill(id, null) }
        try {
            valueProvider.lookUpValues(requested) { values ->
                // Copy on the caller's thread, inside a guard: the app's map may change later or throw
                // when read (a Java caller may even pass null); nothing it hands over is read on the main thread.
                val copy: Map<StandardAttributeKey, String>? = try {
                    values.toMap()
                } catch (_: Throwable) {
                    null
                }
                scheduler.post { finishPrefill(id, copy) }
            }
        } catch (e: Throwable) {
            log("Lead Generation value lookup failed: ${e.javaClass.simpleName}")
            finishPrefill(id, null)
        }
    }

    private fun finishPrefill(id: Int, values: Map<StandardAttributeKey, String>?) {
        if (disposed || id != lookupId || prefillDone) return
        if (stale()) {
            // The user changed or the app opted out: release nothing, the form is going away.
            dispose()
            return
        }
        prefillDone = true
        cancelLookupTimer?.invoke()
        cancelLookupTimer = null
        if (mutableState.value != LeadFormState.Activating) return
        // An answer past the deadline counts as no answer, even when the timeout task has not run yet.
        val answer = if (clock() > lookupDeadline) null else values
        val prefilled =
            send("prefill", buildJsonObject { put("values", LeadFormPrefill.values(answer, requested, contract)) })
        // Without the prefill the page has nothing to answer: a failed send ends the form, as a failed initialize does.
        if (!prefilled) mutableState.value = LeadFormState.Failed("bridge_error", false)
    }

    private fun onEvent(payload: JsonObject) {
        val name = payload.string("name")
        val url = events[name] ?: return
        if (!fired.add(name)) return
        fireTracking(url)
    }

    private fun onPrefillApplied() {
        if (mutableState.value != LeadFormState.Activating) return
        mutableState.value = LeadFormState.ReadyToShow
        // A collector may already have shown the form inline on `ReadyToShow`: one signal, one notification.
        if (visibleRequested && mutableState.value == LeadFormState.ReadyToShow) becomeVisible()
    }

    private fun becomeVisible() {
        if (send("became_visible")) {
            visibleOnWire = true
            everVisible = true
            mutableState.value = LeadFormState.Visible
        }
    }

    private fun syncVisibility() {
        if (visibleRequested == visibleOnWire) return
        if (send(if (visibleRequested) "became_visible" else "became_hidden")) visibleOnWire = visibleRequested
    }

    private fun onPrepareFailed(payload: JsonObject) {
        val code = payload.string("errorCode")
        if (code == "expired") {
            mutableState.value = LeadFormState.Expired
            return
        }
        // The contract's error catalog has the last word: a code it marks final is never retryable.
        val pageSaysRetryable = payload["retryable"]?.jsonPrimitive?.content == "true"
        mutableState.value = LeadFormState.Failed(code, BridgeProtocol.retryable(code, pageSaysRetryable))
    }

    private fun fail(code: String) {
        mutableState.value = if (code == "expired") LeadFormState.Expired else LeadFormState.Failed(code, false)
    }

    private fun onSubmitted(payload: JsonObject) {
        if (!isVisibleState()) return
        submittedReceived = true
        mutableState.value = LeadFormState.Submitted(payload.string("leadId"), payload.string("submittedAt"))
    }

    private fun onExternalUrl(payload: JsonObject) {
        val purpose = ExternalLinkPurpose.entries.firstOrNull { it.wire == payload.string("purpose") } ?: return
        if (purpose == ExternalLinkPurpose.WEBSITE_CTA && !submittedReceived) return
        host.onOpenExternalLink(this, purpose, payload.string("url"))
    }

    private fun JsonObject.string(key: String): String = (getValue(key) as JsonPrimitive).content

    /** Encodes and sends one app message; a message the contract refuses fails the controller. */
    private fun send(type: String, payload: JsonObject = EMPTY): Boolean {
        val t = transport ?: return false
        val text = try {
            session.encode(type, payload)
        } catch (e: IllegalArgumentException) {
            log("Lead form message not sent: ${e.message}")
            return false
        }
        return try {
            t.send(text)
            true
        } catch (e: Exception) {
            log("Lead form transport failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private companion object {
        val EMPTY = JsonObject(emptyMap())
    }
}
