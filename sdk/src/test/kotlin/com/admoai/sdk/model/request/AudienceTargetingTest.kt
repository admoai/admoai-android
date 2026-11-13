package com.admoai.sdk.model.request

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.audience.AccountTenure
import com.admoai.sdk.model.request.audience.AccountType
import com.admoai.sdk.model.request.audience.AgeGroup
import com.admoai.sdk.model.request.audience.Gender
import com.admoai.sdk.model.request.audience.LastTripRecency
import com.admoai.sdk.model.request.audience.SubscriptionStatus
import com.admoai.sdk.model.request.audience.TripFrequency30d
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Standard audience signals and device details on a decision request: the scenarios of
 * `src/main/kotlin/com/admoai/sdk/model/request/AudienceTargeting.feature`, one test per scenario,
 * named after it. The private functions hold the assertions; each scenario test composes the ones
 * that prove it.
 *
 * There is no Gherkin runner in this module, so `every scenario has a test` holds the binding
 * instead: a scenario added to the feature without a test here, or a test whose scenario was
 * renamed away, fails the build.
 */
class AudienceTargetingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        Admoai.resetForTesting()
        Admoai.initialize(SDKConfig(baseUrl = "https://test.admoai.com", networkClientEngine = null))
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
    }

    private fun builder() = DecisionRequestBuilder().addPlacement("home")

    private fun wire(b: DecisionRequestBuilder): String =
        json.encodeToString(Admoai.getInstance().prepareFinalDecisionRequest(b.build()))

    private fun segmentEntries(b: DecisionRequestBuilder) =
        b.build().targeting?.custom.orEmpty().filter { it.key == "publisher_segments" }

    // --- Device carrier / category ---

    private val tabletDevice = DeviceConfig(
        model = "Pixel Tablet",
        osName = "Android",
        osVersion = "14",
        timezone = "Europe/Madrid",
        deviceId = "device-abc",
        manufacturer = "Google",
        language = "es-ES",
        carrier = "Movistar",
        category = DeviceCategory.TABLET
    )

    private fun `device carrier and category match the golden`() {
        Admoai.getInstance().setDeviceConfig(tabletDevice)
        val expected = File("src/test/resources/fixtures/request-golden/device.carrier-category.json")
            .readText().trimEnd('\n')
        val actual = wire(builder())
        assertTrue(actual.contains("\"carrier\":\"Movistar\",\"category\":\"tablet\""))
        assertEquals(expected, actual)
    }

    private fun `disabled device collection sends no device even with carrier and category`() {
        Admoai.getInstance().setDeviceConfig(tabletDevice)
        val actual = wire(builder().disableDeviceCollection())
        assertFalse(actual.contains("\"device\""))
        assertFalse(actual.contains("Movistar"))
    }

    private fun `device without carrier or category omits both keys`() {
        Admoai.getInstance().setDeviceConfig(tabletDevice.copy(carrier = null, category = null))
        val actual = wire(builder())
        assertFalse(actual.contains("carrier"))
        assertFalse(actual.contains("category"))
    }

    // --- Publisher segments ---

    private fun `one code is still an array`() {
        val b = builder().setPublisherSegments(listOf("vip"))
        assertTrue(wire(b).contains("{\"key\":\"publisher_segments\",\"value\":[\"vip\"]}"))
    }

    private fun `duplicates are removed keeping first-seen order, case-sensitive`() {
        val b = builder().setPublisherSegments(listOf("a", "B", "a", "b_2"))
        assertTrue(wire(b).contains("\"value\":[\"a\",\"B\",\"b_2\"]"))
    }

    private fun `each invalid code is rejected without echoing the value`() {
        listOf("", " ", " b ", "a-b", "\u00e9", "a\n").forEach { bad ->
            val e = runCatching { builder().setPublisherSegments(listOf("ok", bad)) }.exceptionOrNull()
            assertTrue("expected IllegalArgumentException for '$bad'", e is IllegalArgumentException)
            assertTrue(e!!.message!!.contains("index 1"))
            if (bad.isNotBlank()) assertFalse(e.message!!.contains(bad))
        }
    }

    private fun `invalid setter does not partially apply over an existing value`() {
        val b = builder().setPublisherSegments(listOf("vip"))
        runCatching { b.setPublisherSegments(listOf("ok", "bad code")) }
            .also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
        assertEquals(listOf("[\"vip\"]"), segmentEntries(b).map { it.value.toString() })
    }

    private fun `invalid setter does not partially apply from no segments`() {
        val b = builder()
        runCatching { b.setPublisherSegments(listOf("ok", "bad code")) }
            .also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
        assertTrue(segmentEntries(b).isEmpty())
        assertNull(b.build().targeting)
    }

    private fun `empty list sends no entry and no targeting key`() {
        val b = builder().setPublisherSegments(emptyList())
        assertTrue(segmentEntries(b).isEmpty())
        assertNull(b.build().targeting)
        assertFalse(wire(b).contains("targeting"))
    }

    private fun `null removes a previous value`() {
        val b = builder().setPublisherSegments(listOf("vip")).setPublisherSegments(null)
        assertTrue(segmentEntries(b).isEmpty())
        assertNull(b.build().targeting)
    }

    private fun `clearing keeps other custom targets`() {
        val b = builder().addCustomTarget("tier", "gold")
            .setPublisherSegments(listOf("vip")).setPublisherSegments(emptyList())
        assertEquals(listOf("tier"), b.build().targeting?.custom?.map { it.key })
    }

    private fun `addCustomTarget then setPublisherSegments - segments win`() {
        val b = builder().addCustomTarget("publisher_segments", "x").setPublisherSegments(listOf("y"))
        assertEquals(1, segmentEntries(b).size)
        assertTrue(wire(b).contains("\"value\":[\"y\"]"))
    }

    private fun `setPublisherSegments then addCustomTarget - custom target wins`() {
        val b = builder().setPublisherSegments(listOf("y")).addCustomTarget("publisher_segments", "x")
        assertEquals(1, segmentEntries(b).size)
        assertTrue(wire(b).contains("{\"key\":\"publisher_segments\",\"value\":\"x\"}"))
    }

    private fun `repeated setPublisherSegments keeps a single entry`() {
        val b = builder().setPublisherSegments(listOf("a")).setPublisherSegments(listOf("b"))
        assertEquals(1, segmentEntries(b).size)
        assertTrue(wire(b).contains("\"value\":[\"b\"]"))
    }

    // --- Standard audience signals ---

    private fun allStandard() = builder()
        .setAgeGroup(AgeGroup.AGE_25_34)
        .setGender(Gender.FEMALE)
        .setAccountType(AccountType.BUSINESS)
        .setSubscriptionStatus(SubscriptionStatus.TRIAL)
        .setAccountTenure(AccountTenure.DAYS_91_365)
        .setTripFrequency30d(TripFrequency30d.ZERO)
        .setLastTripRecency(LastTripRecency.DAYS_8_30)
        .setPublisherSegments(listOf("vip", "commuter"))

    private val standardKeys = listOf(
        "age_group",
        "gender",
        "account_type",
        "subscription_status",
        "account_tenure",
        "trip_frequency_30d",
        "last_trip_recency"
    )

    private val nullers: List<Pair<String, (DecisionRequestBuilder) -> DecisionRequestBuilder>> = listOf(
        "age_group" to { b -> b.setAgeGroup(null) },
        "gender" to { b -> b.setGender(null) },
        "account_type" to { b -> b.setAccountType(null) },
        "subscription_status" to { b -> b.setSubscriptionStatus(null) },
        "account_tenure" to { b -> b.setAccountTenure(null) },
        "trip_frequency_30d" to { b -> b.setTripFrequency30d(null) },
        "last_trip_recency" to { b -> b.setLastTripRecency(null) }
    )

    private fun `all standard audience signals match the golden`() {
        val expected = File("src/test/resources/fixtures/request-golden/audience.all-standard.json")
            .readText().trimEnd('\n')
        val actual = wire(allStandard())
        assertTrue(actual.contains("{\"key\":\"trip_frequency_30d\",\"value\":\"0\"}"))
        assertEquals(expected, actual)
    }

    private fun `null removes only that key for each setter`() {
        nullers.forEach { (key, nuller) ->
            val keys = nuller(allStandard()).build().targeting?.custom.orEmpty().map { it.key }
            assertEquals(
                "removing $key",
                (standardKeys - key) + "publisher_segments",
                keys
            )
        }
    }

    private fun `setting all then nulling all leaves no targeting`() {
        val b = nullers.fold(allStandard().setPublisherSegments(null)) { acc, (_, nuller) -> nuller(acc) }
        assertNull(b.build().targeting)
        assertFalse(wire(b).contains("targeting"))
    }

    private fun `addCustomTarget then setGender - setter wins`() {
        val b = builder().addCustomTarget("gender", "male").setGender(Gender.FEMALE)
        assertEquals(listOf("gender"), b.build().targeting?.custom?.map { it.key })
        assertTrue(wire(b).contains("{\"key\":\"gender\",\"value\":\"female\"}"))
    }

    private fun `setGender then addCustomTarget - custom target wins`() {
        val b = builder().setGender(Gender.FEMALE).addCustomTarget("gender", "male")
        assertEquals(listOf("gender"), b.build().targeting?.custom?.map { it.key })
        assertTrue(wire(b).contains("{\"key\":\"gender\",\"value\":\"male\"}"))
    }

    // --- Binary compatibility with apps compiled against 1.8 ---

    private val s = String::class.java
    private val seven: Array<Class<*>> = Array(7) { s }
    private val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

    // Scenario: a 1.8.x caller builds a DeviceConfig with all seven fields.
    private fun `DeviceConfig 7-argument constructor still links`() {
        val ctor = DeviceConfig::class.java.getConstructor(*seven)
        val cfg = ctor.newInstance("Pixel 6", "Android", "13", "Europe/Madrid", "id", "Google", "es-ES")
        assertEquals(
            DeviceConfig("Pixel 6", "Android", "13", "Europe/Madrid", "id", "Google", "es-ES", null, null),
            cfg
        )
    }

    // Scenario: a 1.8.x Kotlin caller used named/default arguments: DeviceConfig(model = "x").
    private fun `DeviceConfig 1_8 default-argument constructor still links`() {
        val ctor = DeviceConfig::class.java.getConstructor(*seven, Int::class.javaPrimitiveType, marker)
        // Mask 0b1111110: every parameter except the first takes its default.
        val cfg = ctor.newInstance("Pixel 6", null, null, null, null, null, null, 0b1111110, null)
        assertEquals(DeviceConfig(model = "Pixel 6"), cfg)
    }

    // Scenario: a 1.8.x caller copies a DeviceConfig; carrier and category survive the copy.
    private fun `DeviceConfig 7-argument copy and copy default still link and keep the new fields`() {
        val original = DeviceConfig(model = "a", carrier = "Movistar", category = DeviceCategory.TABLET)

        val copy = DeviceConfig::class.java.getMethod("copy", *seven)
        val copied = copy.invoke(original, "b", null, null, null, null, null, null) as DeviceConfig
        assertEquals("b", copied.model)
        assertEquals("Movistar", copied.carrier)
        assertEquals(DeviceCategory.TABLET, copied.category)

        val copyDefault = DeviceConfig::class.java.getMethod(
            "copy\$default",
            DeviceConfig::class.java,
            *seven,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        // Mask 0b1111110: only model is passed.
        val viaDefault = copyDefault.invoke(null, original, "c", null, null, null, null, null, null, 0b1111110, null)
            as DeviceConfig
        assertEquals(original.copy(model = "c"), viaDefault)
    }

    // Scenario: a 1.8.x caller builds a Device request model directly.
    private fun `Device 7-argument constructor, default constructor and copy still link`() {
        val ctor = Device::class.java.getConstructor(*seven)
        val device = ctor.newInstance("Android", "13", "Pixel 6", "Google", "id", "UTC", "en") as Device
        assertNull(device.carrier)
        assertNull(device.category)

        val defaultCtor = Device::class.java.getConstructor(*seven, Int::class.javaPrimitiveType, marker)
        assertEquals(
            Device(os = "Android"),
            defaultCtor.newInstance("Android", null, null, null, null, null, null, 0b1111110, null)
        )

        val withNew = Device(os = "Android", carrier = "Movistar", category = "tablet")
        val copied = Device::class.java.getMethod("copy", *seven)
            .invoke(withNew, "iOS", null, null, null, null, null, null) as Device
        assertEquals("iOS", copied.os)
        assertEquals("Movistar", copied.carrier)
        assertEquals("tablet", copied.category)

        val copyDefault = Device::class.java.getMethod(
            "copy\$default",
            Device::class.java,
            *seven,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        assertEquals(
            withNew.copy(os = "iOS"),
            copyDefault.invoke(null, withNew, "iOS", null, null, null, null, null, null, 0b1111110, null)
        )
    }

    // --- Baseline request goldens ---

    private fun baselineFullBuilder() = DecisionRequestBuilder()
        .addPlacement("home")
        .addGeoTarget(123)
        .addLocationTarget(40.4168, -3.7038)
        .addCustomTarget("tier", "gold")
        .addCustomTarget("age", 42)
        .addCustomTarget("subscriber", true)
        .setUserId("user-1")
        .setUserConsent(true)

    private val baselineDevice = DeviceConfig(
        model = "Pixel 6",
        osName = "Android",
        osVersion = "13",
        timezone = "Europe/Madrid",
        deviceId = "device-abc",
        manufacturer = "Google",
        language = "es-ES"
    )

    private fun assertBaseline(name: String, b: DecisionRequestBuilder, device: DeviceConfig?) {
        Admoai.resetForTesting()
        Admoai.initialize(SDKConfig(baseUrl = "https://test.admoai.com", networkClientEngine = null))
        if (device != null) Admoai.getInstance().setDeviceConfig(device)
        val expected = File("src/test/resources/fixtures/request-golden/$name").readText().trimEnd('\n')
        assertEquals(expected, wire(b))
    }

    // --- Scenarios ---

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/model/request/AudienceTargeting.feature")
        val titles = feature.readLines()
            .map { it.trim() }
            .filter { it.startsWith("Scenario:") }
            .map { it.removePrefix("Scenario:").trim() }
            .toSet()
        val tests = javaClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .map { it.name }
            .toSet()

        assertTrue("the feature file has scenarios", titles.isNotEmpty())
        assertEquals(emptySet<String>(), titles - tests)
    }

    @Test
    fun `a request that uses no audience helper is unchanged on the wire`() {
        assertBaseline("baseline.full.json", baselineFullBuilder(), baselineDevice)
        assertBaseline("baseline.device-disabled.json", baselineFullBuilder().disableDeviceCollection(), baselineDevice)
        assertBaseline("baseline.minimal.json", DecisionRequestBuilder().addPlacement("home"), null)
    }

    @Test
    fun `each Standard audience helper sends its key with the catalog value`() {
        `all standard audience signals match the golden`()
    }

    @Test
    fun `trip frequency zero is sent as a string, never a number`() {
        val b = builder().setTripFrequency30d(TripFrequency30d.ZERO)
        assertTrue(wire(b).contains("{\"key\":\"trip_frequency_30d\",\"value\":\"0\"}"))
    }

    @Test
    fun `setting a helper to null removes only that key`() {
        `null removes only that key for each setter`()
        `setting all then nulling all leaves no targeting`()
        `null removes a previous value`()
    }

    @Test
    fun `the last write wins between a typed helper and a raw custom target`() {
        `addCustomTarget then setGender - setter wins`()
        `setGender then addCustomTarget - custom target wins`()
        `addCustomTarget then setPublisherSegments - segments win`()
        `setPublisherSegments then addCustomTarget - custom target wins`()
        `repeated setPublisherSegments keeps a single entry`()
    }

    @Test
    fun `publisher segments are always sent as a list, even for one code`() {
        `one code is still an array`()
    }

    @Test
    fun `repeated publisher segment codes are sent once, in first-seen order`() {
        `duplicates are removed keeping first-seen order, case-sensitive`()
    }

    @Test
    fun `an empty publisher segment list sends no segments at all`() {
        `empty list sends no entry and no targeting key`()
        `clearing keeps other custom targets`()
    }

    @Test
    fun `an invalid publisher segment code rejects the whole call and changes nothing`() {
        `each invalid code is rejected without echoing the value`()
        `invalid setter does not partially apply over an existing value`()
        `invalid setter does not partially apply from no segments`()
    }

    @Test
    fun `device carrier and category are sent when the app provides them`() {
        `device carrier and category match the golden`()
        `device without carrier or category omits both keys`()
    }

    @Test
    fun `turning off device collection drops carrier and category with the rest of the device`() {
        `disabled device collection sends no device even with carrier and category`()
    }

    @Test
    fun `apps compiled against the previous version keep working without recompiling`() {
        `DeviceConfig 7-argument constructor still links`()
        `DeviceConfig 1_8 default-argument constructor still links`()
        `DeviceConfig 7-argument copy and copy default still link and keep the new fields`()
        `Device 7-argument constructor, default constructor and copy still link`()
    }

    @Test
    fun `publisherSegmentsValue validates first, then returns distinct codes or null`() {
        assertNull(AudienceTargeting.publisherSegmentsValue(null))
        assertNull(AudienceTargeting.publisherSegmentsValue(emptyList()))
        assertEquals(
            JsonArray(listOf(JsonPrimitive("vip"))),
            AudienceTargeting.publisherSegmentsValue(listOf("vip"))
        )
        assertEquals(
            JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("B"), JsonPrimitive("b_2"))),
            AudienceTargeting.publisherSegmentsValue(listOf("a", "B", "a", "b_2"))
        )
        listOf("", " ", " b ", "a-b", "\u00e9", "a\n").forEach { bad ->
            val e = runCatching { AudienceTargeting.publisherSegmentsValue(listOf("ok", bad)) }.exceptionOrNull()
            assertTrue("expected IllegalArgumentException for '$bad'", e is IllegalArgumentException)
            assertTrue(e!!.message!!.contains("index 1"))
            if (bad.isNotBlank()) assertFalse(e.message!!.contains(bad))
        }
    }
}
