package com.admoai.sdk.interactive

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.request.InteractiveExperienceCapability
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lead Generation opt-in on a decision request: the scenarios of
 * `src/main/kotlin/com/admoai/sdk/interactive/InteractiveExperienceOptIn.feature`, one test per
 * scenario, named after it. `every scenario has a test` holds the binding: a scenario added to the
 * feature without a test here, or a test whose scenario was renamed away, fails the build.
 */
@OptIn(ExperimentalInteractiveExperienceApi::class)
class InteractiveExperienceOptInTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val fixtures = "src/test/resources/interactive-experience-contract/decision"

    @Before
    fun setUp() {
        Admoai.resetForTesting()
        init(apiVersion = "2025-11-01")
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
    }

    private fun init(apiVersion: String?) {
        Admoai.initialize(
            SDKConfig(baseUrl = "https://test.admoai.com", apiVersion = apiVersion, networkClientEngine = null)
        )
        Admoai.getInstance().secureFormSurfaceAvailable = { true }
    }

    private fun builder() = DecisionRequestBuilder().addPlacement("ride_summary_banner")

    private fun wire(request: DecisionRequest = builder().build()): String =
        json.encodeToString(Admoai.getInstance().prepareFinalDecisionRequest(request))

    private fun wireObject(): JsonObject = json.parseToJsonElement(wire()) as JsonObject

    private fun fixture(name: String): JsonObject =
        json.parseToJsonElement(File("$fixtures/$name").readText()) as JsonObject

    private fun provider(vararg keys: StandardAttributeKey) = object : LeadFormValueProvider {
        override fun availableKeys(): Set<StandardAttributeKey> = keys.toSet()
    }

    private fun failingProvider() = object : LeadFormValueProvider {
        override fun availableKeys(): Set<StandardAttributeKey> = throw IllegalStateException(SECRET)
    }

    private fun declared(): JsonElement? = wireObject()["interactiveExperience"]

    private fun assertDeclaredKeys(vararg keys: String) {
        val d = declared() as JsonObject
        val expected = """{"supported":true,"availableAttributeKeys":[${keys.joinToString(",") { "\"$it\"" }}]}"""
        assertEquals(json.parseToJsonElement(expected), d)
    }

    private fun assertUnchangedFrom(baseline: String) {
        val actual = wire()
        assertFalse(actual.contains("interactiveExperience"))
        assertEquals(baseline, actual)
    }

    private fun captureLogs(): MutableList<Pair<String, Admoai.LogLevel>> {
        val captured = mutableListOf<Pair<String, Admoai.LogLevel>>()
        Admoai.getInstance().logSink = { message, level, _ -> captured.add(message to level) }
        return captured
    }

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/interactive/InteractiveExperienceOptIn.feature")
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
    fun `an SDK update alone declares nothing`() {
        val baseline = wire()
        assertFalse(baseline.contains("interactiveExperience"))
        assertEquals(
            fixture("request-omitted.json").keys,
            json.parseToJsonElement(baseline).let {
                (it as JsonObject).keys
            }
        )
        assertEquals(baseline, wire(builder().build()))
    }

    @Test
    fun `an opted-in app declares the standard values it holds, by key only`() {
        Admoai.getInstance().enableInteractiveExperiences(
            provider(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL, StandardAttributeKey.PHONE)
        )
        assertEquals(fixture("request-supported-with-keys.json")["interactiveExperience"], declared())
        assertDeclaredKeys("fullName", "email", "phone")
    }

    @Test
    fun `an opted-in app that holds no standard value still declares support`() {
        Admoai.getInstance().enableInteractiveExperiences(provider())
        assertEquals(fixture("request-supported-empty-keys.json")["interactiveExperience"], declared())
    }

    @Test
    fun `keys are declared once each, in catalog order`() {
        Admoai.getInstance().enableInteractiveExperiences(
            provider(StandardAttributeKey.GENDER, StandardAttributeKey.EMAIL, StandardAttributeKey.FIRST_NAME)
        )
        assertDeclaredKeys("firstName", "email", "gender")
    }

    @Test
    fun `a provider that fails declares no key and the request still goes out`() {
        val logs = captureLogs()
        Admoai.getInstance().enableInteractiveExperiences(failingProvider())
        assertEquals(fixture("request-supported-empty-keys.json")["interactiveExperience"], declared())
        val failure = logs.single { it.first.contains("value provider failed") }
        assertEquals(Admoai.LogLevel.WARNING, failure.second)
        assertTrue(failure.first.contains("IllegalStateException"))
        assertFalse(logs.any { it.first.contains(SECRET) })
    }

    @Test
    fun `a device without a secure form view does not declare support`() {
        val baseline = wire()
        Admoai.getInstance().secureFormSurfaceAvailable = { false }
        Admoai.getInstance().enableInteractiveExperiences(provider(StandardAttributeKey.EMAIL))
        assertUnchangedFrom(baseline)
    }

    @Test
    fun `an app that turns the opt-in off stops declaring support`() {
        val baseline = wire()
        Admoai.getInstance().enableInteractiveExperiences(provider(StandardAttributeKey.EMAIL))
        assertNotNull(declared())
        Admoai.getInstance().disableInteractiveExperiences()
        assertUnchangedFrom(baseline)
    }

    @Test
    fun `a declaration a request cannot set by hand`() {
        val baseline = wire()
        val forged = builder().build().copy(
            interactiveExperience = InteractiveExperienceCapability(true, listOf("email"))
        )
        assertEquals(baseline, wire(forged))
        assertFalse(wire(forged).contains("interactiveExperience"))
    }

    @Test
    fun `an opted-in app on an older API version is warned that Lead Generation will not serve`() {
        listOf("2025-01-01", null).forEach { version ->
            Admoai.resetForTesting()
            init(apiVersion = version)
            val logs = captureLogs()
            Admoai.getInstance().enableInteractiveExperiences(provider(StandardAttributeKey.EMAIL))
            wire()
            assertTrue(
                "expected the apiVersion warning for $version",
                logs.any {
                    it.second == Admoai.LogLevel.WARNING &&
                        it.first.contains("served on apiVersion \"2025-11-01\" only")
                }
            )
        }
        Admoai.resetForTesting()
        init(apiVersion = "2025-11-01")
        val logs = captureLogs()
        Admoai.getInstance().enableInteractiveExperiences(provider(StandardAttributeKey.EMAIL))
        wire()
        assertFalse(logs.any { it.first.contains("2025-11-01") })
    }

    @Test
    fun `an app compiled against the previous release still builds and copies its requests`() {
        val placements = Class.forName("java.util.List")
        val classes: Array<Class<*>> = arrayOf(
            placements,
            com.admoai.sdk.model.request.Targeting::class.java,
            com.admoai.sdk.model.request.User::class.java,
            com.admoai.sdk.model.request.App::class.java,
            com.admoai.sdk.model.request.Device::class.java,
            String::class.java,
            com.admoai.sdk.model.common.JourneyOpt::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        )
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        val list = builder().build().placements

        val ctor = DecisionRequest::class.java.getConstructor(*classes)
        val built = ctor.newInstance(list, null, null, null, null, null, null, true, true) as DecisionRequest
        assertEquals(null, built.interactiveExperience)

        val defaultCtor = DecisionRequest::class.java.getConstructor(*classes, Int::class.javaPrimitiveType, marker)
        val viaDefault = defaultCtor.newInstance(
            list, null, null, null, null, null, null, true, true, 0b1_1111_1110, null
        ) as DecisionRequest
        assertEquals(DecisionRequest(placements = list), viaDefault)

        val declared = built.copy(interactiveExperience = InteractiveExperienceCapability(true, listOf("email")))
        val copied = DecisionRequest::class.java.getMethod("copy", *classes)
            .invoke(declared, list, null, null, null, null, "s", null, true, true) as DecisionRequest
        assertEquals("s", copied.sessionId)
        assertEquals(declared.interactiveExperience, copied.interactiveExperience)

        val copyDefault = DecisionRequest::class.java.getMethod(
            "copy\$default",
            DecisionRequest::class.java,
            *classes,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        // Mask 0b1_1101_1111: only sessionId (bit 5) is passed, the rest default to this.*.
        val viaCopyDefault = copyDefault.invoke(
            null, declared, null, null, null, null, null, "t", null, false, false, 0b1_1101_1111, null
        ) as DecisionRequest
        assertEquals(declared.copy(sessionId = "t"), viaCopyDefault)

        // Built through the old path, the request still carries no declaration on the wire.
        assertFalse(wire(built).contains("interactiveExperience"))
    }

    private companion object {
        const val SECRET = "secret-value-xyz"
    }
}
