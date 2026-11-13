package com.admoai.sdk.config

import android.content.Context
import android.hardware.display.DisplayManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import com.admoai.sdk.Admoai
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.request.DeviceCategory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Device carrier and category collection: the scenarios of
 * `src/main/kotlin/com/admoai/sdk/config/DeviceSignals.feature`, one test per scenario, named
 * after it. `every scenario has a test` holds the binding, as in AudienceTargetingTest.
 */
@Suppress("DEPRECATION")
class DeviceSignalsTest {

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
        unmockkAll()
    }

    private fun deviceOnWire(config: DeviceConfig, collect: Boolean = true): JsonObject? {
        Admoai.getInstance().setDeviceConfig(config)
        val builder = DecisionRequestBuilder().addPlacement("home")
        if (!collect) builder.disableDeviceCollection()
        val request = Admoai.getInstance().prepareFinalDecisionRequest(builder.build())
        return json.parseToJsonElement(json.encodeToString(request)).jsonObject["device"]?.jsonObject
    }

    /** A context whose default display reports the given real metrics. */
    private fun contextWithDisplay(widthPx: Int, heightPx: Int, density: Float): Context {
        mockkConstructor(DisplayMetrics::class)
        val display = mockk<Display>()
        every { display.getRealMetrics(any()) } answers {
            val metrics = firstArg<DisplayMetrics>()
            metrics.widthPixels = widthPx
            metrics.heightPixels = heightPx
            metrics.density = density
        }
        return contextWithDisplayManager(display)
    }

    private fun contextWithDisplayManager(display: Display?): Context {
        val manager = mockk<DisplayManager>()
        every { manager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSystemService(DisplayManager::class.java) } returns manager
        return context
    }

    private fun contextWithTelephony(telephony: TelephonyManager?): Context {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSystemService(TelephonyManager::class.java) } returns telephony
        return context
    }

    private fun telephonyNamed(name: String?): TelephonyManager {
        val telephony = mockk<TelephonyManager>()
        every { telephony.networkOperatorName } returns name
        return telephony
    }

    private fun readCarrierWith(operator: String?): String? {
        mockkStatic(SubscriptionManager::class)
        every { SubscriptionManager.getDefaultDataSubscriptionId() } returns 7
        val telephony = mockk<TelephonyManager>()
        every { telephony.createForSubscriptionId(7) } returns telephonyNamed(operator)
        return DeviceSignals.readCarrier(contextWithTelephony(telephony))
    }

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/config/DeviceSignals.feature")
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
    fun `a phone reports the phone category`() {
        assertEquals(DeviceCategory.PHONE, DeviceSignals.categoryFor(1199, 1920, 2.0f))
        assertEquals(DeviceCategory.PHONE, DeviceSignals.categoryFor(1080, 2400, 2.625f))
        assertEquals(DeviceCategory.PHONE, DeviceSignals.readCategory(contextWithDisplay(1080, 2400, 2.625f)))
    }

    @Test
    fun `a display 600 dp wide on its shortest side reports tablet`() {
        assertEquals(DeviceCategory.TABLET, DeviceSignals.categoryFor(1200, 1920, 2.0f))
        assertEquals(DeviceCategory.TABLET, DeviceSignals.categoryFor(1600, 2560, 2.0f))
        assertEquals(DeviceCategory.TABLET, DeviceSignals.readCategory(contextWithDisplay(1600, 2560, 2.0f)))
    }

    @Test
    fun `a display 599 dp wide on its shortest side reports phone`() {
        // 1199 / 2.0 = 599.5 dp
        assertEquals(DeviceCategory.PHONE, DeviceSignals.categoryFor(1199, 1920, 2.0f))
        assertEquals(DeviceCategory.PHONE, DeviceSignals.categoryFor(599, 1000, 1.0f))
    }

    @Test
    fun `rotation does not change the category`() {
        assertEquals(DeviceSignals.categoryFor(1200, 1920, 2.0f), DeviceSignals.categoryFor(1920, 1200, 2.0f))
        assertEquals(DeviceCategory.TABLET, DeviceSignals.categoryFor(1920, 1200, 2.0f))
        assertEquals(DeviceSignals.categoryFor(1199, 1920, 2.0f), DeviceSignals.categoryFor(1920, 1199, 2.0f))
    }

    @Test
    fun `an unreadable display reports other`() {
        assertEquals(DeviceCategory.OTHER, DeviceSignals.categoryFor(1200, 1920, 0f))
        assertEquals(DeviceCategory.OTHER, DeviceSignals.categoryFor(0, 1920, 2.0f))
        assertEquals(DeviceCategory.OTHER, DeviceSignals.categoryFor(1200, 0, 2.0f))
        assertEquals(DeviceCategory.OTHER, DeviceSignals.readCategory(contextWithDisplayManager(null)))

        mockkConstructor(DisplayMetrics::class)
        val throwing = mockk<Display>()
        every { throwing.getRealMetrics(any()) } throws IllegalStateException("boom")
        assertEquals(DeviceCategory.OTHER, DeviceSignals.readCategory(contextWithDisplayManager(throwing)))
    }

    @Test
    fun `the carrier is the operator of the default data subscription`() {
        assertEquals("Movistar", readCarrierWith("Movistar"))
    }

    @Test
    fun `placeholder carrier names are not sent`() {
        listOf(null, "", "   ", "--", "unknown", "UNKNOWN", "WiFi", "wi-fi", " Wi-Fi ").forEach {
            assertNull("[$it]", DeviceSignals.normalizeCarrier(it))
        }
        assertNull(readCarrierWith("unknown"))

        // No default data subscription: the plain TelephonyManager answers.
        mockkStatic(SubscriptionManager::class)
        every { SubscriptionManager.getDefaultDataSubscriptionId() } returns SubscriptionManager.INVALID_SUBSCRIPTION_ID
        assertEquals("Claro", DeviceSignals.readCarrier(contextWithTelephony(telephonyNamed("Claro"))))
        assertNull(DeviceSignals.readCarrier(contextWithTelephony(null)))

        val throwing = mockk<TelephonyManager>()
        every { throwing.networkOperatorName } throws SecurityException("no")
        assertNull(DeviceSignals.readCarrier(contextWithTelephony(throwing)))
    }

    @Test
    fun `a carrier name is trimmed and keeps its case`() {
        assertEquals("Movistar", DeviceSignals.normalizeCarrier(" Movistar "))
        assertEquals("T-Mobile", DeviceSignals.normalizeCarrier("T-Mobile"))
        assertEquals("T-Mobile", readCarrierWith("  T-Mobile "))
    }

    @Test
    fun `a carrier the app sets by hand is cleaned the same way`() {
        assertFalse(deviceOnWire(DeviceConfig(model = "x", carrier = " wifi "))!!.containsKey("carrier"))
        assertEquals(
            "Claro",
            deviceOnWire(DeviceConfig(model = "x", carrier = " Claro "))!!["carrier"]!!.toString().trim('"')
        )
    }

    @Test
    fun `an app that configures the device without a context sends no carrier or category`() {
        val config = DeviceConfig.systemDefault()
        assertNull(config.carrier)
        assertNull(config.category)
        assertNull(DeviceConfig.systemDefault("id").carrier)
        val device = deviceOnWire(config)!!
        assertFalse(device.containsKey("carrier"))
        assertFalse(device.containsKey("category"))
    }

    @Test
    fun `turning off device collection drops carrier and category`() {
        val config = DeviceConfig(model = "x", carrier = "Movistar", category = DeviceCategory.TABLET)
        assertNull(deviceOnWire(config, collect = false))
    }
}
