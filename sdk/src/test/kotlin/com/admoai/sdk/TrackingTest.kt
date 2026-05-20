@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.network.AdMoaiApiService
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `given tracking info with impression when firing impression then sends tracking`() = runTest(UnconfinedTestDispatcher()) {
        // Given: SDK initialized and tracking info with impression
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.sdkScope = this
        admoaiInstance.apiService = mockApiService

        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = "https://track.admoai.com/impression"))
        )

        coEvery { mockApiService.fireTrackingUrl(any()) } returns flowOf(Unit)

        // When: Firing impression tracking (fire-and-forget, no collection needed)
        admoaiInstance.fireImpression(trackingInfo)

        // Then: Should call tracking with correct URL
        coVerify(exactly = 1) {
            mockApiService.fireTrackingUrl("https://track.admoai.com/impression")
        }
    }

    @Test
    fun `given tracking info with click when firing click then sends tracking`() = runTest(UnconfinedTestDispatcher()) {
        // Given: SDK initialized and tracking info with click
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.sdkScope = this
        admoaiInstance.apiService = mockApiService

        val trackingInfo = TrackingInfo(
            clicks = listOf(TrackingDetail(key = "default", url = "https://track.admoai.com/click"))
        )

        coEvery { mockApiService.fireTrackingUrl(any()) } returns flowOf(Unit)

        // When: Firing click tracking (fire-and-forget)
        admoaiInstance.fireClick(trackingInfo)

        // Then: Should call tracking with correct URL
        coVerify(exactly = 1) {
            mockApiService.fireTrackingUrl("https://track.admoai.com/click")
        }
    }

    @Test
    fun `given tracking info with custom event when firing custom event then sends tracking`() = runTest(UnconfinedTestDispatcher()) {
        // Given: SDK initialized and tracking info with custom event
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.sdkScope = this
        admoaiInstance.apiService = mockApiService

        val trackingInfo = TrackingInfo(
            custom = listOf(TrackingDetail(key = "video_start", url = "https://track.admoai.com/video_start"))
        )

        coEvery { mockApiService.fireTrackingUrl(any()) } returns flowOf(Unit)

        // When: Firing custom tracking event (fire-and-forget)
        admoaiInstance.fireCustomEvent(trackingInfo, "video_start")

        // Then: Should call tracking with correct URL
        coVerify(exactly = 1) {
            mockApiService.fireTrackingUrl("https://track.admoai.com/video_start")
        }
    }

    @Test
    fun `given empty tracking info when firing impression then no request is made`() = runTest(UnconfinedTestDispatcher()) {
        // Given: SDK initialized and empty tracking info
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.sdkScope = this
        admoaiInstance.apiService = mockApiService

        val trackingInfo = TrackingInfo(
            impressions = emptyList(),
            clicks = emptyList(),
            custom = emptyList()
        )

        // When: Firing impression tracking on empty tracking (fire-and-forget)
        admoaiInstance.fireImpression(trackingInfo)

        // Then: Should handle gracefully (no tracking calls)
        coVerify(exactly = 0) { mockApiService.fireTrackingUrl(any()) }
    }

    @Test
    fun `given network failure when firing impression then no exception thrown to caller`() = runTest(UnconfinedTestDispatcher()) {
        // Given: SDK initialized with a service that throws
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.sdkScope = this
        admoaiInstance.apiService = mockApiService

        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = "https://track.admoai.com/impression"))
        )
        coEvery { mockApiService.fireTrackingUrl(any()) } throws RuntimeException("Network failure")

        // When: Firing impression tracking — must not throw
        admoaiInstance.fireImpression(trackingInfo)

        // Then: No exception propagated, tracking was attempted
        coVerify(exactly = 1) { mockApiService.fireTrackingUrl(any()) }
    }

    @Test
    fun `given sdk not initialized when calling getInstance then throws exception`() {
        // Given: SDK is not initialized
        Admoai.resetForTesting()

        // When & Then: getting instance should throw
        try {
            Admoai.getInstance()
            fail("Should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not initialized") == true)
        }
    }

    @Test
    fun `given raw url when fireTracking called then fires the url`() = runTest(UnconfinedTestDispatcher()) {
        // Given: SDK initialized
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.sdkScope = this
        admoaiInstance.apiService = mockApiService

        coEvery { mockApiService.fireTrackingUrl(any()) } returns flowOf(Unit)

        // When: Firing a raw tracking URL directly
        admoaiInstance.fireTracking("https://track.admoai.com/raw")

        // Then: Should call tracking with that URL
        coVerify(exactly = 1) {
            mockApiService.fireTrackingUrl("https://track.admoai.com/raw")
        }
    }

    @Test
    fun `given no apiService when fireTracking called then no exception thrown`() {
        // Given: SDK initialized but apiService is null
        Admoai.initialize(testConfig)
        val admoaiInstance = Admoai.getInstance()
        admoaiInstance.apiService = null

        // When & Then: fireTracking should silently do nothing
        admoaiInstance.fireTracking("https://track.admoai.com/raw")
        // Test passes if no exception thrown
    }
}
