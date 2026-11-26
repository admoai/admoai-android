package com.admoai.sdk.e2e

import com.admoai.sdk.interactive.StandardAttributeKey
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.InteractiveExperience
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Offline: schemaVersion 8, the Lead Generation descriptor assertion and the non-loopback base. */
class ManifestLeadGenerationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val now = 1_000_000L

    private fun lead(
        formId: String = "form_01ARZ3NDEKTSV4RRFFQ69G5FAV",
        preparedVersionId: String = "form_version_01ARZ3NDEKTSV4RRFFQ69G5FAV",
        formInstanceId: String = "form_instance_01ARZ3NDEKTSV4RRFFQ69G5FAV",
        expires: Long = now + 60_000,
        keys: List<StandardAttributeKey> = listOf(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL),
        launchUrl: String = "https://10.0.0.5:8080/interactive-experiences/runtime/index.html",
        token: String = "token"
    ) = InteractiveExperience.LeadGeneration(
        formId = formId,
        formInstanceId = formInstanceId,
        preparedVersionId = preparedVersionId,
        expiresAtEpochMillis = expires,
        prefillAttributeKeys = keys,
        launchUrl = launchUrl,
        preparationToken = token
    )

    private fun creative(experience: InteractiveExperience?): Creative {
        val c = json.decodeFromString<Creative>(
            """{"contents":[{"key":"headline","type":"text","value":"Hello"}],""" +
                """"template":{"key":"native","style":"standard"},""" +
                """"tracking":{"impressions":[{"key":"default","url":"https://t.example.com/i"}]},""" +
                """"delivery":"json"}"""
        )
        return c.copy(interactiveExperience = experience)
    }

    private fun spec(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private val wanted = """{"interactiveExperience":{"type":"lead_generation",""" +
        """"prefillAttributeKeys":["fullName","email"]}}"""

    private fun passes(c: Creative, expectation: String = wanted) {
        assertCreative(c, spec(expectation), now)
    }

    private fun fails(c: Creative, expectation: String = wanted, containing: String) {
        try {
            assertCreative(c, spec(expectation), now)
            fail("expected an assertion failure containing \"$containing\"")
        } catch (e: AssertionError) {
            assertTrue("message was: ${e.message}", e.message.orEmpty().contains(containing))
        }
    }

    @Test
    fun `schemaVersion 8 is executed and 9 is refused`() {
        checkSchemaVersion(6)
        checkSchemaVersion(7)
        checkSchemaVersion(8)
        try {
            checkSchemaVersion(9)
            fail("schemaVersion 9 must be refused")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("schemaVersion 9"))
        }
    }

    @Test
    fun `isLoopbackHost recognises loopback names and literals only`() {
        listOf("localhost", "127.0.0.1", "127.1.2.3", "[::1]", "::1").forEach { assertTrue(it, isLoopbackHost(it)) }
        listOf("10.0.0.5", "example.test", "128.0.0.1").forEach { assertFalse(it, isLoopbackHost(it)) }
    }

    @Test
    fun `lgBaseUrl lets the env override win`() {
        assertEquals("https://x.test/", lgBaseUrl("http://127.0.0.1:8080/", "https://x.test/", { "10.0.0.5" }))
    }

    @Test
    fun `lgBaseUrl keeps a base that is already non-loopback`() {
        assertEquals("http://engine.test:8080/", lgBaseUrl("http://engine.test:8080/", null, { error("not called") }))
    }

    @Test
    fun `lgBaseUrl swaps a loopback host and keeps scheme, port and path`() {
        assertEquals("http://10.0.0.5:8080/api/", lgBaseUrl("http://localhost:8080/api/", null, { "10.0.0.5" }))
        assertEquals("http://10.0.0.5:8080/", lgBaseUrl("http://127.0.0.1:8080/", null, { "10.0.0.5" }))
    }

    @Test
    fun `lgBaseUrl names the override when there is no address`() {
        try {
            lgBaseUrl("http://127.0.0.1:8080/", null, { null })
            fail("expected a failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains(LG_BASE_URL_ENV))
        }
    }

    @Test
    fun `a well-formed descriptor passes`() {
        passes(creative(lead()))
    }

    @Test
    fun `false passes only when the creative has no descriptor`() {
        passes(creative(null), """{"interactiveExperience":false}""")
        fails(creative(lead()), """{"interactiveExperience":false}""", containing = "carries no interactiveExperience")
    }

    @Test
    fun `an object fails when the creative has no descriptor`() {
        assertNull(creative(null).interactiveExperience)
        fails(creative(null), containing = "carries a lead_generation")
    }

    @Test
    fun `a http launchUrl fails`() {
        fails(creative(lead(launchUrl = "http://10.0.0.5:8080/x")), containing = "loopback host")
    }

    @Test
    fun `a launchUrl with a query fails`() {
        fails(creative(lead(launchUrl = "https://10.0.0.5/x?a=1")), containing = "launchUrl")
    }

    @Test
    fun `a launchUrl with a fragment fails`() =
        fails(creative(lead(launchUrl = "https://10.0.0.5/x#f")), containing = "launchUrl")

    @Test
    fun `prefill keys in the wrong order fail`() = fails(
        creative(lead(keys = listOf(StandardAttributeKey.EMAIL, StandardAttributeKey.FULL_NAME))),
        containing = "in that order"
    )

    @Test
    fun `a wrong id prefix fails`() {
        fails(creative(lead(formId = "frm_01ARZ3NDEKTSV4RRFFQ69G5FAV")), containing = "formId")
        fails(creative(lead(preparedVersionId = "form_01ARZ3NDEKTSV4RRFFQ69G5FAV")), containing = "preparedVersionId")
        fails(creative(lead(formInstanceId = "form_instance_short")), containing = "formInstanceId")
    }

    @Test
    fun `an expiry in the past fails`() {
        fails(creative(lead(expires = now - 1)), containing = "expiresAt")
    }

    @Test
    fun `an empty preparation token fails`() {
        fails(creative(lead(token = "")), containing = "preparationToken")
    }

    @Test
    fun `an unknown type, an unknown key or a non-object value fails`() {
        val c = creative(lead())
        fails(
            c,
            """{"interactiveExperience":{"type":"survey","prefillAttributeKeys":[]}}""",
            containing = "unknown type"
        )
        fails(
            c,
            """{"interactiveExperience":{"type":"lead_generation","prefillAttributeKeys":[],"x":1}}""",
            containing = "does not execute"
        )
        fails(c, """{"interactiveExperience":"yes"}""", containing = "must be an object or false")
    }
}
