package com.admoai.sdk.model.response

import com.admoai.sdk.interactive.CredentialRedaction
import com.admoai.sdk.interactive.StandardAttributeKey
import com.admoai.sdk.serialization.parseRfc3339ToEpochMillis
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Lead Generation descriptor on a creative: the scenarios of
 * `src/main/kotlin/com/admoai/sdk/model/response/InteractiveExperience.feature`, one test per
 * scenario (the outline is one test that runs every Examples row), named after it.
 * `every scenario has a test` holds the binding, including the number of outline rows.
 */
class InteractiveExperienceTest {

    // The real response Json of AdMoaiApiServiceImpl.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val featurePath = "src/main/kotlin/com/admoai/sdk/model/response/InteractiveExperience.feature"

    private val descriptor: JsonObject = (
        json.parseToJsonElement(
            File("src/test/resources/interactive-experience-contract/decision/creative-descriptor.json").readText()
        ) as JsonObject
        )["interactiveExperience"] as JsonObject

    private fun creativeJson(experience: JsonElement?, formId: String = "ride_summary_banner"): String {
        val fields = mutableMapOf<String, JsonElement>(
            "contents" to json.parseToJsonElement(
                """[{"key":"headline","type":"text","value":"Hello"}]"""
            ),
            "template" to json.parseToJsonElement("""{"key":"native","style":"standard"}"""),
            "tracking" to json.parseToJsonElement(
                """{"impressions":[{"key":"default","url":"https://t.example.com/i"}]}"""
            ),
            "delivery" to JsonPrimitive("json")
        )
        if (experience != null) fields["interactiveExperience"] = experience
        return JsonObject(fields).toString().also { check(formId.isNotEmpty()) }
    }

    private fun response(vararg placements: Pair<String, JsonElement?>): DecisionResponse {
        val data = placements.joinToString(",") { (key, exp) ->
            """{"placement":"$key","creatives":[${creativeJson(exp)}]}"""
        }
        return json.decodeFromString("""{"success":true,"data":[$data]}""")
    }

    private fun experienceOf(placement: Pair<String, JsonElement?>): InteractiveExperience? =
        response(placement).data!!.single().creatives!!.single().interactiveExperience

    private fun assertRestDecodes(response: DecisionResponse, placement: String = "home") {
        assertTrue(response.success)
        val decision = response.data!!.single()
        assertEquals(placement, decision.placement)
        val creative = decision.creatives!!.single()
        assertEquals("json", creative.delivery)
        assertEquals("native", creative.template?.key)
        assertEquals(1, creative.contents.size)
        assertEquals(1, creative.tracking.impressions?.size)
    }

    private fun without(key: String) = JsonObject(descriptor - key)

    private fun with(key: String, value: JsonElement) = JsonObject(descriptor + (key to value))

    private val defects: Map<String, JsonElement> = mapOf(
        "no preparation token" to without("preparationToken"),
        "a blank form instance" to with("formInstanceId", JsonPrimitive("  ")),
        "a launch URL that is not HTTPS" to with("launchUrl", JsonPrimitive("http://api.example.com/runtime")),
        "an expiry that is not a timestamp" to with("expiresAt", JsonPrimitive("tomorrow")),
        "prefill keys that are not a list" to with("prefillAttributeKeys", JsonPrimitive("email")),
        "a value that is not an object" to JsonPrimitive("lead_generation")
    )

    @Test
    fun `every scenario has a test`() {
        val lines = File(featurePath).readLines().map { it.trim() }
        val titles = lines
            .filter { it.startsWith("Scenario:") || it.startsWith("Scenario Outline:") }
            .map { it.substringAfter(":").trim() }
            .toSet()
        val tests = javaClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .map { it.name }
            .toSet()
        assertTrue("the feature file has scenarios", titles.isNotEmpty())
        assertEquals(emptySet<String>(), titles - tests)

        val rows = lines.dropWhile { !it.startsWith("Examples:") }.drop(1)
            .filter { it.startsWith("|") }.drop(1)
            .map { it.trim('|').trim() }
        assertEquals("every Examples row is exercised", rows.toSet(), defects.keys)
    }

    @Test
    fun `a creative without a descriptor decodes as before`() {
        val r = response("home" to null)
        assertRestDecodes(r)
        assertNull(r.data!!.single().creatives!!.single().interactiveExperience)
        val withNull = response("home" to JsonNull)
        assertNull(withNull.data!!.single().creatives!!.single().interactiveExperience)
        assertRestDecodes(withNull)
    }

    @Test
    fun `a Lead Generation descriptor is usable`() {
        val experience = experienceOf("home" to descriptor)
        assertTrue(experience is InteractiveExperience.LeadGeneration)
        experience as InteractiveExperience.LeadGeneration
        assertEquals("form_01KG000000000000000000F001", experience.formId)
        assertEquals("form_instance_01KG000000000000000000N001", experience.formInstanceId)
        assertEquals("form_version_01KG000000000000000000V002", experience.preparedVersionId)
        assertEquals(parseRfc3339ToEpochMillis("2026-11-12T15:15:00Z"), experience.expiresAtEpochMillis)
        assertEquals(
            listOf(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL),
            experience.prefillAttributeKeys
        )
        assertRestDecodes(response("home" to descriptor))
    }

    @Test
    fun `each creative keeps its own descriptor`() {
        val second = with("formInstanceId", JsonPrimitive("form_instance_01KG000000000000000000N002"))
        val r = response("a" to descriptor, "b" to second)
        val ids = r.data!!.map {
            (it.creatives!!.single().interactiveExperience as InteractiveExperience.LeadGeneration).formInstanceId
        }
        assertEquals(
            listOf("form_instance_01KG000000000000000000N001", "form_instance_01KG000000000000000000N002"),
            ids
        )
    }

    @Test
    fun `an experience type this SDK does not know is unsupported`() {
        val unknown = with("type", JsonPrimitive("quiz"))
        val experience = experienceOf("home" to unknown)
        assertEquals(InteractiveExperience.Unsupported("quiz"), experience)
        assertRestDecodes(response("home" to unknown))
    }

    @Test
    fun `a malformed descriptor is unsupported and never fails the response`() {
        defects.forEach { (defect, value) ->
            val experience = experienceOf("home" to value)
            assertTrue("$defect: $experience", experience is InteractiveExperience.Unsupported)
            assertRestDecodes(response("home" to value))
        }
    }

    @Test
    fun `a prefill key this SDK does not know is dropped`() {
        val keys = JsonArray(listOf("fullName", "homeAddress", "email", "email").map { JsonPrimitive(it) })
        val experience = experienceOf("home" to with("prefillAttributeKeys", keys))
        assertEquals(
            listOf(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL),
            (experience as InteractiveExperience.LeadGeneration).prefillAttributeKeys
        )
    }

    @Test
    fun `the preparation token is never printed`() {
        val creative = response("home" to descriptor).data!!.single().creatives!!.single()
        val token = (creative.interactiveExperience as InteractiveExperience.LeadGeneration).preparationToken
        assertEquals("REDACTED_PREPARATION_TOKEN", token)
        assertFalse(creative.toString().contains(token))
        assertTrue(creative.toString().contains("preparationToken=REDACTED"))

        val body = """{"success":true,"data":[{"placement":"home","creatives":[${creativeJson(descriptor)}]}]}"""
        val redacted = CredentialRedaction.redact(body)
        assertFalse(redacted.contains(token))
        assertTrue(redacted.contains("\"preparationToken\":\"REDACTED\""))
        assertTrue(redacted.contains("form_instance_01KG000000000000000000N001"))
        assertTrue(redacted.contains("\"placement\":\"home\""))
    }

    @Test
    fun `an app compiled against the previous release still builds and copies creatives`() {
        val classes: Array<Class<*>> = arrayOf(
            java.util.List::class.java,
            Advertiser::class.java,
            TemplateInfo::class.java,
            TrackingInfo::class.java,
            CreativeMetadata::class.java,
            String::class.java,
            VastData::class.java,
            java.util.List::class.java,
            CreativeJourney::class.java
        )
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

        val built = Creative::class.java.getConstructor(*classes)
            .newInstance(emptyList<Content>(), Advertiser(), null, TrackingInfo(), null, "json", null, null, null)
            as Creative
        assertEquals("json", built.delivery)
        assertNull(built.interactiveExperience)

        val viaDefault = Creative::class.java
            .getConstructor(*classes, Int::class.javaPrimitiveType, marker)
            .newInstance(null, null, null, null, null, "x", null, null, null, 0b1_1101_1111, null) as Creative
        assertEquals(Creative(delivery = "x"), viaDefault)

        val original = response("home" to descriptor).data!!.single().creatives!!.single()
        assertTrue(original.interactiveExperience is InteractiveExperience.LeadGeneration)

        val copied = Creative::class.java.getMethod("copy", *classes).invoke(
            original, original.contents, original.advertiser, null, original.tracking, null, "copied", null, null,
            null
        ) as Creative
        assertEquals("copied", copied.delivery)
        assertEquals(original.interactiveExperience, copied.interactiveExperience)

        val copyDefault = Creative::class.java.getMethod(
            "copy\$default",
            Creative::class.java,
            *classes,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        // Mask 0b1_1101_1111: only delivery (bit 5) is passed.
        val viaCopyDefault = copyDefault.invoke(
            null, original, null, null, null, null, null, "d", null, null, null, 0b1_1101_1111, null
        ) as Creative
        assertEquals("d", viaCopyDefault.delivery)
        assertEquals(original.interactiveExperience, viaCopyDefault.interactiveExperience)
    }

    // --- Non-scenario unit tests ---

    @Test
    fun `descriptor round trips through the wire`() {
        val original = experienceOf("home" to descriptor)
        val creative = response("home" to descriptor).data!!.single().creatives!!.single()
        val encoded = json.encodeToString(Creative.serializer(), creative)
        val again = json.decodeFromString(Creative.serializer(), encoded).interactiveExperience
        assertEquals(original, again)
        assertNotNull(again)

        // An expiry with milliseconds keeps them through the round trip.
        val fractional = with("expiresAt", JsonPrimitive("2026-11-12T15:15:00.123Z"))
        val withMillis = response("home" to fractional).data!!.single().creatives!!.single()
        val decoded = json.decodeFromString(
            Creative.serializer(),
            json.encodeToString(Creative.serializer(), withMillis)
        ).interactiveExperience as InteractiveExperience.LeadGeneration
        assertEquals(parseRfc3339ToEpochMillis("2026-11-12T15:15:00.123Z"), decoded.expiresAtEpochMillis)
        assertEquals(withMillis.interactiveExperience, decoded)
    }

    @Test
    fun `redaction handles escaped quotes, spacing and every credential key`() {
        val text = """{"submissionToken" : "a\"b","recoveryToken":"x","other":"keep"}"""
        assertEquals(
            """{"submissionToken" : "REDACTED","recoveryToken":"REDACTED","other":"keep"}""",
            CredentialRedaction.redact(text)
        )
        // JSON may escape any character of a member name; it is the same member and is redacted too.
        val escaped = """{"preparation\u0054oken":"secret","\u0073ubmissionToken":"s2"}"""
        assertEquals(
            """{"preparation\u0054oken":"REDACTED","\u0073ubmissionToken":"REDACTED"}""",
            CredentialRedaction.redact(escaped)
        )
    }

    @Test
    fun `parseRfc3339ToEpochMillis reads Z, offsets and fractions and rejects the rest`() {
        assertEquals(1_700_000_000_000L, parseRfc3339ToEpochMillis("2023-11-14T22:13:20Z"))
        assertEquals(1_700_000_000_000L, parseRfc3339ToEpochMillis("2023-11-15T00:13:20+02:00"))
        assertEquals(1_700_000_000_000L, parseRfc3339ToEpochMillis("2023-11-14T17:13:20-05:00"))
        assertEquals(1_700_000_000_123L, parseRfc3339ToEpochMillis("2023-11-14T22:13:20.123456Z"))
        assertEquals(1_700_000_000_500L, parseRfc3339ToEpochMillis("2023-11-14T22:13:20.5Z"))
        assertNull(parseRfc3339ToEpochMillis("2023-13-14T22:13:20Z"))
        assertNull(parseRfc3339ToEpochMillis("2023-02-30T22:13:20Z"))
        assertNull(parseRfc3339ToEpochMillis("2023-11-14T22:13:20"))
        assertNull(parseRfc3339ToEpochMillis("garbage"))
        assertNull(parseRfc3339ToEpochMillis(""))
    }
}
