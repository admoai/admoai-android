package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.request.Placement
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.network.AdMoaiApiService
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*

/**
 * Tests for ad request behavior in the AdMoai SDK.
 */
class AdRequestTest {

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
    fun `given valid placement when requesting ad then returns decision response`() = runTest {
        // Given: SDK initialized and valid placement
        val decisionResponse = DecisionResponse(success = true)
        Admoai.initialize(testConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService

        val placement = Placement("home")
        val request = DecisionRequest(placements = listOf(placement))
        val preparedRequest = admoai.prepareFinalDecisionRequest(request)
        
        coEvery { mockApiService.requestAds(preparedRequest) } returns flowOf(decisionResponse)
        
        // When: Requesting ad decisions
        val result = admoai.requestAds(request).first()
        
        // Then: Should return successful response
        assertEquals(decisionResponse, result)
        coVerify { mockApiService.requestAds(preparedRequest) }
    }

    @Test
    fun `given multiple placements when requesting ad then handles all placements`() = runTest {
        // Given: SDK initialized and multiple placements
        val decisionResponse = DecisionResponse(success = true)
        Admoai.initialize(testConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService

        val placements = listOf(
            Placement("home"),
            Placement("sidebar")
        )
        val request = DecisionRequest(placements = placements)
        val preparedRequest = admoai.prepareFinalDecisionRequest(request)
        
        coEvery { mockApiService.requestAds(preparedRequest) } returns flowOf(decisionResponse)
        
        // When: Requesting ad decisions for multiple placements
        val result = admoai.requestAds(request).first()
        
        // Then: Should handle all placements
        assertEquals(decisionResponse, result)
        coVerify { mockApiService.requestAds(preparedRequest) }
    }

    @Test
    fun `given empty placements when requesting ad then handles gracefully`() = runTest {
        // Given: SDK initialized with empty placements
        val decisionResponse = DecisionResponse(success = true)
        Admoai.initialize(testConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService

        val request = DecisionRequest(placements = emptyList())
        val preparedRequest = admoai.prepareFinalDecisionRequest(request)
        
        coEvery { mockApiService.requestAds(preparedRequest) } returns flowOf(decisionResponse)
        
        // When: Requesting ad decisions with empty placements
        val result = admoai.requestAds(request).first()
        
        // Then: Should handle empty placements gracefully
        assertEquals(decisionResponse, result)
        coVerify { mockApiService.requestAds(preparedRequest) }
    }
}
