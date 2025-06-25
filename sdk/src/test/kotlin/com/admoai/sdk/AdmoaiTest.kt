package com.admoai.sdk

import com.admoai.sdk.config.AppConfig
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.config.UserConfig
import com.admoai.sdk.exception.AdMoaiConfigurationException
import com.admoai.sdk.model.request.*
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.network.AdMoaiApiService
import com.admoai.sdk.network.AdmoaiHttpRequest
import io.ktor.client.engine.HttpClientEngine
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.* // Using wildcard for now, can be made specific if issues persist
import org.junit.Before
import org.junit.Test

class AdmoaiTest {

    private lateinit var mockApiService: AdMoaiApiService
    // mockEngine is no longer primary for most tests, but keep for specific engine tests if any in future
    private lateinit var mockEngine: HttpClientEngine 

    private val minimalSDKConfig = SDKConfig(baseUrl = "https://test.admoai.com")
    private val placementKey = "test-placement"
    private val minimalDecisionRequest = DecisionRequest(placements = listOf(Placement(placementKey)))

    @Before
    fun setUp() {
        mockApiService = mockk(relaxed = true)
        mockEngine = mockk(relaxed = true) 
        Admoai.resetForTesting() 
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
        unmockkAll()
    }

    // --- Initialization and Singleton ---
    @Test
    fun `initialize success`() {
        // Initialize with null engine, Admoai should use default (CIO)
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        assertTrue(Admoai.isInitialized())
        assertNotNull(Admoai.getInstance())
        assertNotNull(Admoai.getInstance().apiService) // Check that apiService is internally initialized
    }

    // This test is no longer relevant since apiKey was removed
    // Keeping as placeholder in case we want to test other validation scenarios
    @Test
    fun `initialize with minimal config succeeds`() {
        Admoai.initialize(SDKConfig(baseUrl = "test.com"))
        assertTrue(Admoai.isInitialized())
    }

    @Test(expected = IllegalStateException::class)
    fun `getInstance before initialize throws exception`() {
        Admoai.getInstance()
    }

    @Test
    fun `isInitialized returns false before init`() {
        assertFalse(Admoai.isInitialized())
    }

    @Test
    fun `re-initialize updates config and keeps instance`() {
        // Ensure logging is OFF for this JVM unit test for BOTH configs
        val initialConfig = minimalSDKConfig.copy(enableLogging = false, networkClientEngine = null) // Logging explicitly false
        Admoai.initialize(initialConfig)
        val admoaiInstance1 = Admoai.getInstance()
        val initialApiService = admoaiInstance1.apiService // Capture the internally created one on the first init
        assertNotNull("Initial ApiService should not be null after first init", initialApiService)

        val newConfig = SDKConfig(baseUrl = "https://updated-test.admoai.com", enableLogging = true, networkClientEngine = null) // Logging explicitly false
        Admoai.initialize(newConfig) // This should reconfigure the existing instance
        val admoaiInstance2 = Admoai.getInstance()
        val newApiService = admoaiInstance2.apiService // Capture the apiService after re-init

        assertSame("Singleton instance should be the same", admoaiInstance1, admoaiInstance2) // Line 84
        assertNotNull("New ApiService should not be null after re-initialization", newApiService)
        assertNotEquals("ApiService should be a new instance after re-initialization with new config", initialApiService, newApiService)
    }

    // --- Configuration ---
    @Test
    fun `configure updates SDKConfig after initialization`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService // Set mock service

        val newSdkConfig = SDKConfig(baseUrl = "https://test.admoai.com", enableLogging = true, networkClientEngine = null)

        admoai.configure(newSdkConfig)
        // Test a behavior that depends on the new config (e.g., logging or apiService re-creation)
        // If configure re-creates apiService, then our mock would be replaced.
        // Let's assume configure re-initializes apiService based on new config.
        // We set it to mockApiService before configure, so after configure it should be different.
        assertNotEquals(mockApiService, admoai.apiService)
        assertNotNull(admoai.apiService) // apiService should be re-initialized by configure
    }

    @Test(expected = AdMoaiConfigurationException::class)
    fun `configure throws if Admoai becomes uninitialized after instance obtained`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val instance = Admoai.getInstance()
        instance.apiService = mockApiService
        Admoai.resetForTesting() 
        instance.configure(SDKConfig("new-base", enableLogging = true, networkClientEngine = null)) 
    }


    @Test(expected = IllegalStateException::class)
    fun `configure throws if called on an instance obtained before initialization (via getInstance)`() = runTest {
        val instance = Admoai.getInstance() 
        instance.configure(minimalSDKConfig) 
    }


    @Test
    fun `set and get UserConfig`() {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        val userConfig = UserConfig(id = "testUser", ip = "1.1.1.1")
        admoai.setUserConfig(userConfig)
        assertEquals(userConfig, admoai.getUserConfig())
        admoai.clearUserConfig()
        assertNull(admoai.getUserConfig())
    }

    @Test
    fun `set and get DeviceConfig`() {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        val deviceConfig = DeviceConfig(model = "Pixel Test", osName = "Android")
        admoai.setDeviceConfig(deviceConfig)
        assertEquals(deviceConfig, admoai.getDeviceConfig())
        admoai.clearDeviceConfig()
        assertNull(admoai.getDeviceConfig())
    }

    @Test
    fun `set and get AppConfig`() {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        val appConfig = AppConfig(appName = "TestApp", appVersion = "1.0")
        admoai.setAppConfig(appConfig)
        assertEquals(appConfig, admoai.getAppConfig())
        admoai.clearAppConfig()
        assertNull(admoai.getAppConfig())
    }

    @Test
    fun `createRequestBuilder returns new instance`() {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        val builder1 = admoai.createRequestBuilder()
        val builder2 = admoai.createRequestBuilder()
        assertNotNull(builder1)
        assertNotSame(builder1, builder2)
    }

    // --- requestAds & getHttpRequestData: Merging Logic Tests ---

    @Test(expected = AdMoaiConfigurationException::class)
    fun `requestAds before initialization throws AdMoaiConfigurationException`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        // admoai.apiService = mockApiService // No need to set, exception is pre-check
        Admoai.resetForTesting() 
        admoai.requestAds(minimalDecisionRequest).first() 
    }
    
    @Test(expected = AdMoaiConfigurationException::class)
    fun `requestAds if apiService is null throws AdMoaiConfigurationException`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null)) 
        val admoai = Admoai.getInstance()
        admoai.apiService = null // Manually set apiService to null
        admoai.requestAds(minimalDecisionRequest).first() 
    }


    @Test
    fun `prepareFinalDecisionRequest merges UserConfig - request overrides global`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        admoai.setUserConfig(UserConfig(id = "globalUser", ip = "1.1.1.1", timezone = "Global/Zone", consentData = Consent(false)))

        val request = DecisionRequestBuilder()
            .addPlacement(placementKey)
            .setUserId("localUser")
            .setUserIp("2.2.2.2")
            .setUserTimezone("Local/Zone")
            .setUserConsent(true)
            .build()
        
        val finalRequest = admoai.prepareFinalDecisionRequest(request) 
        assertEquals("localUser", finalRequest.user?.id)
        assertEquals("2.2.2.2", finalRequest.user?.ip)
        assertEquals("Local/Zone", finalRequest.user?.timezone)
        assertTrue(finalRequest.user?.consent?.gdpr == true)
    }

    @Test
    fun `prepareFinalDecisionRequest merges UserConfig - global used if request null`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        admoai.setUserConfig(UserConfig(id = "globalUser", ip = "1.1.1.1", timezone = "Global/Zone", consentData = Consent(false)))
        admoai.setDeviceConfig(DeviceConfig(timezone = "Device/Zone"))

        val request = DecisionRequestBuilder().addPlacement(placementKey).build()
        
        val finalRequest = admoai.prepareFinalDecisionRequest(request) 
        assertEquals("globalUser", finalRequest.user?.id)
        assertEquals("1.1.1.1", finalRequest.user?.ip)
        assertEquals("Global/Zone", finalRequest.user?.timezone)
        assertFalse(finalRequest.user?.consent?.gdpr == true)
    }
    
    @Test
    fun `prepareFinalDecisionRequest merges UserConfig - device timezone fallback`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        admoai.setUserConfig(UserConfig(id = "globalUser")) 
        admoai.setDeviceConfig(DeviceConfig(timezone = "Device/Zone"))

        val request = DecisionRequestBuilder().addPlacement(placementKey).build()
        
        val finalRequest = admoai.prepareFinalDecisionRequest(request) 
        assertEquals("Device/Zone", finalRequest.user?.timezone)
    }

    @Test
    fun `prepareFinalDecisionRequest adds AppConfig and DeviceConfig as top-level fields`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        admoai.setDeviceConfig(DeviceConfig(
            model = "TestModel", 
            osName = "TestOS", 
            osVersion = "13", 
            deviceId = "testDevId", 
            timezone = "Device/Zone",
            manufacturer = "TestManufacturer",
            language = "en-US"
        ))
        admoai.setAppConfig(AppConfig(
            appName = "TestApp", 
            appVersion = "1.1", 
            packageName = "com.test.app",
            buildNumber = "42",
            language = "es"
        ))

        val request = DecisionRequestBuilder()
            .addPlacement(placementKey)
            .addCustomTarget("existing_custom", "value")
            .build()

        val finalRequest = admoai.prepareFinalDecisionRequest(request) 
        
        // Check that custom targeting still works
        assertNotNull(finalRequest.targeting?.custom)
        val customMap = finalRequest.targeting?.custom?.associate { it.key to it.value }
        assertEquals(JsonPrimitive("value"), customMap?.get("existing_custom"))
        assertEquals(1, customMap?.size) 
        
        // Check app as a top-level field
        assertNotNull(finalRequest.app)
        assertEquals("TestApp", finalRequest.app?.name)
        assertEquals("1.1", finalRequest.app?.version)
        assertEquals("com.test.app", finalRequest.app?.identifier)
        assertEquals("42", finalRequest.app?.buildNumber)
        assertEquals("es", finalRequest.app?.language)
        
        // Check device as a top-level field
        assertNotNull(finalRequest.device)
        assertEquals("TestModel", finalRequest.device?.model)
        assertEquals("TestOS", finalRequest.device?.os)
        assertEquals("13", finalRequest.device?.osVersion)
        assertEquals("testDevId", finalRequest.device?.id)
        assertEquals("Device/Zone", finalRequest.device?.timezone)
        assertEquals("TestManufacturer", finalRequest.device?.manufacturer)
        assertEquals("en-US", finalRequest.device?.language)
    }

    @Test
    fun `prepareFinalDecisionRequest respects collectDeviceData false`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        admoai.setDeviceConfig(DeviceConfig(model = "TestModel"))

        val request = DecisionRequestBuilder()
            .addPlacement(placementKey)
            .disableDeviceCollection()
            .build()
        
        val finalRequest = admoai.prepareFinalDecisionRequest(request) 
        val customMap = finalRequest.targeting?.custom?.associate { it.key to it.value }
        assertNull(customMap?.get("sdk_device_model"))
        assertFalse(finalRequest.collectDeviceData)
    }

    @Test
    fun `prepareFinalDecisionRequest respects collectAppData false`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService
        admoai.setAppConfig(AppConfig(appName = "TestApp"))

        val request = DecisionRequestBuilder()
            .addPlacement(placementKey)
            .disableAppCollection()
            .build()
        
        val finalRequest = admoai.prepareFinalDecisionRequest(request) 
        val customMap = finalRequest.targeting?.custom?.associate { it.key to it.value }
        assertNull(customMap?.get("sdk_app_name"))
        assertFalse(finalRequest.collectAppData)
    }
    
    @Test
    fun `requestAds basic call now uses injected mockApiService`() = runTest {
        val decisionResponse = DecisionResponse(success = true)
        
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null)) 
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService // Inject the mock
        
        // Prepare the request that would be passed to the mock
        val preparedRequest = admoai.prepareFinalDecisionRequest(minimalDecisionRequest)
        coEvery { mockApiService.requestAds(preparedRequest) } returns flowOf(decisionResponse)

        val result = admoai.requestAds(minimalDecisionRequest).first()
        assertEquals(decisionResponse, result)
        
        coVerify { mockApiService.requestAds(preparedRequest) }
    }

    @Test(expected = AdMoaiConfigurationException::class)
    fun `getHttpRequestData before init throws AdMoaiConfigurationException`() {
         Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
         val admoai = Admoai.getInstance()
         Admoai.resetForTesting()
         admoai.getHttpRequestData(minimalDecisionRequest) 
    }
    
    @Test(expected = AdMoaiConfigurationException::class)
    fun `getHttpRequestData if apiService is null throws AdMoaiConfigurationException`() {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null)) 
        val admoai = Admoai.getInstance()
        admoai.apiService = null // Manually set apiService to null
        admoai.getHttpRequestData(minimalDecisionRequest)
    }

    @Test
    fun `getHttpRequestData now uses injected mockApiService`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null)) // Initialize Admoai FIRST
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService // Inject mock

        val expectedHttpRequest = AdmoaiHttpRequest("POST", "url", emptyMap(), "body")
        // Prepare the request using the initialized instance for coVerify
        val preparedRequest = admoai.prepareFinalDecisionRequest(minimalDecisionRequest)
        coEvery { mockApiService.getHttpRequestData(preparedRequest) } returns expectedHttpRequest
        
        val actualRequest = admoai.getHttpRequestData(minimalDecisionRequest)
        assertEquals(expectedHttpRequest, actualRequest)
                                                      
        coVerify { mockApiService.getHttpRequestData(preparedRequest) }
    }

    private val sampleTrackingInfo = TrackingInfo(
        impressions = listOf(TrackingDetail("default", "imp_default_url"), TrackingDetail("custom_imp", "imp_custom_url")),
        clicks = listOf(TrackingDetail("default", "click_default_url")),
        custom = listOf(TrackingDetail("event1", "custom_event1_url"))
    )

    @Test
    fun `fireImpression success now uses injected mockApiService`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null, enableLogging = true))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService // Inject mock
        
        coEvery { mockApiService.fireTrackingUrl("imp_default_url") } returns flowOf(Unit)

        admoai.fireImpression(sampleTrackingInfo, "default").first()
        coVerify { mockApiService.fireTrackingUrl("imp_default_url") }
    }
    
    @Test
    fun `fireImpression non-default key now uses injected mockApiService`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService // Inject mock

        coEvery { mockApiService.fireTrackingUrl("imp_custom_url") } returns flowOf(Unit)
        
        admoai.fireImpression(sampleTrackingInfo, "custom_imp").first()
        coVerify { mockApiService.fireTrackingUrl("imp_custom_url") }
    }

    @Test
    fun `fireImpression with no matching key does not call service`() = runTest {
         Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null, enableLogging = true))
         val admoai = Admoai.getInstance()
         admoai.apiService = mockApiService // Inject mock

        admoai.fireImpression(sampleTrackingInfo, "non_existent_key").first() 
        coVerify(exactly = 0) { mockApiService.fireTrackingUrl(any()) } 
    }

    @Test
    fun `fireEvent when apiService is null completes without error and logs warning`() = runTest {
        Admoai.initialize(minimalSDKConfig.copy(networkClientEngine = null, enableLogging = true)) 
        val admoai = Admoai.getInstance()
        admoai.apiService = null 
        
        var loggedWarning = false
        val originalOut = System.out
        val stream = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(stream))
        try {
            admoai.fireImpression(sampleTrackingInfo).first() 
            loggedWarning = stream.toString().contains("ApiService not initialized. Cannot fire impression.")
        } finally {
            System.setOut(originalOut)
        }
        assertTrue("Expected warning log was not found.", loggedWarning)
    }
}
