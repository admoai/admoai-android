package com.admoai.sdk.model.request

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Byte-for-byte request goldens. A request that uses none of the audience-signal APIs must
 * serialize exactly as it did before those APIs existed; the baseline.* files were captured from
 * the code before they were added and must never be regenerated to make a change pass.
 */
class RequestGoldenTest {

    // Same flags as the production encoder in AdMoaiApiServiceImpl (prettyPrint off).
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

    private fun fullBuilder() = DecisionRequestBuilder()
        .addPlacement("home")
        .addGeoTarget(123)
        .addLocationTarget(40.4168, -3.7038)
        .addCustomTarget("tier", "gold")
        .addCustomTarget("age", 42)
        .addCustomTarget("subscriber", true)
        .setUserId("user-1")
        .setUserConsent(true)

    private val explicitDevice = DeviceConfig(
        model = "Pixel 6",
        osName = "Android",
        osVersion = "13",
        timezone = "Europe/Madrid",
        deviceId = "device-abc",
        manufacturer = "Google",
        language = "es-ES"
    )

    private fun wire(builder: DecisionRequestBuilder, device: DeviceConfig? = explicitDevice): String {
        val admoai = Admoai.getInstance()
        if (device != null) admoai.setDeviceConfig(device)
        return json.encodeToString(admoai.prepareFinalDecisionRequest(builder.build()))
    }

    private fun golden(name: String): String =
        File("src/test/resources/fixtures/request-golden/$name").readText().trimEnd('\n')

    private fun assertGolden(name: String, actual: String) {
        assertEquals(golden(name), actual)
    }

    @Test
    fun `baseline full request is unchanged`() {
        assertGolden("baseline.full.json", wire(fullBuilder()))
    }

    @Test
    fun `baseline request with device collection disabled is unchanged`() {
        assertGolden("baseline.device-disabled.json", wire(fullBuilder().disableDeviceCollection()))
    }

    @Test
    fun `baseline minimal request is unchanged`() {
        assertGolden("baseline.minimal.json", wire(DecisionRequestBuilder().addPlacement("home"), device = null))
    }
}
