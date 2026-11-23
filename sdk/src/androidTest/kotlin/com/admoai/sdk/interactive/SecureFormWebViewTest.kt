package com.admoai.sdk.interactive

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The scenarios of `src/main/kotlin/com/admoai/sdk/interactive/SecureFormWebView.feature`, one test
 * per scenario, named after it, on a real WebView. Pages come from `src/androidTest/assets/interactive`
 * and are served over HTTPS through [WebViewAssetLoader], installed as the view's test interceptor.
 * `SecureFormWebViewBindingTest` (JVM) holds the binding between this class and the feature. Test
 * names keep the scenario's words; `@JvmName` gives each a dex-safe name (minSdk 24 forbids spaces).
 */
@RunWith(AndroidJUnit4::class)
class SecureFormWebViewTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testContext: Context = instrumentation.context
    private val origin = "https://appassets.androidplatform.net"
    private val launchBase = "$origin/assets/interactive"
    private val fragment = "#instanceId=form_instance_01KG000000000000000000N001&channel=chan_example_0000000001"

    private val formLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(testContext))
        .build()
    private val foreignLoader = WebViewAssetLoader.Builder()
        .setDomain("other.appassets.example")
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(testContext))
        .build()

    private val requests = CopyOnWriteArrayList<String>()
    private val received = CopyOnWriteArrayList<String>()
    private val blocks = java.util.concurrent.atomic.AtomicInteger()
    private val loadFailures = CopyOnWriteArrayList<Int?>()
    private var latch = CountDownLatch(0)
    private lateinit var transport: SecureFormWebView
    private lateinit var parent: FrameLayout
    private var destroyed = false

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { throw it }
    }

    private fun serve(request: WebResourceRequest): WebResourceResponse? {
        requests += request.url.toString()
        return formLoader.shouldInterceptRequest(request.url) ?: foreignLoader.shouldInterceptRequest(request.url)
    }

    private fun createView() {
        onMain {
            parent = FrameLayout(testContext)
            transport = SecureFormWebView(testContext, origin)
            transport.testInterceptor = ::serve
            transport.setOnMessage {
                received += it
                latch.countDown()
            }
            transport.setOnNavigationBlocked { blocks.incrementAndGet() }
            transport.setOnLoadFailed { loadFailures += it }
            parent.addView(transport.view)
        }
    }

    private fun await(count: Int = 1, block: () -> Unit) {
        latch = CountDownLatch(count)
        onMain(block)
        assertTrue("timed out waiting for $count message(s); got $received", latch.await(10, TimeUnit.SECONDS))
    }

    private fun loadForm(page: String = "form.html", hash: String = fragment, messages: Int = 2) {
        createView()
        await(messages) { transport.load("$launchBase/$page$hash") }
    }

    private fun awaitBlocked() {
        val deadline = System.currentTimeMillis() + 10_000
        while (blocks.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals("the view reported the refused navigation once", 1, blocks.get())
    }

    @After
    fun tearDown() {
        if (!destroyed && ::transport.isInitialized) onMain { transport.destroy() }
    }

    @Test
    @JvmName("scenario_the_form_page_at_the_launch_origin_reaches_the_app")
    fun `the form page at the launch origin reaches the app`() {
        loadForm()
        assertTrue(received.contains("hello"))
    }

    @Test
    @JvmName("scenario_the_form_receives_the_app_s_messages")
    fun `the form receives the app's messages`() {
        loadForm()
        await { transport.send("{\"type\":\"initialize\",\"quote\":\"\\\"\"}") }
        assertEquals("echo:{\"type\":\"initialize\",\"quote\":\"\\\"\"}", received.last())
    }

    @Test
    @JvmName("scenario_the_page_learns_its_instance_and_channel_from_the_launch_url_fragment")
    fun `the page learns its instance and channel from the launch URL fragment`() {
        loadForm()
        assertTrue(
            received.toString(),
            received.contains(
                "address:no-query:form_instance_01KG000000000000000000N001:chan_example_0000000001"
            )
        )
        var url: String? = null
        onMain { url = (transport.view as android.webkit.WebView).url }
        assertEquals("$launchBase/form.html$fragment", url)
    }

    @Test
    @JvmName("scenario_a_frame_from_another_origin_cannot_reach_the_app")
    fun `a frame from another origin cannot reach the app`() {
        loadForm(page = "frame-host.html", hash = "#frame=other", messages = 1)
        Thread.sleep(2000)
        assertEquals(listOf("frame-loaded"), received.toList())
    }

    @Test
    @JvmName("scenario_a_frame_from_the_same_origin_cannot_reach_the_app")
    fun `a frame from the same origin cannot reach the app`() {
        loadForm(page = "frame-host.html", hash = "#frame=same", messages = 1)
        Thread.sleep(2000)
        assertEquals(listOf("frame-loaded"), received.toList())
    }

    @Test
    @JvmName("scenario_the_form_cannot_navigate_away")
    fun `the form cannot navigate away`() {
        loadForm()
        requests.clear()
        onMain { transport.send("navigate") }
        // The page answers nothing: it was asked to navigate. The view must report the refusal.
        awaitBlocked()
        var url: String? = null
        onMain { url = (transport.view as android.webkit.WebView).url }
        assertEquals("$launchBase/form.html$fragment", url)
        assertTrue(requests.toString(), requests.none { it.contains("other.appassets.example") })
        assertEquals("still reported once", 1, blocks.get())
    }

    @Test
    @JvmName("scenario_the_form_cannot_submit_a_page_form_or_load_a_second_page")
    fun `the form cannot submit a page form or load a second page`() {
        loadForm()
        requests.clear()
        onMain { transport.send("post") }
        Thread.sleep(2000)
        var url: String? = null
        onMain { url = (transport.view as android.webkit.WebView).url }
        assertEquals("$launchBase/form.html$fragment", url)
        assertEquals(1, blocks.get())
        assertTrue(requests.toString(), requests.none { it.contains("frame.html") })
    }

    @Test
    @JvmName("scenario_a_form_page_that_answers_an_error_is_reported_to_the_app")
    fun `a form page that answers an error is reported to the app`() {
        createView()
        onMain {
            transport.testInterceptor = { request ->
                requests += request.url.toString()
                WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    404,
                    "Not Found",
                    emptyMap(),
                    java.io.ByteArrayInputStream("form_not_available".toByteArray())
                )
            }
            transport.load("$launchBase/form.html$fragment")
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (loadFailures.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        Thread.sleep(1000)
        assertEquals(listOf<Int?>(404), loadFailures.toList())
    }

    @Test
    @JvmName("scenario_the_form_cannot_open_windows_or_popups")
    fun `the form cannot open windows or popups`() {
        loadForm()
        requests.clear()
        await { transport.send("open") }
        assertEquals("open:null", received.last())
        Thread.sleep(1000)
        assertTrue(requests.toString(), requests.none { it.contains("other.appassets.example") })
    }

    @Test
    @JvmName("scenario_files_and_content_providers_are_out_of_reach")
    fun `files and content providers are out of reach`() {
        loadForm()
        await { transport.send("files") }
        assertEquals("files:refused,refused,refused", received.last())
    }

    @Test
    @JvmName("scenario_the_form_view_is_locked_down")
    fun `the form view is locked down`() {
        createView()
        onMain {
            val s = (transport.view as android.webkit.WebView).settings
            assertFalse(s.allowFileAccess)
            assertFalse(s.allowContentAccess)
            assertFalse(s.domStorageEnabled)
            assertFalse(s.supportMultipleWindows())
            assertFalse(s.javaScriptCanOpenWindowsAutomatically)
            assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, s.mixedContentMode)
            assertTrue(s.javaScriptEnabled)
        }
    }

    @Test
    @JvmName("scenario_destroying_the_view_removes_the_bridge")
    fun `destroying the view removes the bridge`() {
        loadForm()
        await { transport.send("tick") }
        onMain { transport.destroy() }
        destroyed = true
        val after = received.size
        Thread.sleep(1000)
        assertEquals("no post reaches the app after destroy", after, received.size)
        assertNull(transport.view.parent)
        assertNotNull(received.find { it == "tick" })
    }
}
