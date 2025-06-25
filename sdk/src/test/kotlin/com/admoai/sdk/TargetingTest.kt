package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.*
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.network.AdMoaiApiService
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.*
import org.junit.Assert.*

/**
 * Tests for targeting behavior in the AdMoai SDK.
 */
class TargetingTest {

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
    fun `given geo targeting when building request then includes location data`() = runTest {
        // Given: SDK initialized with geo targeting
        val decisionResponse = DecisionResponse(success = true)
        Admoai.initialize(testConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService

        val targeting = Targeting(
            location = listOf(
                LocationTargetingInfo(
                    latitude = 37.7749,
                    longitude = -122.4194
                )
            )
        )
        val placement = Placement("home")
        val request = DecisionRequest(
            placements = listOf(placement),
            targeting = targeting
        )
        
        val preparedRequest = admoai.prepareFinalDecisionRequest(request)
        coEvery { mockApiService.requestAds(preparedRequest) } returns flowOf(decisionResponse)
        
        // When: Building request with geo targeting
        val result = admoai.requestAds(request).first()
        
        // Then: Should include location data in prepared request
        assertEquals(decisionResponse, result)
        coVerify { mockApiService.requestAds(preparedRequest) }
        assertNotNull(preparedRequest.targeting?.location)
        assertEquals(37.7749, preparedRequest.targeting?.location?.get(0)?.latitude)
        assertEquals(-122.4194, preparedRequest.targeting?.location?.get(0)?.longitude)
    }

    @Test
    fun `given custom targeting when building request then includes custom parameters`() = runTest {
        // Given: SDK initialized with custom targeting
        val decisionResponse = DecisionResponse(success = true)
        Admoai.initialize(testConfig.copy(networkClientEngine = null))
        val admoai = Admoai.getInstance()
        admoai.apiService = mockApiService

        val customTargeting = listOf(
            CustomTargetingInfo(
                key = "age_group",
                value = JsonPrimitive("25-34")
            ),
            CustomTargetingInfo(
                key = "interests", 
                value = JsonPrimitive("technology")
            )
        )
        val targeting = Targeting(custom = customTargeting)
        val placement = Placement("home")
        val request = DecisionRequest(
            placements = listOf(placement),
            targeting = targeting
        )
        
        val preparedRequest = admoai.prepareFinalDecisionRequest(request)
        coEvery { mockApiService.requestAds(preparedRequest) } returns flowOf(decisionResponse)
        
        // When: Building request with custom targeting
        val result = admoai.requestAds(request).first()
        
        // Then: Should include custom parameters in prepared request
        assertEquals(decisionResponse, result)
        coVerify { mockApiService.requestAds(preparedRequest) }
        assertNotNull(preparedRequest.targeting?.custom)
        assertEquals(2, preparedRequest.targeting?.custom?.size)
    }
}
