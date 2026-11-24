package com.admoai.sdk.interactive

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.network.AdMoaiApiServiceImpl
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scenarios of `src/main/kotlin/com/admoai/sdk/interactive/CredentialRedaction.feature`, one test
 * per scenario, named after it.
 */
class CredentialRedactionTest {

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/interactive/CredentialRedaction.feature")
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

    /** Fires [url] through the SDK's real network service with logging on, and returns what it printed. */
    private fun printedWhileFiring(url: String): String {
        val printed = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(printed, true, "UTF-8"))
        try {
            val service = AdMoaiApiServiceImpl(
                SDKConfig(baseUrl = "https://api.tenant.example.invalid", enableLogging = true),
                MockEngine { respond("", HttpStatusCode.OK) }
            )
            try {
                runBlocking { service.fireTrackingUrl(url).first() }
            } finally {
                service.close()
            }
        } finally {
            System.setOut(original)
        }
        return printed.toString("UTF-8")
    }

    @Test
    fun `a form event's tracking query never appears in the SDK's log`() {
        val url = LeadFormFixtures.eventUrl("form_open")
        val line = "https://api.tenant.example.invalid/v1/tracking/events?t=abc&e=def"
        val shown = printedWhileFiring(line)
        assertTrue(shown, shown.contains("https://api.tenant.example.invalid/v1/tracking/events?t=REDACTED&e=REDACTED"))
        assertFalse(shown, shown.contains("abc") || shown.contains("def"))
        // The form's own event URL, as the page hands it over, goes through the same logger.
        val eventShown = printedWhileFiring(url)
        assertTrue(eventShown, eventShown.contains("https://api.tenant.example.invalid/v1/tracking/events?t=REDACTED"))
        assertFalse(eventShown, eventShown.contains("REDACTED_TRACKING_ONLY_FORM_OPEN"))
    }

    @Test
    fun `a form event on any path has its query values redacted in the SDK's log`() {
        assertEquals(
            "REQUEST: https://track.example.invalid/events/open?e=REDACTED&k=REDACTED#frag",
            CredentialRedaction.redact("REQUEST: https://track.example.invalid/events/open?e=opaque-secret&k=2#frag")
        )
        assertEquals(
            "https://track.example.invalid/pixel.gif",
            CredentialRedaction.redact("https://track.example.invalid/pixel.gif")
        )
        // An apostrophe inside a value does not end the URL: nothing after it is left in the log.
        assertEquals(
            "GET https://track.example.invalid/events?e=REDACTED&k=REDACTED",
            CredentialRedaction.redact("GET https://track.example.invalid/events?e=first'second&k=3")
        )
        // An apostrophe in the path does not hide the query either.
        assertEquals(
            "REQUEST: https://track.example.invalid/events/o'pen?e=REDACTED",
            CredentialRedaction.redact("REQUEST: https://track.example.invalid/events/o'pen?e=opaque-secret")
        )
        // A value after an empty name, and a part with no name at all, are redacted too.
        assertEquals(
            "https://track.example.invalid/events?=REDACTED&k=REDACTED&REDACTED",
            CredentialRedaction.redact("https://track.example.invalid/events?=opaque-secret&k=2&bare-secret")
        )
        // A final apostrophe still closes a URL quoted with them.
        assertEquals(
            "url='https://track.example.invalid/e?e=REDACTED' sent",
            CredentialRedaction.redact("url='https://track.example.invalid/e?e=opaque' sent")
        )
        // Any other URL is redacted the same way.
        assertEquals(
            "GET https://api.example.invalid/ads?x=REDACTED",
            CredentialRedaction.redact("GET https://api.example.invalid/ads?x=1")
        )
        // Any other backslash still ends the URL.
        assertEquals(
            """{"u":"https://x.example.invalid/v?r=REDACTED\n"}""",
            CredentialRedaction.redact("""{"u":"https://x.example.invalid/v?r=a\n"}""")
        )
    }

    @Test
    fun `query values the engine wrote with an escaped ampersand are redacted too`() {
        // As the engine's Go json.Marshal writes it: an ampersand inside a string is backslash, u, 0026.
        val body = """{"url":"https://x.example.invalid/v?r=a&ts=1&sig=s3cr3t"}"""
        assertEquals(
            """{"url":"https://x.example.invalid/v?r=REDACTED&ts=REDACTED&sig=REDACTED"}""",
            CredentialRedaction.redact(body)
        )
        // Mixed with a plain ampersand, and with a fragment after the query.
        assertEquals(
            """{"url":"https://x.example.invalid/v?r=REDACTED&ts=REDACTED&sig=REDACTED#f"}""",
            CredentialRedaction.redact("""{"url":"https://x.example.invalid/v?r=a&ts=1&sig=s3cr3t#f"}""")
        )
    }

    @Test
    fun `the form's tokens are redacted from logged JSON`() {
        assertEquals(
            """{"preparationToken":"REDACTED","note":"x"}""",
            CredentialRedaction.redact("""{"preparationToken":"tok","note":"x"}""")
        )
    }
}
