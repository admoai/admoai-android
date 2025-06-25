package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.network.AdMoaiApiService
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*

/**
 * Tests for tracking behavior in the AdMoai SDK.
 */
class TrackingTest {

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
    fun `given tracking info with impression when firing impression then sends tracking`() = runTest {
        // Given: SDK initialized and tracking info with impression
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.apiService = mockApiService
        
        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = "https://track.admoai.com/impression"))
        )
        
        coEvery { mockApiService.fireTrackingUrl(any()) } returns flowOf(Unit)
        
        // When: Firing impression tracking
        admoaiInstance.fireImpression(trackingInfo).first()
        
        // Then: Should call tracking with correct URL
        coVerify(exactly = 1) { 
            mockApiService.fireTrackingUrl("https://track.admoai.com/impression") 
        }
    }

    @Test
    fun `given tracking info with click when firing click then sends tracking`() = runTest {
        // Given: SDK initialized and tracking info with click
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.apiService = mockApiService
        
        val trackingInfo = TrackingInfo(
            clicks = listOf(TrackingDetail(key = "default", url = "https://track.admoai.com/click"))
        )
        
        coEvery { mockApiService.fireTrackingUrl(any()) } returns flowOf(Unit)
        
        // When: Firing click tracking
        admoaiInstance.fireClick(trackingInfo).first()
        
        // Then: Should call tracking with correct URL
        coVerify(exactly = 1) { 
            mockApiService.fireTrackingUrl("https://track.admoai.com/click") 
        }
    }

    @Test
    fun `given tracking info with custom event when firing custom event then sends tracking`() = runTest {
        // Given: SDK initialized and tracking info with custom event
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.apiService = mockApiService
        
        val trackingInfo = TrackingInfo(
            custom = listOf(TrackingDetail(key = "video_start", url = "https://track.admoai.com/video_start"))
        )
        
        coEvery { mockApiService.fireTrackingUrl(any()) } returns flowOf(Unit)
        
        // When: Firing custom tracking event
        admoaiInstance.fireCustomEvent(trackingInfo, "video_start").first()
        
        // Then: Should call tracking with correct URL
        coVerify(exactly = 1) { 
            mockApiService.fireTrackingUrl("https://track.admoai.com/video_start") 
        }
    }

    @Test
    fun `given empty tracking info when firing impression then handles gracefully`() = runTest {
        // Given: SDK initialized and empty tracking info
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.apiService = mockApiService
        
        val trackingInfo = TrackingInfo(
            impressions = emptyList(),
            clicks = emptyList(), 
            custom = emptyList()
        )
        
        // When: Firing impression tracking on empty tracking
        val result = admoaiInstance.fireImpression(trackingInfo).first()
        
        // Then: Should handle gracefully (no tracking calls)
        coVerify(exactly = 0) { mockApiService.fireTrackingUrl(any()) }
        assertNotNull(result)
    }

    @Test
    fun `given sdk not initialized when firing tracking then throws exception`() = runTest {
        // Given: SDK is not initialized
        Admoai.resetForTesting()
        
        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = "https://track.admoai.com/impression"))
        )
        
        // When & Then: Firing tracking should throw exception
        try {
            val admoaiInstance = Admoai.getInstance()
            admoaiInstance.fireImpression(trackingInfo).first()
            fail("Should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not initialized") == true)
        }
    }
}
