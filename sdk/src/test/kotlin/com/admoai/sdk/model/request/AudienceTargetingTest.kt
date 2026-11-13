package com.admoai.sdk.model.request

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    @Test
    fun `device carrier and category match the golden`() {
        Admoai.getInstance().setDeviceConfig(tabletDevice)
        val expected = File("src/test/resources/fixtures/request-golden/device.carrier-category.json")
            .readText().trimEnd('\n')
        val actual = wire(builder())
        assertTrue(actual.contains("\"carrier\":\"Movistar\",\"category\":\"tablet\""))
        assertEquals(expected, actual)
    }

    @Test
    fun `disabled device collection sends no device even with carrier and category`() {
        Admoai.getInstance().setDeviceConfig(tabletDevice)
        val actual = wire(builder().disableDeviceCollection())
        assertFalse(actual.contains("\"device\""))
        assertFalse(actual.contains("Movistar"))
    }

    @Test
    fun `device without carrier or category omits both keys`() {
        Admoai.getInstance().setDeviceConfig(tabletDevice.copy(carrier = null, category = null))
        val actual = wire(builder())
        assertFalse(actual.contains("carrier"))
        assertFalse(actual.contains("category"))
    }

    // --- Publisher segments ---

    @Test
    fun `one code is still an array`() {
        val b = builder().setPublisherSegments(listOf("vip"))
        assertTrue(wire(b).contains("{\"key\":\"publisher_segments\",\"value\":[\"vip\"]}"))
    }

    @Test
    fun `duplicates are removed keeping first-seen order, case-sensitive`() {
        val b = builder().setPublisherSegments(listOf("a", "B", "a", "b_2"))
        assertTrue(wire(b).contains("\"value\":[\"a\",\"B\",\"b_2\"]"))
    }

    @Test
    fun `each invalid code is rejected without echoing the value`() {
        listOf("", " ", " b ", "a-b", "\u00e9", "a\n").forEach { bad ->
            val e = runCatching { builder().setPublisherSegments(listOf("ok", bad)) }.exceptionOrNull()
            assertTrue("expected IllegalArgumentException for '$bad'", e is IllegalArgumentException)
            assertTrue(e!!.message!!.contains("index 1"))
            if (bad.isNotBlank()) assertFalse(e.message!!.contains(bad))
        }
    }

    @Test
    fun `invalid setter does not partially apply over an existing value`() {
        val b = builder().setPublisherSegments(listOf("vip"))
        runCatching { b.setPublisherSegments(listOf("ok", "bad code")) }
            .also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
        assertEquals(listOf("[\"vip\"]"), segmentEntries(b).map { it.value.toString() })
    }

    @Test
    fun `invalid setter does not partially apply from no segments`() {
        val b = builder()
        runCatching { b.setPublisherSegments(listOf("ok", "bad code")) }
            .also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
        assertTrue(segmentEntries(b).isEmpty())
        assertNull(b.build().targeting)
    }

    @Test
    fun `empty list sends no entry and no targeting key`() {
        val b = builder().setPublisherSegments(emptyList())
        assertTrue(segmentEntries(b).isEmpty())
        assertNull(b.build().targeting)
        assertFalse(wire(b).contains("targeting"))
    }

    @Test
    fun `null removes a previous value`() {
        val b = builder().setPublisherSegments(listOf("vip")).setPublisherSegments(null)
        assertTrue(segmentEntries(b).isEmpty())
        assertNull(b.build().targeting)
    }

    @Test
    fun `clearing keeps other custom targets`() {
        val b = builder().addCustomTarget("tier", "gold")
            .setPublisherSegments(listOf("vip")).setPublisherSegments(emptyList())
        assertEquals(listOf("tier"), b.build().targeting?.custom?.map { it.key })
    }

    @Test
    fun `addCustomTarget then setPublisherSegments - segments win`() {
        val b = builder().addCustomTarget("publisher_segments", "x").setPublisherSegments(listOf("y"))
        assertEquals(1, segmentEntries(b).size)
        assertTrue(wire(b).contains("\"value\":[\"y\"]"))
    }

    @Test
    fun `setPublisherSegments then addCustomTarget - custom target wins`() {
        val b = builder().setPublisherSegments(listOf("y")).addCustomTarget("publisher_segments", "x")
        assertEquals(1, segmentEntries(b).size)
        assertTrue(wire(b).contains("{\"key\":\"publisher_segments\",\"value\":\"x\"}"))
    }

    @Test
    fun `repeated setPublisherSegments keeps a single entry`() {
        val b = builder().setPublisherSegments(listOf("a")).setPublisherSegments(listOf("b"))
        assertEquals(1, segmentEntries(b).size)
        assertTrue(wire(b).contains("\"value\":[\"b\"]"))
    }
}
