package com.admoai.sdk.config

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import io.mockk.verify
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
        DeviceSignals.resetForTests()
        Admoai.resetForTesting()
        Admoai.initialize(SDKConfig(baseUrl = "https://test.admoai.com", networkClientEngine = null))
    }

    @After
    fun tearDown() {
        DeviceSignals.resetForTests()
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
    private fun contextWithDisplay(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        features: Set<String> = emptySet(),
        uiModeType: Int? = null
    ): Context {
        mockkConstructor(DisplayMetrics::class)
        val display = mockk<Display>()
        every { display.getRealMetrics(any()) } answers {
            val metrics = firstArg<DisplayMetrics>()
            metrics.widthPixels = widthPx
            metrics.heightPixels = heightPx
            metrics.density = density
        }
        return contextWithDisplayManager(display, features, uiModeType)
    }

    private fun packageManagerWith(features: Set<String>, throwing: Boolean = false): PackageManager {
        val pm = mockk<PackageManager>()
        if (throwing) {
            every { pm.hasSystemFeature(any()) } throws RuntimeException("boom")
        } else {
            every { pm.hasSystemFeature(any()) } answers { firstArg<String>() in features }
        }
        return pm
    }

    private fun contextWithDisplayManager(
        display: Display?,
        features: Set<String> = emptySet(),
        uiModeType: Int? = null
    ): Context {
        val manager = mockk<DisplayManager>()
        every { manager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSystemService(DisplayManager::class.java) } returns manager
        every { context.packageManager } returns packageManagerWith(features)
        val uiMode = mockk<UiModeManager>()
        every { uiMode.currentModeType } returns (uiModeType ?: Configuration.UI_MODE_TYPE_NORMAL)
        every { context.getSystemService(UiModeManager::class.java) } returns uiMode
        return context
    }

    private fun contextWithTelephony(
        telephony: TelephonyManager?,
        features: Set<String> = setOf(PackageManager.FEATURE_TELEPHONY)
    ): Context {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.packageManager } returns packageManagerWith(features)
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
    fun `a tv, watch, car or desktop device reports other whatever its screen size`() {
        listOf(
            PackageManager.FEATURE_LEANBACK,
            PackageManager.FEATURE_WATCH,
            PackageManager.FEATURE_AUTOMOTIVE,
            PackageManager.FEATURE_PC
        ).forEach {
            assertTrue(it, DeviceSignals.isNonHandheld { f -> f == it })
            assertEquals(
                it,
                DeviceCategory.OTHER,
                DeviceSignals.readCategory(contextWithDisplay(1600, 2560, 2.0f, setOf(it)))
            )
        }
        assertFalse(DeviceSignals.isNonHandheld { false })
        listOf(Configuration.UI_MODE_TYPE_TELEVISION, Configuration.UI_MODE_TYPE_WATCH).forEach {
            assertEquals(
                it.toString(),
                DeviceCategory.OTHER,
                DeviceSignals.readCategory(contextWithDisplay(1600, 2560, 2.0f, uiModeType = it))
            )
        }
        assertEquals(DeviceCategory.TABLET, DeviceSignals.readCategory(contextWithDisplay(1200, 1920, 2.0f)))

        // A failing feature check does not decide: the display rule applies.
        mockkConstructor(DisplayMetrics::class)
        val display = mockk<Display>()
        every { display.getRealMetrics(any()) } answers {
            val metrics = firstArg<DisplayMetrics>()
            metrics.widthPixels = 1200
            metrics.heightPixels = 1920
            metrics.density = 2.0f
        }
        val context = contextWithDisplayManager(display)
        every { context.packageManager } returns packageManagerWith(emptySet(), throwing = true)
        assertEquals(DeviceCategory.TABLET, DeviceSignals.readCategory(context))
    }

    @Test
    fun `a phone in car mode still reports phone`() {
        val car = contextWithDisplay(1080, 2400, 2.625f, uiModeType = Configuration.UI_MODE_TYPE_CAR)
        assertEquals(DeviceCategory.PHONE, DeviceSignals.readCategory(car))
        val desk = contextWithDisplay(1080, 2400, 2.625f, uiModeType = Configuration.UI_MODE_TYPE_DESK)
        assertEquals(DeviceCategory.PHONE, DeviceSignals.readCategory(desk))
    }

    @Test
    fun `an unreadable display sends no category`() {
        assertNull(DeviceSignals.categoryFor(1200, 1920, 0f))
        assertNull(DeviceSignals.categoryFor(0, 1920, 2.0f))
        assertNull(DeviceSignals.categoryFor(1200, 0, 2.0f))
        assertNull(DeviceSignals.readCategory(contextWithDisplayManager(null)))

        mockkConstructor(DisplayMetrics::class)
        val throwing = mockk<Display>()
        every { throwing.getRealMetrics(any()) } throws IllegalStateException("boom")
        assertNull(DeviceSignals.readCategory(contextWithDisplayManager(throwing)))
    }

    @Test
    fun `the carrier is the operator of the default data subscription`() {
        assertEquals("Movistar", readCarrierWith("Movistar"))
    }

    @Test
    fun `a device without telephony sends no carrier`() {
        mockkStatic(SubscriptionManager::class)
        every { SubscriptionManager.getDefaultDataSubscriptionId() } returns 7
        val telephony = mockk<TelephonyManager>()
        val context = contextWithTelephony(telephony, features = emptySet())
        assertNull(DeviceSignals.readCarrier(context))
        verify(exactly = 0) { context.getSystemService(TelephonyManager::class.java) }
        verify(exactly = 0) { telephony.createForSubscriptionId(any()) }
        verify(exactly = 0) { telephony.networkOperatorName }
    }

    @Test
    fun `no default data subscription sends no carrier`() {
        mockkStatic(SubscriptionManager::class)
        every { SubscriptionManager.getDefaultDataSubscriptionId() } returns SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val telephony = telephonyNamed("Claro")
        assertNull(DeviceSignals.readCarrier(contextWithTelephony(telephony)))
        verify(exactly = 0) { telephony.networkOperatorName }
        verify(exactly = 0) { telephony.createForSubscriptionId(any()) }
    }

    @Test
    fun `placeholder carrier names are not sent`() {
        listOf(null, "", "   ", "--", "unknown", "UNKNOWN", "WiFi", "wi-fi", " Wi-Fi ").forEach {
            assertNull("[$it]", DeviceSignals.normalizeCarrier(it))
        }
        assertNull(readCarrierWith("unknown"))

        mockkStatic(SubscriptionManager::class)
        every { SubscriptionManager.getDefaultDataSubscriptionId() } returns 7
        assertNull(DeviceSignals.readCarrier(contextWithTelephony(null)))

        val throwing = mockk<TelephonyManager>()
        every { throwing.createForSubscriptionId(7) } throws SecurityException("no")
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

    private fun appContextMock(): Context {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        return context
    }

    private fun injectReaders(carrier: () -> String?, category: () -> DeviceCategory?, calls: IntArray) {
        DeviceSignals.carrierReader = {
            calls[0]++
            carrier()
        }
        DeviceSignals.categoryReader = {
            calls[1]++
            category()
        }
    }

    private fun JsonObject?.text(key: String) = this?.get(key)?.toString()?.trim('"')

    @Test
    fun `an app that configures the device without a context sends no carrier or category`() {
        // An earlier configuration registered a context; a context-free config must not use it.
        val calls = IntArray(2)
        injectReaders({ "Movistar" }, { DeviceCategory.PHONE }, calls)
        DeviceConfig.systemDefault(appContextMock())

        val config = DeviceConfig.systemDefault()
        assertNull(config.carrier)
        assertNull(config.category)
        assertFalse(config.collectCarrierAndCategory)
        assertNull(DeviceConfig.systemDefault("id").carrier)
        val device = deviceOnWire(config)!!
        assertFalse(device.containsKey("carrier"))
        assertFalse(device.containsKey("category"))
        assertEquals(0, calls[0])
        assertEquals(0, calls[1])

        // And an app that never registered a context at all.
        DeviceSignals.resetForTests()
        assertEquals(
            Pair<String?, DeviceCategory?>(null, null),
            DeviceSignals.resolve(DeviceConfig(collectCarrierAndCategory = true))
        )
    }

    @Test
    fun `carrier and category are read fresh for every request`() {
        var carrier: String? = "Movistar"
        var category: DeviceCategory? = DeviceCategory.PHONE
        val calls = IntArray(2)
        injectReaders({ carrier }, { category }, calls)
        val config = DeviceConfig.systemDefault(appContextMock())
        assertNull(config.carrier)
        assertNull(config.category)

        val first = deviceOnWire(config)!!
        assertEquals("Movistar", first.text("carrier"))
        assertEquals("phone", first.text("category"))

        carrier = "Claro"
        category = DeviceCategory.TABLET
        val second = deviceOnWire(config)!!
        assertEquals("Claro", second.text("carrier"))
        assertEquals("tablet", second.text("category"))
        assertEquals(2, calls[0])
        assertEquals(2, calls[1])
        assertNull(config.carrier)
        assertNull(config.category)
    }

    @Test
    fun `a value the app sets wins over the collected one`() {
        val calls = IntArray(2)
        injectReaders({ "Movistar" }, { DeviceCategory.PHONE }, calls)
        val base = DeviceConfig.systemDefault(appContextMock())

        val both = deviceOnWire(base.copy(carrier = "Manual", category = DeviceCategory.OTHER))!!
        assertEquals("Manual", both.text("carrier"))
        assertEquals("other", both.text("category"))
        assertEquals(0, calls[0])
        assertEquals(0, calls[1])

        val onlyCarrier = deviceOnWire(base.copy(carrier = "Manual"))!!
        assertEquals("Manual", onlyCarrier.text("carrier"))
        assertEquals("phone", onlyCarrier.text("category"))
        assertEquals(0, calls[0])
        assertEquals(1, calls[1])

        // A manual carrier that normalizes to nothing falls back to the collected one.
        val placeholder = deviceOnWire(base.copy(carrier = " wifi "))!!
        assertEquals("Movistar", placeholder.text("carrier"))
    }

    @Test
    fun `a cleared device configuration sends nothing until it is set again`() {
        val calls = IntArray(2)
        injectReaders({ "Movistar" }, { DeviceCategory.PHONE }, calls)
        val config = DeviceConfig.systemDefault(appContextMock())
        assertTrue(deviceOnWire(config)!!.containsKey("carrier"))
        val before = calls.sum()

        val request = Admoai.getInstance().let {
            it.clearDeviceConfig()
            it.prepareFinalDecisionRequest(DecisionRequestBuilder().addPlacement("home").build())
        }
        assertNull(json.parseToJsonElement(json.encodeToString(request)).jsonObject["device"])
        assertEquals(before, calls.sum())

        val reset = deviceOnWire(config.clear())!!
        assertFalse(reset.containsKey("carrier"))
        assertFalse(reset.containsKey("category"))
        assertEquals(before, calls.sum())
        assertFalse(DeviceConfig().resetToDefaults().collectCarrierAndCategory)
    }

    @Test
    fun `one failing reader does not affect the other`() {
        val calls = IntArray(2)
        injectReaders({ throw IllegalStateException("boom") }, { DeviceCategory.TABLET }, calls)
        val device = deviceOnWire(DeviceConfig.systemDefault(appContextMock(), "id-1"))!!
        assertFalse(device.containsKey("carrier"))
        assertEquals("tablet", device.text("category"))
        assertEquals("Android", device.text("os"))
        assertEquals("id-1", device.text("id"))

        injectReaders({ "Claro" }, { throw IllegalStateException("boom") }, calls)
        val other = deviceOnWire(DeviceConfig.systemDefault(appContextMock()))!!
        assertEquals("Claro", other.text("carrier"))
        assertFalse(other.containsKey("category"))
    }

    @Test
    fun `the SDK keeps only the application context`() {
        val activity = mockk<Context>()
        val application = mockk<Context>()
        every { activity.applicationContext } returns application
        DeviceConfig.systemDefault(activity)
        assertTrue(DeviceSignals.appContext === application)
    }

    @Test
    fun `turning off device collection drops carrier and category`() {
        val config = DeviceConfig(model = "x", carrier = "Movistar", category = DeviceCategory.TABLET)
        assertNull(deviceOnWire(config, collect = false))
    }
}
