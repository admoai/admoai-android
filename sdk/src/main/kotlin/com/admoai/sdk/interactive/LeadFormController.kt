package com.admoai.sdk.interactive

import android.view.View
import com.admoai.sdk.model.response.InteractiveExperience
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
@ExperimentalInteractiveExperienceApi
class LeadFormController internal constructor(
    private val descriptor: InteractiveExperience.LeadGeneration,
    /** How the app shows the form; told to the form so it draws its own close button only full screen. */
    val presentation: FormPresentation,
    private val host: LeadFormHost,
    private val transportFactory: () -> BridgeTransport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val channel: String = newChannel(),
    private val log: (String) -> Unit = {}
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
    fun prepare() {
        if (disposed || mutableState.value != LeadFormState.Idle) return
        if (expired()) {
            mutableState.value = LeadFormState.Expired
            return
        }
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
                prepare()
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

            // `event`: the page reports it; firing its tracking URL is added in a later release.
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
        mutableState.value = LeadFormState.Prepared
        if (pendingOpen) {
            pendingOpen = false
            activate()
        }
    }

    private fun activate() {
        if (send("activate")) {
            activateSent = true
            mutableState.value = LeadFormState.Activating
        }
    }

    private fun onActivated(payload: JsonObject) {
        if (mutableState.value != LeadFormState.Activating) return
        activatedReceived = true
        // formVersionId, requestedAttributeKeys and events are kept by a later release, which fills
        // the prefill and fires the events; this one answers with an empty prefill.
        send("prefill", buildJsonObject { put("values", JsonObject(emptyMap())) })
    }

    private fun onPrefillApplied() {
        if (mutableState.value != LeadFormState.Activating) return
        mutableState.value = LeadFormState.ReadyToShow
        if (visibleRequested) becomeVisible()
    }

    private fun becomeVisible() {
        if (send("became_visible")) {
            visibleOnWire = true
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
