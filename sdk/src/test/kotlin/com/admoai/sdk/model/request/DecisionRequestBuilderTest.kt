package com.admoai.sdk.model.request

import com.admoai.sdk.exception.AdMoaiConfigurationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionRequestBuilderTest {

    @Test
    fun `build with minimal placement`() {
        val request = DecisionRequestBuilder()
            .addPlacement("test-placement")
            .build()

        assertEquals(1, request.placements.size)
        assertEquals("test-placement", request.placements[0].key)
        assertNull(request.placements[0].count)
        assertNull(request.placements[0].format)
        assertNull(request.user)
        assertNull(request.targeting)
        assertTrue(request.collectAppData) // Default
        assertTrue(request.collectDeviceData) // Default
    }

    @Test(expected = AdMoaiConfigurationException::class)
    fun `build without placement throws exception`() {
        DecisionRequestBuilder().build()
    }

    @Test
    fun `addPlacement with all parameters`() {
        val request = DecisionRequestBuilder()
            .addPlacement(
                key = "full-placement",
                count = 3,
                format = PlacementFormat.NATIVE,
                advertiserId = "adv-123",
                templateId = "tpl-456"
            )
            .build()

        assertEquals(1, request.placements.size)
        val placement = request.placements[0]
        assertEquals("full-placement", placement.key)
        assertEquals(3, placement.count)
        assertEquals(PlacementFormat.NATIVE, placement.format)
        assertEquals("adv-123", placement.advertiserId)
        assertEquals("tpl-456", placement.templateId)
    }

    @Test
    fun `add multiple placements`() {
        val placement1 = Placement("p1")
        val request = DecisionRequestBuilder()
            .addPlacement("p0")
            .addPlacement(placement1)
            .addPlacement(key = "p2", count = 2)
            .build()

        assertEquals(3, request.placements.size)
        assertEquals("p0", request.placements[0].key)
        assertEquals("p1", request.placements[1].key)
        assertEquals("p2", request.placements[2].key)
        assertEquals(2, request.placements[2].count)
    }

    @Test
    fun `setPlacements replaces existing`() {
        val initialPlacements = listOf(Placement("initial1"), Placement("initial2"))
        val newPlacements = listOf(Placement("new1"))

        val request = DecisionRequestBuilder()
            .setPlacements(initialPlacements) // Set initial
            .addPlacement("extra-should-be-replaced") // Add one more
            .setPlacements(newPlacements) // Now replace all
            .build()

        assertEquals(1, request.placements.size)
        assertEquals("new1", request.placements[0].key)
    }


    @Test
    fun `set user properties`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setUserId("user-xyz")
            .setUserIp("192.168.1.1")
            .setUserTimezone("Europe/London")
            .setUserConsent(true)
            .build()

        assertNotNull(request.user)
        assertEquals("user-xyz", request.user?.id)
        assertEquals("192.168.1.1", request.user?.ip)
        assertEquals("Europe/London", request.user?.timezone)
        assertNotNull(request.user?.consent)
        assertEquals(true, request.user?.consent?.gdpr)
    }

    @Test
    fun `set user consent object`() {
        val consent = Consent(gdpr = false)
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setUserConsent(consent)
            .build()
        assertNotNull(request.user?.consent)
        assertEquals(false, request.user?.consent?.gdpr)
    }

    @Test
    fun `targeting remains null if no targeting info added`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .build()
        assertNull(request.targeting)
    }

    @Test
    fun `user remains null if no user info added`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .build()
        assertNull(request.user)
    }

    @Test
    fun `add geo target`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addGeoTarget(123)
            .addGeoTarget(456)
            .addGeoTarget(123) // duplicate, should be distinct
            .build()

        assertNotNull(request.targeting)
        assertNotNull(request.targeting?.geo)
        assertEquals(2, request.targeting?.geo?.size)
        assertTrue(request.targeting?.geo?.contains(123) == true)
        assertTrue(request.targeting?.geo?.contains(456) == true)
    }

    @Test
    fun `set geo targets`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addGeoTarget(111) // Should be replaced
            .setGeoTargets(listOf(789, 101))
            .build()

        assertNotNull(request.targeting?.geo)
        assertEquals(2, request.targeting?.geo?.size)
        assertTrue(request.targeting?.geo?.contains(789) == true)
    }

    @Test
    fun `add location target`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addLocationTarget(10.0, 20.0)
            .build()

        assertNotNull(request.targeting?.location)
        assertEquals(1, request.targeting?.location?.size)
        assertEquals(10.0, request.targeting?.location?.get(0)?.latitude)
        assertEquals(20.0, request.targeting?.location?.get(0)?.longitude)
    }

    @Test
    fun `set location targets`() {
        val locations = listOf(LocationTargetingInfo(1.0, 2.0), LocationTargetingInfo(3.0, 4.0))
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addLocationTarget(9.0,9.0) // should be replaced
            .setLocationTargets(locations)
            .build()

        assertEquals(locations, request.targeting?.location)
    }

    @Test
    fun `add custom target string`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addCustomTarget("key1", "value1")
            .build()

        assertNotNull(request.targeting?.custom)
        assertEquals(1, request.targeting?.custom?.size)
        assertEquals("key1", request.targeting?.custom?.get(0)?.key)
        assertEquals(JsonPrimitive("value1"), request.targeting?.custom?.get(0)?.value)
    }

    @Test
    fun `add custom target number`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addCustomTarget("keyNum", 123.45)
            .build()
        val custom = request.targeting?.custom?.find { it.key == "keyNum" }
        assertNotNull(custom)
        assertEquals(JsonPrimitive(123.45), custom?.value)
    }

    @Test
    fun `add custom target boolean`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addCustomTarget("keyBool", true)
            .build()
        val custom = request.targeting?.custom?.find { it.key == "keyBool" }
        assertNotNull(custom)
        assertEquals(JsonPrimitive(true), custom?.value)
    }
     @Test
    fun `add custom target JsonElement null`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addCustomTarget("keyNull", JsonNull)
            .build()
        val custom = request.targeting?.custom?.find { it.key == "keyNull" }
        assertNotNull(custom)
        assertEquals(JsonNull, custom?.value)
    }


    @Test
    fun `add custom target updates existing key`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addCustomTarget("key1", "initialValue")
            .addCustomTarget("key1", "updatedValue")
            .addCustomTarget("key2", "anotherValue")
            .build()

        assertEquals(2, request.targeting?.custom?.size)
        val custom1 = request.targeting?.custom?.find { it.key == "key1" }
        assertEquals(JsonPrimitive("updatedValue"), custom1?.value)
    }

    @Test
    fun `set custom targets`() {
        val customTargets = listOf(CustomTargetingInfo("sKey", JsonPrimitive("sValue")))
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addCustomTarget("old", "oldVal") // Should be replaced
            .setCustomTargets(customTargets)
            .build()

        assertEquals(customTargets, request.targeting?.custom)
    }

    @Test
    fun `disable app data collection`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .disableAppCollection()
            .build()
        assertFalse(request.collectAppData)
        assertTrue(request.collectDeviceData) // Should remain default true
    }

    @Test
    fun `disable device data collection`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .disableDeviceCollection()
            .build()
        assertTrue(request.collectAppData) // Should remain default true
        assertFalse(request.collectDeviceData)
    }

    @Test
    fun `disable both app and device data collection`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .disableAppCollection()
            .disableDeviceCollection()
            .build()
        assertFalse(request.collectAppData)
        assertFalse(request.collectDeviceData)
    }

    @Test
    fun `build includes targeting if only geo is set`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addGeoTarget(1)
            .build()
        assertNotNull(request.targeting)
        assertNotNull(request.targeting?.geo)
        assertNull(request.targeting?.location)
        assertNull(request.targeting?.custom)
    }

    @Test
    fun `build includes targeting if only location is set`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addLocationTarget(1.0, 1.0)
            .build()
        assertNotNull(request.targeting)
        assertNull(request.targeting?.geo)
        assertNotNull(request.targeting?.location)
        assertNull(request.targeting?.custom)
    }

    @Test
    fun `build includes targeting if only custom is set`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addCustomTarget("key", "value")
            .build()
        assertNotNull(request.targeting)
        assertNull(request.targeting?.geo)
        assertNull(request.targeting?.location)
        assertNotNull(request.targeting?.custom)
    }

    @Test
    fun `build includes user if only id is set`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setUserId("testId")
            .build()
        assertNotNull(request.user)
        assertEquals("testId", request.user?.id)
        assertNull(request.user?.ip)
        assertNull(request.user?.timezone)
        assertNull(request.user?.consent)
    }
    
    @Test
    fun `build includes user if only ip is set`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setUserIp("1.2.3.4")
            .build()
        assertNotNull(request.user)
        assertNull(request.user?.id)
        assertEquals("1.2.3.4", request.user?.ip)
        assertNull(request.user?.timezone)
        assertNull(request.user?.consent)
    }

    @Test
    fun `build includes user if only timezone is set`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setUserTimezone("America/New_York")
            .build()
        assertNotNull(request.user)
        assertNull(request.user?.id)
        assertNull(request.user?.ip)
        assertEquals("America/New_York", request.user?.timezone)
        assertNull(request.user?.consent)
    }

    @Test
    fun `build includes user if only consent is set`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setUserConsent(true)
            .build()
        assertNotNull(request.user)
        assertNull(request.user?.id)
        assertNull(request.user?.ip)
        assertNull(request.user?.timezone)
        assertNotNull(request.user?.consent)
        assertTrue(request.user?.consent?.gdpr == true)
    }
}
