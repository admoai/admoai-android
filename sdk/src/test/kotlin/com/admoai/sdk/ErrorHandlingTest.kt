package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiConfigurationException
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.request.Placement
import com.admoai.sdk.network.AdMoaiApiService
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*

/**
 * Tests for error handling behavior in the AdMoai SDK.
 */
class ErrorHandlingTest {

    private lateinit var mockApiService: AdMoaiApiService
    private val testConfig = SDKConfig(baseUrl = "https://test.admoai.com", enableLogging = true)

    @Before
    fun setUp() {
        mockApiService = mockk(relaxed = true)
        Admoai.resetForTesting()
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
        unmockkAll()
    }

    @Test
    fun `given network error when requesting ad then propagates exception`() = runTest {
        // Given: SDK initialized but network call fails
        Admoai.initialize(testConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService

        val placement = Placement("home")
        val request = DecisionRequest(placements = listOf(placement))
        val preparedRequest = admoai.prepareFinalDecisionRequest(request)
        
        coEvery { mockApiService.requestAds(preparedRequest) } throws RuntimeException("Network error")
        
        // When & Then: Requesting ad decisions with network failure should propagate error
        try {
            admoai.requestAds(request).first()
            fail("Should have thrown an exception")
        } catch (e: RuntimeException) {
            assertEquals("Network error", e.message)
        }
    }

    @Test
    fun `given invalid configuration when initializing then accepts configuration without validation`() {
        // Given & When: SDK configuration with empty base URL (SDK doesn't validate during init)
        val configWithEmptyUrl = SDKConfig(baseUrl = "", enableLogging = true)
        
        // Then: Should accept configuration without throwing exception (no validation during init)
        Admoai.initialize(configWithEmptyUrl)
        assertTrue("SDK should be initialized even with empty baseUrl", Admoai.isInitialized())
        Admoai.resetForTesting()
    }

    @Test(expected = IllegalStateException::class)
    fun `given sdk not initialized when requesting ad then throws exception`() = runTest {
        // Given: SDK is not initialized
        Admoai.resetForTesting()
        
        // When & Then: Accessing uninitialized SDK should throw exception
        Admoai.getInstance()
    }

    @Test(expected = AdMoaiConfigurationException::class)
    fun `given api service null when requesting ad then throws configuration exception`() = runTest {
        // Given: SDK initialized but API service is null
        Admoai.initialize(testConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = null
        
        val placement = Placement("home")
        val request = DecisionRequest(placements = listOf(placement))
        
        // When & Then: Should throw configuration exception
        admoai.getHttpRequestData(request)
    }
}
