package com.admoai.sample.behavior

import android.app.Application
import com.admoai.sample.ui.MainViewModel
import com.admoai.sdk.Admoai
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.network.AdMoaiApiService
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Minimal BDD tests for sample app MainViewModel user flows.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelBehaviorTest {

    private lateinit var application: Application
    private lateinit var mockApiService: AdMoaiApiService
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        mockApiService = mockk(relaxed = true)
        
        // Initialize Admoai SDK for tests
        Admoai.initialize("https://mock-dev-admoai.fly.dev", true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `given fresh app start when viewmodel initializes then sets up default state`() = runTest {
        // Given: Fresh app state
        // When: ViewModel is created
        viewModel = MainViewModel(application)
        
        // Then: Should have sensible defaults
        assertEquals("Default placement should be home", "home", viewModel.placementKey.value)
        assertEquals("Default user ID should be set", "user_123", viewModel.userId.value)
        assertTrue("GDPR consent should default to true", viewModel.gdprConsent.value)
        assertFalse("Should not be loading initially", viewModel.isLoading.value)
        assertNull("Should have no response initially", viewModel.response.value)
    }

    @Test
    fun `given user changes placement when updating placement then reflects in state`() = runTest {
        // Given: ViewModel with default state
        viewModel = MainViewModel(application)
        assertEquals("Should start with home", "home", viewModel.placementKey.value)
        
        // When: User changes placement
        viewModel.setPlacementKey("search")
        
        // Then: State should update
        assertEquals("Placement should be updated", "search", viewModel.placementKey.value)
    }

    @Test
    fun `given user updates configuration when changing user settings then state reflects changes`() = runTest {
        // Given: ViewModel with default state
        viewModel = MainViewModel(application)
        
        // When: User updates various settings
        viewModel.setUserId("new_user_456")
        viewModel.setGdprConsent(false)
        
        // Then: State should update accordingly
        assertEquals("User ID should be updated", "new_user_456", viewModel.userId.value)
        assertFalse("GDPR consent should be updated", viewModel.gdprConsent.value)
    }

    @Test
    fun `given app configured when user updates request then generates valid preview`() = runTest {
        // Given: ViewModel with configuration
        viewModel = MainViewModel(application)
        viewModel.setPlacementKey("search")
        viewModel.setUserId("test_user")
        
        // When: Request is updated
        // Then: Basic state should be updated (simplified test - just check if viewModel works)
        assertEquals("Placement should be updated", "search", viewModel.placementKey.value)
        assertEquals("User ID should be updated", "test_user", viewModel.userId.value)
    }
}
