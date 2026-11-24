package com.admoai.sdk.interactive

import android.content.Context
import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.config.UserConfig
import com.admoai.sdk.model.response.Creative
import io.mockk.mockk
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scenarios of `src/main/kotlin/com/admoai/sdk/interactive/LeadFormRegistry.feature`, one test per
 * scenario, named after it, driven through [Admoai.createLeadFormController] and its test seams.
 */
@OptIn(ExperimentalInteractiveExperienceApi::class)
class LeadFormRegistryTest {

    private class Page {
        val transport = FakeTransport()
    }

    private val scheduler = FakeScheduler()
    private val pages = mutableListOf<Page>()
    private val bound = BridgeContract.maxHiddenPreparedForms
    private var pendingCompletion: LeadFormValues? = null

    private val host = object : LeadFormHost {
        override fun onRequestClose(controller: LeadFormController) = Unit

        override fun onOpenExternalLink(controller: LeadFormController, purpose: ExternalLinkPurpose, url: String) =
            Unit
    }

    private fun provider(hold: Boolean = false) = object : LeadFormValueProvider {
        override fun availableKeys(): Set<StandardAttributeKey> = setOf(StandardAttributeKey.EMAIL)

        override fun lookUpValues(keys: Set<StandardAttributeKey>, completion: LeadFormValues) {
            if (hold) pendingCompletion = completion else completion.complete(emptyMap())
        }
    }

    @Before
    fun setUp() {
        Admoai.resetForTesting()
        Admoai.initialize(
            SDKConfig(baseUrl = "https://test.admoai.com", apiVersion = "2025-11-01", networkClientEngine = null)
        )
        val sdk = Admoai.getInstance()
        sdk.secureFormSurfaceAvailable = { true }
        sdk.leadFormScheduler = scheduler
        sdk.leadFormTransportFactory = { _, _ -> Page().also { pages += it }.transport }
        sdk.enableInteractiveExperiences(provider())
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
    }

    private fun create(): LeadFormController = Admoai.getInstance().createLeadFormController(
        mockk<Context>(relaxed = true),
        Creative(interactiveExperience = LeadFormFixtures.descriptor(System.currentTimeMillis() + 600_000)),
        FormPresentation.FULL_SCREEN,
        host
    )!!

    private val transports = java.util.IdentityHashMap<LeadFormController, FakeTransport>()

    private fun transportOf(c: LeadFormController): FakeTransport = transports.getValue(c)

    /** Remembers the transport a form created by the call just made (the factory knows no controller). */
    private fun tracked(c: LeadFormController, call: () -> Unit) {
        val before = pages.size
        call()
        if (pages.size > before) transports[c] = pages.last().transport
    }

    private fun newForm(): LeadFormController = create()

    /** A form that reached the page: its transport exists and the page prepared it. */
    private fun prepare(c: LeadFormController) {
        tracked(c) { c.prepare() }
        if (c.state.value == LeadFormState.Idle) return
        val t = transportOf(c)
        t.page("bridge-ready")
        t.page("prepared")
    }

    private fun show(c: LeadFormController) {
        prepare(c)
        c.open()
        val t = transportOf(c)
        t.page("activated")
        t.page("prefill-applied")
        c.setVisible(true)
    }

    private fun hiddenForms(): List<LeadFormController> = List(bound) { newForm().also { prepare(it) } }

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/interactive/LeadFormRegistry.feature")
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
    fun `a user change closes every open form`() {
        val sdk = Admoai.getInstance()
        sdk.setUserConfig(UserConfig(id = "user-a"))
        val a = newForm().also { prepare(it) }
        val b = newForm().also { show(it) }
        sdk.setUserConfig(UserConfig(id = "user-b"))
        assertEquals(LeadFormState.Disposed, a.state.value)
        assertEquals(LeadFormState.Disposed, b.state.value)
    }

    @Test
    fun `a lookup that answers after the user changed fills nothing`() {
        val sdk = Admoai.getInstance()
        sdk.enableInteractiveExperiences(provider(hold = true))
        sdk.setUserConfig(UserConfig(id = "user-a"))
        val c = newForm()
        prepare(c)
        c.open()
        val t = transportOf(c)
        t.page("activated")
        sdk.clearUserConfig()
        pendingCompletion!!.complete(mapOf(StandardAttributeKey.EMAIL to "a@example.invalid"))
        scheduler.advance(BridgeContract.prefillLookupTimeoutMs)
        assertEquals(LeadFormState.Disposed, c.state.value)
        assertTrue(t.sentOf("prefill").isEmpty())
    }

    @Test
    fun `a lookup answer that arrives after the user changed but before the forms are disposed sends nothing`() {
        val sdk = Admoai.getInstance()
        sdk.enableInteractiveExperiences(provider(hold = true))
        sdk.setUserConfig(UserConfig(id = "user-a"))
        val c = newForm()
        prepare(c)
        c.open()
        val t = transportOf(c)
        t.page("activated")
        scheduler.holdNextPost()
        sdk.clearUserConfig()
        pendingCompletion!!.complete(mapOf(StandardAttributeKey.EMAIL to "a@example.invalid"))
        assertTrue(t.sentOf("prefill").isEmpty())
        scheduler.runHeld()
        assertEquals(LeadFormState.Disposed, c.state.value)
        assertTrue(t.sentOf("prefill").isEmpty())
    }

    @Test
    fun `a form created while the app opts out is not kept`() {
        val sdk = Admoai.getInstance()
        // The surface check runs after the provider was read and before the form is registered.
        sdk.secureFormSurfaceAvailable = {
            sdk.disableInteractiveExperiences()
            true
        }
        val got = sdk.createLeadFormController(
            mockk<Context>(relaxed = true),
            Creative(interactiveExperience = LeadFormFixtures.descriptor(System.currentTimeMillis() + 600_000)),
            FormPresentation.FULL_SCREEN,
            host
        )
        assertEquals(null, got)
        assertTrue(pages.isEmpty())
    }

    @Test
    fun `a form created after a user change is not closed by the earlier change`() {
        val sdk = Admoai.getInstance()
        sdk.setUserConfig(UserConfig(id = "user-a"))
        val a = newForm().also { prepare(it) }
        scheduler.holdNextPost()
        sdk.setUserConfig(UserConfig(id = "user-b"))
        val b = newForm().also { prepare(it) }
        scheduler.runHeld()
        assertEquals(LeadFormState.Disposed, a.state.value)
        assertEquals(LeadFormState.Prepared, b.state.value)
    }

    @Test
    fun `setting the same user again keeps the forms`() {
        val sdk = Admoai.getInstance()
        sdk.setUserConfig(UserConfig(id = "user-a"))
        val c = newForm().also { prepare(it) }
        sdk.setUserConfig(UserConfig(id = "user-a", timezone = "UTC"))
        assertEquals(LeadFormState.Prepared, c.state.value)
    }

    @Test
    fun `turning the opt-in off or changing the value provider closes every form`() {
        val sdk = Admoai.getInstance()
        val off = newForm().also { show(it) }
        sdk.disableInteractiveExperiences()
        assertEquals(LeadFormState.Disposed, off.state.value)
        sdk.enableInteractiveExperiences(provider())
        val swapped = newForm().also { show(it) }
        sdk.enableInteractiveExperiences(provider())
        assertEquals(LeadFormState.Disposed, swapped.state.value)
    }

    @Test
    fun `the app can close every form at once`() {
        val a = newForm().also { prepare(it) }
        val b = newForm().also { prepare(it) }
        Admoai.getInstance().disposeAllLeadForms()
        assertEquals(LeadFormState.Disposed, a.state.value)
        assertEquals(LeadFormState.Disposed, b.state.value)
    }

    @Test
    fun `hidden prepared forms are bounded`() {
        hiddenForms()
        val extra = newForm()
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Idle, extra.state.value)
        assertEquals(bound, pages.size)
        tracked(extra) { extra.open() }
        assertEquals(LeadFormState.Preparing, extra.state.value)
        assertEquals(bound + 1, pages.size)
        val t = transportOf(extra)
        t.page("bridge-ready")
        t.page("prepared")
        assertEquals(LeadFormState.Activating, extra.state.value)
    }

    @Test
    fun `a form that becomes visible no longer counts toward the bound`() {
        val forms = hiddenForms()
        val extra = newForm()
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Idle, extra.state.value)
        forms.first().open()
        val t = transportOf(forms.first())
        t.page("activated")
        t.page("prefill-applied")
        forms.first().setVisible(true)
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Preparing, extra.state.value)
    }

    @Test
    fun `a disposed form frees its place`() {
        val forms = hiddenForms()
        val extra = newForm()
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Idle, extra.state.value)
        forms.first().dispose()
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Preparing, extra.state.value)
    }

    @Test
    fun `a failed form frees its place`() {
        val forms = hiddenForms()
        val extra = newForm()
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Idle, extra.state.value)
        transportOf(forms.first()).blocked()
        assertEquals(LeadFormState.Failed("navigation_blocked", false), forms.first().state.value)
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Preparing, extra.state.value)
    }

    @Test
    fun `an expired form frees its place`() {
        val forms = hiddenForms()
        val extra = newForm()
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Idle, extra.state.value)
        val pageSaysExpired = File("$CONTRACT_BRIDGE_DIR/runtime-to-app/prepare-failed.json").readText()
            .replace("temporary_failure", "expired")
        transportOf(forms.first()).say(pageSaysExpired)
        assertEquals(LeadFormState.Expired, forms.first().state.value)
        tracked(extra) { extra.prepare() }
        assertEquals(LeadFormState.Preparing, extra.state.value)
    }
}
