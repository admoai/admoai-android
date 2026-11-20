package com.admoai.sdk.interactive

import android.content.Context
import android.view.View
import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.InteractiveExperience
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scenarios of `src/main/kotlin/com/admoai/sdk/interactive/LeadFormController.feature`, one test
 * per scenario, named after it, against a fake [BridgeTransport] that records what the controller
 * loads and sends and lets the test play the page with the contract's own runtime fixtures.
 */
@OptIn(ExperimentalInteractiveExperienceApi::class)
class LeadFormControllerTest {

    private val root = "src/test/resources/interactive-experience-contract/bridge"
    private val context = Json.parseToJsonElement(File("$root/context.json").readText()).jsonObject
    private val instanceId = context.getValue("instanceId").jsonPrimitive.content
    private val channel = context.getValue("channel").jsonPrimitive.content

    private class FakeHost : LeadFormHost {
        val closes = mutableListOf<LeadFormController>()
        val links = mutableListOf<Pair<ExternalLinkPurpose, String>>()

        override fun onRequestClose(controller: LeadFormController) {
            closes += controller
        }

        override fun onOpenExternalLink(controller: LeadFormController, purpose: ExternalLinkPurpose, url: String) {
            links += purpose to url
        }
    }

    private val token = "tok_secret_value_123"
    private val launchUrl = "https://api.tenant.example.invalid/interactive-experiences/runtime/index.html"
    private var now = 0L
    private var expiresAt = 1_000_000L
    private var factoryCalls = 0
    private val transport = FakeTransport()
    private val scheduler = FakeScheduler()
    private val fired = mutableListOf<String>()
    private val host = FakeHost()

    private fun descriptor(expires: Long = expiresAt, instance: String = instanceId) =
        InteractiveExperience.LeadGeneration(
            formId = "form_01KG000000000000000000F001",
            formInstanceId = instance,
            preparedVersionId = "form_version_01KG000000000000000000V002",
            expiresAtEpochMillis = expires,
            prefillAttributeKeys = listOf(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL),
            launchUrl = launchUrl,
            preparationToken = token
        )

    private val logs = mutableListOf<String>()

    private fun emptyProvider() = object : LeadFormValueProvider {
        override fun availableKeys(): Set<StandardAttributeKey> = emptySet()

        override fun lookUpValues(keys: Set<StandardAttributeKey>, completion: LeadFormValues) =
            completion.complete(emptyMap())
    }

    private fun controller(
        presentation: FormPresentation = FormPresentation.FULL_SCREEN,
        on: FakeTransport = transport
    ) = LeadFormController(
        descriptor = descriptor(),
        presentation = presentation,
        host = host,
        transportFactory = {
            factoryCalls++
            on
        },
        clock = { now },
        channel = channel,
        log = { logs += it },
        valueProvider = emptyProvider(),
        fireTracking = { fired += it },
        scheduler = scheduler
    )

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun sentTypes() = transport.sentTypes()

    private fun sentOf(type: String) = transport.sent.map { parse(it) }
        .filter { it.getValue("type").jsonPrimitive.content == type }

    /** The page speaks: the contract's own runtime fixture, as the page would send it. */
    private fun page(name: String) = transport.page(name)

    private fun pageRaw(text: String) = transport.say(text)

    private fun prepared(c: LeadFormController) {
        c.prepare()
        page("bridge-ready")
        page("prepared")
    }

    private fun readyToShow(c: LeadFormController) {
        prepared(c)
        c.open()
        page("activated")
        page("prefill-applied")
    }

    private fun visible(c: LeadFormController) {
        readyToShow(c)
        c.setVisible(true)
    }

    private fun websiteRequest(): String {
        val base = parse(File("$root/runtime-to-app/request-external-url.json").readText())
        val payload = JsonObject(
            base.getValue("payload").jsonObject +
                mapOf(
                    "purpose" to JsonPrimitive("website_cta"),
                    "url" to JsonPrimitive("https://advertiser.example.invalid/")
                )
        )
        return JsonObject(base + ("payload" to payload)).toString()
    }

    @Before
    fun setUp() {
        Admoai.resetForTesting()
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
    }

    private fun optedInSdk() {
        Admoai.initialize(
            SDKConfig(baseUrl = "https://test.admoai.com", apiVersion = "2025-11-01", networkClientEngine = null)
        )
        Admoai.getInstance().secureFormSurfaceAvailable = { true }
        Admoai.getInstance().enableInteractiveExperiences(emptyProvider())
    }

    private fun creative(experience: InteractiveExperience?) = Creative(interactiveExperience = experience)

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/interactive/LeadFormController.feature")
        val titles = feature.readLines()
            .map { it.trim() }
            .filter { it.startsWith("Scenario:") || it.startsWith("Scenario Outline:") }
            .map { it.substringAfter(":").trim() }
            .toSet()
        val tests = javaClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .map { it.name }
            .toSet()

        assertTrue("the feature file has scenarios", titles.isNotEmpty())
        assertEquals(emptySet<String>(), titles - tests)
    }

    @Test
    fun `an app that has not opted in gets no controller`() {
        Admoai.initialize(
            SDKConfig(baseUrl = "https://test.admoai.com", apiVersion = "2025-11-01", networkClientEngine = null)
        )
        Admoai.getInstance().secureFormSurfaceAvailable = { true }
        val got = Admoai.getInstance().createLeadFormController(
            mockk<Context>(relaxed = true),
            creative(descriptor(System.currentTimeMillis() + 60_000)),
            FormPresentation.INLINE,
            host
        )
        assertNull(got)
    }

    @Test
    fun `a creative without a usable descriptor gets no controller`() {
        optedInSdk()
        val ctx = mockk<Context>(relaxed = true)
        val sdk = Admoai.getInstance()
        assertNull(
            sdk.createLeadFormController(
                ctx,
                creative(InteractiveExperience.Unsupported("lead_generation")),
                FormPresentation.INLINE,
                host
            )
        )
        assertNull(sdk.createLeadFormController(ctx, creative(null), FormPresentation.INLINE, host))
        // An instance the bridge contract does not allow never reaches a controller (nor throws).
        assertNull(
            sdk.createLeadFormController(
                ctx,
                creative(descriptor(instance = "not a valid instance")),
                FormPresentation.INLINE,
                host
            )
        )
        // The same SDK does hand out a controller for a usable descriptor, wired to the form's origin.
        var origin: String? = null
        sdk.leadFormTransportFactory = { _, o ->
            origin = o
            transport
        }
        val c = sdk.createLeadFormController(
            ctx,
            creative(descriptor(System.currentTimeMillis() + 60_000)),
            FormPresentation.INLINE,
            host
        )
        assertNotNull(c)
        c!!.prepare()
        assertEquals("https://api.tenant.example.invalid", origin)
        val loaded = transport.loads.single()
        assertTrue(loaded.startsWith("$launchUrl#instanceId=$instanceId&channel="))
        assertTrue(Regex("channel=[A-Za-z0-9_-]{32}$").containsMatchIn(loaded))
        assertEquals(FormPresentation.INLINE, c.presentation)
        assertEquals(instanceId, c.formInstanceId)

        // The origin matches what the form's frame reports: no default :443, any other port kept.
        fun originFor(url: String): String? {
            origin = null
            sdk.createLeadFormController(
                ctx,
                creative(
                    InteractiveExperience.LeadGeneration(
                        formId = "form_01KG000000000000000000F001",
                        formInstanceId = instanceId,
                        preparedVersionId = "form_version_01KG000000000000000000V002",
                        expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
                        prefillAttributeKeys = emptyList(),
                        launchUrl = url,
                        preparationToken = token
                    )
                ),
                FormPresentation.INLINE,
                host
            )?.let {
                it.prepare()
                it.dispose()
            }
            return origin
        }
        assertEquals("https://forms.example.invalid", originFor("https://forms.example.invalid:443/x"))
        assertEquals("https://forms.example.invalid:8443", originFor("https://forms.example.invalid:8443/x"))
    }

    @Test
    fun `a device without a secure form view gets no controller`() {
        optedInSdk()
        Admoai.getInstance().secureFormSurfaceAvailable = { false }
        assertNull(
            Admoai.getInstance().createLeadFormController(
                mockk<Context>(relaxed = true),
                creative(descriptor(System.currentTimeMillis() + 60_000)),
                FormPresentation.INLINE,
                host
            )
        )
    }

    @Test
    fun `preparing loads the form hidden and stops before activation`() {
        val c = controller()
        c.prepare()
        assertEquals(LeadFormState.Preparing, c.state.value)
        assertEquals("$launchUrl#instanceId=$instanceId&channel=$channel", transport.loads.single())
        page("bridge-ready")
        page("prepared")
        assertEquals(LeadFormState.Prepared, c.state.value)
        val init = sentOf("initialize").single().getValue("payload").jsonObject
        assertEquals(token, init.getValue("preparationToken").jsonPrimitive.content)
        assertEquals("full_screen", init.getValue("presentation").jsonPrimitive.content)
        assertEquals(listOf("initialize"), sentTypes())
    }

    @Test
    fun `an inline app tells the form it is inline`() {
        val c = controller(FormPresentation.INLINE)
        prepared(c)
        assertEquals(
            "inline",
            sentOf("initialize").single().getValue("payload").jsonObject.getValue("presentation").jsonPrimitive.content
        )
    }

    @Test
    fun `opening a prepared form activates it, prefills and makes it ready to show`() {
        val c = controller()
        prepared(c)
        c.open()
        assertEquals(LeadFormState.Activating, c.state.value)
        assertEquals(listOf("initialize", "activate"), sentTypes())
        page("activated")
        assertEquals(listOf("initialize", "activate", "prefill"), sentTypes())
        assertEquals(
            JsonObject(emptyMap()),
            sentOf("prefill").single().getValue("payload").jsonObject.getValue("values")
        )
        assertEquals(LeadFormState.Activating, c.state.value)
        page("prefill-applied")
        assertEquals(LeadFormState.ReadyToShow, c.state.value)
    }

    @Test
    fun `a form becomes visible only when the app says it is on screen`() {
        val c = controller()
        readyToShow(c)
        assertTrue(sentOf("became_visible").isEmpty())
        c.setVisible(true)
        assertEquals(LeadFormState.Visible, c.state.value)
        c.setVisible(true)
        assertEquals(1, sentOf("became_visible").size)
    }

    @Test
    fun `an app that reports visible early gets visibility once the form is ready`() {
        val c = controller()
        prepared(c)
        c.open()
        c.setVisible(true)
        page("activated")
        assertTrue(sentOf("became_visible").isEmpty())
        page("prefill-applied")
        assertEquals(LeadFormState.Visible, c.state.value)
        assertEquals(1, sentOf("became_visible").size)
    }

    @Test
    fun `tapping before preparation finished opens the form once it is ready`() {
        val c = controller()
        c.prepare()
        c.open()
        assertTrue(sentOf("activate").isEmpty())
        page("bridge-ready")
        assertTrue(sentOf("activate").isEmpty())
        page("prepared")
        assertEquals(1, sentOf("activate").size)
        assertEquals(LeadFormState.Activating, c.state.value)
        assertEquals(1, transport.loads.size)
    }

    @Test
    fun `opening before preparing prepares first and activates without a second load`() {
        val c = controller()
        c.open()
        assertEquals(LeadFormState.Preparing, c.state.value)
        page("bridge-ready")
        page("prepared")
        assertEquals(1, sentOf("activate").size)
        assertEquals(1, transport.loads.size)
    }

    @Test
    fun `preparing or opening twice never creates a second form`() {
        val c = controller()
        c.prepare()
        c.prepare()
        c.open()
        c.open()
        page("bridge-ready")
        page("prepared")
        c.open()
        c.prepare()
        assertEquals(1, transport.loads.size)
        assertEquals(1, factoryCalls)
        assertEquals(1, sentOf("activate").size)
    }

    @Test
    fun `hiding and showing again keeps the same form`() {
        val c = controller()
        visible(c)
        val before = c.view()
        c.setVisible(false)
        c.setVisible(true)
        assertSame(before, c.view())
        assertEquals(1, transport.loads.size)
        assertEquals(
            listOf("became_visible", "became_hidden", "became_visible"),
            sentTypes().filter {
                it.startsWith("became")
            }
        )
        assertEquals(LeadFormState.Visible, c.state.value)
    }

    @Test
    fun `a preparation that expired never opens`() {
        val c = controller()
        prepared(c)
        now = expiresAt + 1
        val before = transport.sent.toList()
        c.open()
        assertEquals(LeadFormState.Expired, c.state.value)
        assertEquals(before, transport.sent)

        // Expired before anything loaded: nothing is loaded, nothing is sent.
        val fresh =
            LeadFormController(
                descriptor(),
                FormPresentation.INLINE,
                host,
                { FakeTransport() },
                { now },
                channel,
                valueProvider = emptyProvider(),
                scheduler = scheduler
            )
        fresh.open()
        assertEquals(LeadFormState.Expired, fresh.state.value)
    }

    @Test
    fun `a retryable preparation failure can be retried in the same view`() {
        val c = controller()
        c.prepare()
        page("bridge-ready")
        page("prepare-failed")
        assertEquals(LeadFormState.Failed("temporary_failure", true), c.state.value)
        c.retry()
        assertEquals(LeadFormState.Preparing, c.state.value)
        assertEquals(listOf("initialize", "retry_prepare"), sentTypes())
        assertEquals(1, transport.loads.size)
        page("prepared")
        assertEquals(LeadFormState.Prepared, c.state.value)
    }

    @Test
    fun `a retryable activation failure retries activation`() {
        val c = controller()
        prepared(c)
        c.open()
        page("prepare-failed")
        assertEquals(LeadFormState.Failed("temporary_failure", true), c.state.value)
        c.retry()
        assertEquals(LeadFormState.Activating, c.state.value)
        assertEquals(listOf("initialize", "activate", "activate"), sentTypes())
        assertEquals(1, transport.loads.size)
    }

    @Test
    fun `a failure that cannot be retried ends the controller`() {
        val c = controller()
        c.prepare()
        page("bridge-ready")
        page("fatal-error")
        assertEquals(LeadFormState.Failed("revoked", false), c.state.value)
        val before = transport.sent.size
        c.retry()
        assertEquals(before, transport.sent.size)
        assertEquals(LeadFormState.Failed("revoked", false), c.state.value)

        // The page says retryable, the contract's catalog says unauthorized is final: final wins.
        val narrowed = controller()
        narrowed.prepare()
        page("bridge-ready")
        pageRaw(
            File("$root/runtime-to-app/prepare-failed.json").readText()
                .replace("\"temporary_failure\"", "\"unauthorized\"")
        )
        assertEquals(LeadFormState.Failed("unauthorized", false), narrowed.state.value)
    }

    @Test
    fun `the form reports a submission in progress and its outcome`() {
        val c = controller()
        visible(c)
        page("submitting")
        assertEquals(LeadFormState.Submitting, c.state.value)
        page("submitted")
        val done = c.state.value as LeadFormState.Submitted
        assertEquals("lead_01KG000000000000000000W001", done.leadId)
        assertEquals("2026-11-12T15:04:05Z", done.submittedAt)
    }

    @Test
    fun `a failed submission keeps the same form for a retry`() {
        val c = controller()
        visible(c)
        val before = c.view()
        page("submitting")
        page("submit-failed")
        assertEquals(LeadFormState.Visible, c.state.value)
        assertSame(before, c.view())
        assertEquals(1, transport.loads.size)
        page("submitting")
        assertEquals(LeadFormState.Submitting, c.state.value)
    }

    @Test
    fun `the form asks to close`() {
        val c = controller()
        visible(c)
        page("request-close")
        assertEquals(listOf(c), host.closes)
    }

    @Test
    fun `the privacy policy link goes to the app, never inside the form`() {
        val c = controller()
        visible(c)
        page("request-external-url")
        assertEquals(
            listOf(ExternalLinkPurpose.PRIVACY_POLICY to "https://advertiser.example.invalid/privacy"),
            host.links
        )
        assertEquals(1, transport.loads.size)
    }

    @Test
    fun `the website link is refused before a submission`() {
        val c = controller()
        visible(c)
        pageRaw(websiteRequest())
        assertTrue(host.links.isEmpty())
    }

    @Test
    fun `the website link goes to the app after a submission`() {
        val c = controller()
        visible(c)
        page("submitting")
        page("submitted")
        pageRaw(websiteRequest())
        assertEquals(listOf(ExternalLinkPurpose.WEBSITE_CTA to "https://advertiser.example.invalid/"), host.links)
    }

    @Test
    fun `a page that reloads after activation fails the controller`() {
        val c = controller()
        visible(c)
        page("bridge-ready")
        assertEquals(LeadFormState.Failed("reloaded", false), c.state.value)
        assertEquals(1, sentOf("initialize").size)
    }

    @Test
    fun `a page that reloads before activation is answered with initialize again`() {
        val c = controller()
        prepared(c)
        page("bridge-ready")
        assertEquals(2, sentOf("initialize").size)
        assertEquals(LeadFormState.Preparing, c.state.value)
        page("prepared")
        assertEquals(LeadFormState.Prepared, c.state.value)
    }

    @Test
    fun `disposing ends the form and ignores everything after`() {
        val c = controller()
        visible(c)
        c.dispose()
        assertEquals("dispose", sentTypes().last())
        assertEquals(1, transport.destroyed)
        val sentBefore = transport.sent.size
        // Messages from a callback the page had already queued.
        val stale = transport.firstListener!!
        File("$root/runtime-to-app").listFiles()!!.forEach { stale(it.readText()) }
        stale(websiteRequest())
        c.open()
        c.prepare()
        c.setVisible(false)
        c.retry()
        c.dispose()
        assertEquals(LeadFormState.Disposed, c.state.value)
        assertEquals(sentBefore, transport.sent.size)
        assertEquals(1, transport.destroyed)
        assertEquals(1, transport.loads.size)
        assertTrue(host.closes.isEmpty())
        assertTrue(host.links.isEmpty())
    }

    @Test
    fun `a form that never spoke is disposed without a message`() {
        val c = controller()
        c.prepare()
        c.dispose()
        assertTrue(transport.sent.isEmpty())
        assertEquals(1, transport.destroyed)
    }

    @Test
    fun `a form that tries to leave its page fails`() {
        val c = controller()
        visible(c)
        transport.blocked()
        assertEquals(LeadFormState.Failed("navigation_blocked", false), c.state.value)
        assertEquals(1, transport.destroyed)
        c.retry()
        assertEquals(LeadFormState.Failed("navigation_blocked", false), c.state.value)
        c.dispose()
        assertEquals(LeadFormState.Disposed, c.state.value)
    }

    /** One fresh controller on its own transport per case, so no case inherits another's end state. */
    private fun preparingOn(t: FakeTransport, tap: Boolean = false): LeadFormController {
        val c = controller(on = t)
        if (tap) c.open() else c.prepare()
        assertEquals(LeadFormState.Preparing, c.state.value)
        return c
    }

    @Test
    fun `a form page that is not available fails and cannot be retried`() {
        for (status in listOf(404, 403, 400, 410)) {
            val t = FakeTransport()
            val c = preparingOn(t, tap = status == 410)
            t.loadFailed(status)
            assertEquals("$status", LeadFormState.Failed("form_not_available", false), c.state.value)
            assertEquals("$status", 1, t.destroyed)
            c.retry()
            assertEquals("$status", LeadFormState.Failed("form_not_available", false), c.state.value)
            assertEquals("$status", 1, t.loads.size)
        }
    }

    @Test
    fun `a form page that fails for now can be loaded again in the same view`() {
        // A server error on a tapped form: the retry loads the same page again and the tap still opens it.
        val t = FakeTransport()
        val server = preparingOn(t, tap = true)
        t.loadFailed(500)
        assertEquals(LeadFormState.Failed("temporary_failure", true), server.state.value)
        assertEquals("the view is kept for the retry", 0, t.destroyed)
        server.retry()
        assertEquals(LeadFormState.Preparing, server.state.value)
        assertEquals(2, t.loads.size)
        assertEquals(t.loads.first(), t.loads.last())
        t.listener(File("$root/runtime-to-app/bridge-ready.json").readText())
        t.listener(File("$root/runtime-to-app/prepared.json").readText())
        assertEquals(LeadFormState.Activating, server.state.value)

        // Rate limited (429), timed out (408), unreachable (null) and a 503 are passing conditions.
        for (status in listOf(429, 408, 503, null)) {
            val other = FakeTransport()
            val c = preparingOn(other)
            other.loadFailed(status)
            assertEquals("$status", LeadFormState.Failed("temporary_failure", true), c.state.value)
            assertEquals("$status", 0, other.destroyed)
            c.retry()
            assertEquals("$status", LeadFormState.Preparing, c.state.value)
            assertEquals("$status", 2, other.loads.size)
        }
    }

    @Test
    fun `a page failure reported after the form's bridge is up does not end the form`() {
        val c = controller()
        prepared(c)
        transport.loadFailed(500)
        assertEquals(LeadFormState.Prepared, c.state.value)
        transport.loadFailed(404)
        assertEquals(LeadFormState.Prepared, c.state.value)
        assertEquals(0, transport.destroyed)
    }

    @Test
    fun `a page failure reported after the app disposed the controller changes nothing`() {
        val c = controller()
        c.prepare()
        c.dispose()
        transport.loadFailed(404)
        assertEquals(LeadFormState.Disposed, c.state.value)
        transport.loadFailed(500)
        assertEquals(LeadFormState.Disposed, c.state.value)
    }

    /** Runs [onState] inline, on the thread that publishes each state, as a main-thread collector does. */
    private fun collectInline(c: LeadFormController, onState: (LeadFormState) -> Unit): Job =
        CoroutineScope(Dispatchers.Unconfined).launch { c.state.collect { onState(it) } }

    @Test
    fun `a tap that was queued while preparing opens the form once, even if the app opens it at the same moment`() {
        val c = controller()
        c.prepare()
        c.open()
        val job = collectInline(c) { if (it == LeadFormState.Prepared) c.open() }
        page("bridge-ready")
        page("prepared")
        job.cancel()
        assertEquals(1, sentOf("activate").size)
        assertEquals(LeadFormState.Activating, c.state.value)
    }

    @Test
    fun `an app that disposes the form while its page reloads leaves it disposed`() {
        val c = controller()
        c.prepare()
        page("bridge-ready")
        page("prepared")
        val job = collectInline(c) { if (it == LeadFormState.Preparing) c.dispose() }
        page("bridge-ready")
        job.cancel()
        assertEquals(LeadFormState.Disposed, c.state.value)
        assertEquals(1, sentOf("initialize").size)
    }

    @Test
    fun `a tap queued before the preparation expired does not activate it afterwards`() {
        val c = controller()
        c.prepare()
        c.open()
        page("bridge-ready")
        now = expiresAt + 1
        page("prepared")
        assertEquals(LeadFormState.Expired, c.state.value)
        assertTrue(sentOf("activate").isEmpty())

        // Before the deadline the same tap activates normally.
        now = 0
        val t = FakeTransport()
        val live = controller(on = t)
        live.prepare()
        live.open()
        t.listener(File("$root/runtime-to-app/bridge-ready.json").readText())
        t.listener(File("$root/runtime-to-app/prepared.json").readText())
        assertEquals(LeadFormState.Activating, live.state.value)
        assertEquals(1, t.sent.count { it.contains("\"activate\"") })
    }

    @Test
    fun `retrying an activation after the preparation expired does not activate it`() {
        val c = controller()
        prepared(c)
        c.open()
        page("prepare-failed")
        assertEquals(LeadFormState.Failed("temporary_failure", true), c.state.value)
        now = expiresAt + 1
        c.retry()
        assertEquals(LeadFormState.Expired, c.state.value)
        assertEquals(1, sentOf("activate").size)
    }

    @Test
    fun `a prefill that cannot be sent fails the form instead of leaving it opening forever`() {
        val c = controller()
        prepared(c)
        c.open()
        transport.failSendOf = "prefill"
        page("activated")
        assertEquals(LeadFormState.Failed("bridge_error", false), c.state.value)
        assertTrue(sentOf("prefill").isEmpty())
        c.retry()
        assertEquals(LeadFormState.Failed("bridge_error", false), c.state.value)
    }

    @Test
    fun `a form shown at the moment it is ready is told it is visible once`() {
        val c = controller()
        prepared(c)
        c.open()
        page("activated")
        val job = collectInline(c) { if (it == LeadFormState.ReadyToShow) c.setVisible(true) }
        page("prefill-applied")
        job.cancel()
        assertEquals(LeadFormState.Visible, c.state.value)
        assertEquals(1, sentOf("became_visible").size)
    }

    @Test
    fun `a renderer that is gone fails the controller`() {
        val c = controller()
        visible(c)
        transport.gone()
        assertEquals(LeadFormState.Failed("renderer_gone", false), c.state.value)
        assertEquals(1, transport.destroyed)
        c.dispose()
        assertEquals(LeadFormState.Disposed, c.state.value)
        assertEquals(listOf("became_visible").size, sentOf("became_visible").size)
        assertTrue(sentOf("dispose").isEmpty())
    }

    @Test
    fun `a refused message is logged by reason only`() {
        val c = controller()
        c.prepare()
        pageRaw("""{"type":"bridge_ready","payload":"alex.secret@example.invalid"}""")
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("bad_envelope"))
        assertTrue(logs.none { it.contains("alex") })
        assertEquals(LeadFormState.Preparing, c.state.value)
    }

    @Test
    fun `the controller's description never shows the preparation token`() {
        val c = controller()
        prepared(c)
        c.open()
        val shown = listOf(c.toString(), c.state.value.toString(), LeadFormState.Failed("x", false).toString())
        shown.forEach { assertTrue(it, !it.contains(token) && !it.contains(launchUrl)) }
        assertEquals(
            "LeadFormController(formInstanceId=$instanceId, presentation=FULL_SCREEN, state=Activating)",
            c.toString()
        )
    }

    @Test
    fun `each form event activation authorized is reported once`() {
        val c = controller()
        visible(c)
        pageRaw(LeadFormFixtures.eventMessage("form_open"))
        pageRaw(LeadFormFixtures.eventMessage("form_open"))
        pageRaw(LeadFormFixtures.eventMessage("form_start"))
        assertEquals(listOf(LeadFormFixtures.eventUrl("form_open"), LeadFormFixtures.eventUrl("form_start")), fired)
    }

    @Test
    fun `an event activation did not authorize is not reported`() {
        val c = controller()
        prepared(c)
        c.open()
        pageRaw(LeadFormFixtures.activated(events = listOf("form_open")))
        page("prefill-applied")
        c.setVisible(true)
        pageRaw(LeadFormFixtures.eventMessage("form_start"))
        assertTrue(fired.isEmpty())
        pageRaw(LeadFormFixtures.eventMessage("form_open"))
        assertEquals(listOf(LeadFormFixtures.eventUrl("form_open")), fired)
    }

    @Test
    fun `the creative's click is never fired by the form`() {
        val impression = "https://track.example.invalid/creative/impression"
        val click = "https://track.example.invalid/creative/click"
        val beacons = mutableListOf<String>()
        optedInSdk()
        val sdk = Admoai.getInstance()
        sdk.apiService = mockk(relaxed = true)
        sdk.beaconObserver = { beacons += it }
        sdk.leadFormScheduler = scheduler
        sdk.leadFormTransportFactory = { _, _ -> transport }
        val c = sdk.createLeadFormController(
            mockk<Context>(relaxed = true),
            Creative(
                tracking = com.admoai.sdk.model.response.TrackingInfo(
                    impressions = listOf(com.admoai.sdk.model.response.TrackingDetail("default", impression)),
                    clicks = listOf(com.admoai.sdk.model.response.TrackingDetail("default", click))
                ),
                interactiveExperience = descriptor(System.currentTimeMillis() + 60_000)
            ),
            FormPresentation.FULL_SCREEN,
            host
        )!!
        visible(c)
        pageRaw(LeadFormFixtures.eventMessage("form_open"))
        pageRaw(LeadFormFixtures.eventMessage("form_start"))
        page("submitting")
        page("submitted")
        pageRaw(websiteRequest())
        pageRaw(LeadFormFixtures.eventMessage("website_cta_click"))
        assertEquals(
            listOf(
                LeadFormFixtures.eventUrl("form_open"),
                LeadFormFixtures.eventUrl("form_start"),
                LeadFormFixtures.eventUrl("website_cta_click")
            ),
            beacons
        )
        assertTrue(beacons.none { it == impression || it == click })
    }

    @Test
    fun `a form event's tracking query never appears in the SDK's log`() {
        val url = LeadFormFixtures.eventUrl("form_open")
        val line = "REQUEST: https://api.tenant.example.invalid/v1/tracking/events?t=abc&e=def"
        assertEquals(
            "REQUEST: https://api.tenant.example.invalid/v1/tracking/events?t=REDACTED&e=REDACTED",
            CredentialRedaction.redact(line)
        )
        val body = """{"url":"$url","note":"x"}"""
        val shown = CredentialRedaction.redact(body)
        assertTrue(shown, !shown.contains("REDACTED_TRACKING_ONLY_FORM_OPEN"))
        assertTrue(shown, shown.contains("https://api.tenant.example.invalid/v1/tracking/events?t=REDACTED"))
        // Other URLs and the credential redaction are untouched.
        assertEquals(
            "GET https://api.example.invalid/ads?x=1",
            CredentialRedaction.redact("GET https://api.example.invalid/ads?x=1")
        )
        assertEquals(
            """{"preparationToken":"REDACTED"}""",
            CredentialRedaction.redact("""{"preparationToken":"tok"}""")
        )
    }
}
