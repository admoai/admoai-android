package com.admoai.sdk.interactive

import android.content.Context
import android.view.View
import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.InteractiveExperience
import io.mockk.mockk
import java.io.File
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

    private class FakeTransport : BridgeTransport {
        val fakeView: View = mockk(relaxed = true)
        override val view: View? = fakeView
        val loads = mutableListOf<String>()
        val sent = mutableListOf<String>()
        var destroyed = 0
        var firstListener: ((String) -> Unit)? = null
        var listener: (String) -> Unit = {}
        var gone: () -> Unit = {}
        var blocked: () -> Unit = {}
        var loadFailed: (Int?) -> Unit = {}

        override fun load(url: String) {
            loads += url
        }

        override fun send(text: String) {
            sent += text
        }

        override fun setOnMessage(listener: (String) -> Unit) {
            if (firstListener == null) firstListener = listener
            this.listener = listener
        }

        override fun setOnRendererGone(listener: () -> Unit) {
            gone = listener
        }

        override fun setOnNavigationBlocked(listener: () -> Unit) {
            blocked = listener
        }

        override fun setOnLoadFailed(listener: (httpStatus: Int?) -> Unit) {
            loadFailed = listener
        }

        override fun destroy() {
            destroyed++
        }
    }

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
    private val host = FakeHost()

    private fun descriptor(expires: Long = expiresAt) = InteractiveExperience.LeadGeneration(
        formId = "form_01KG000000000000000000F001",
        formInstanceId = instanceId,
        preparedVersionId = "form_version_01KG000000000000000000V002",
        expiresAtEpochMillis = expires,
        prefillAttributeKeys = listOf(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL),
        launchUrl = launchUrl,
        preparationToken = token
    )

    private val logs = mutableListOf<String>()

    private fun controller(presentation: FormPresentation = FormPresentation.FULL_SCREEN) = LeadFormController(
        descriptor = descriptor(),
        presentation = presentation,
        host = host,
        transportFactory = {
            factoryCalls++
            transport
        },
        clock = { now },
        channel = channel,
        log = { logs += it }
    )

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun sentTypes() = transport.sent.map { parse(it).getValue("type").jsonPrimitive.content }

    private fun sentOf(type: String) = transport.sent.map { parse(it) }
        .filter { it.getValue("type").jsonPrimitive.content == type }

    /** The page speaks: the contract's own runtime fixture, as the page would send it. */
    private fun page(name: String) = transport.listener(File("$root/runtime-to-app/$name.json").readText())

    private fun pageRaw(text: String) = transport.listener(text)

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
        Admoai.getInstance().enableInteractiveExperiences(
            object : LeadFormValueProvider {
                override fun availableKeys(): Set<StandardAttributeKey> = emptySet()
            }
        )
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
            LeadFormController(descriptor(), FormPresentation.INLINE, host, { FakeTransport() }, { now }, channel)
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

    @Test
    fun `a form page that cannot be loaded fails instead of loading forever`() {
        val notFound = controller()
        notFound.prepare()
        transport.loadFailed(404)
        assertEquals(LeadFormState.Failed("form_not_available", false), notFound.state.value)
        assertEquals(1, transport.destroyed)
        notFound.retry()
        assertEquals(LeadFormState.Failed("form_not_available", false), notFound.state.value)

        val server = controller()
        server.open()
        assertEquals(LeadFormState.Preparing, server.state.value)
        val destroyedBefore = transport.destroyed
        transport.loadFailed(500)
        assertEquals(LeadFormState.Failed("temporary_failure", true), server.state.value)
        assertEquals("the view is kept for the retry", destroyedBefore, transport.destroyed)
        val loadsBefore = transport.loads.size
        server.retry()
        assertEquals(LeadFormState.Preparing, server.state.value)
        assertEquals(loadsBefore + 1, transport.loads.size)
        assertEquals(transport.loads[loadsBefore - 1], transport.loads.last())
        // The page arrives this time; the tap that opened it still activates it once prepared.
        page("bridge-ready")
        page("prepared")
        assertEquals(LeadFormState.Activating, server.state.value)

        val forbidden = controller()
        forbidden.prepare()
        transport.loadFailed(403)
        assertEquals(LeadFormState.Failed("form_not_available", false), forbidden.state.value)

        // Rate limited (429) and timed out (408) are passing conditions: the viewer can retry.
        for (status in listOf(429, 408)) {
            val passing = controller()
            passing.prepare()
            transport.loadFailed(status)
            assertEquals("$status", LeadFormState.Failed("temporary_failure", true), passing.state.value)
        }

        val unreachable = controller()
        unreachable.prepare()
        transport.loadFailed(null)
        assertEquals(LeadFormState.Failed("temporary_failure", true), unreachable.state.value)

        val up = controller()
        up.prepare()
        page("bridge-ready")
        page("prepared")
        transport.loadFailed(500)
        assertEquals(LeadFormState.Prepared, up.state.value)

        val disposed = controller()
        disposed.prepare()
        disposed.dispose()
        transport.loadFailed(404)
        assertEquals(LeadFormState.Disposed, disposed.state.value)
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
}
