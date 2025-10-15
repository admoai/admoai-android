package com.admoai.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sample.Routes
import com.admoai.sample.ui.MainViewModel
import io.ktor.client.engine.okhttp.OkHttp
import com.admoai.sample.ui.screens.CustomTargetingScreen
import com.admoai.sample.ui.screens.DecisionRequestScreen
import com.admoai.sample.ui.screens.GeoTargetingScreen
import com.admoai.sample.ui.screens.LocationTargetingScreen
import com.admoai.sample.ui.screens.PlacementPickerScreen
import com.admoai.sample.ui.screens.RequestPreviewScreen
import com.admoai.sample.ui.screens.ResponseDetailsScreen
import com.admoai.sample.ui.screens.TimezonePickerScreen
import com.admoai.sample.ui.screens.CreativeDetailScreen
import com.admoai.sample.ui.screens.VideoAdDemoScreen
import com.admoai.sample.ui.screens.ComposeIntegrationScreen
import com.admoai.sample.ui.screens.previews.HomePreviewScreen
import com.admoai.sample.ui.screens.previews.MenuPreviewScreen
import com.admoai.sample.ui.screens.previews.PromotionsPreviewScreen
import com.admoai.sample.ui.screens.previews.RideSummaryPreviewScreen
import com.admoai.sample.ui.screens.previews.SearchPreviewScreen
import com.admoai.sample.ui.screens.previews.VehicleSelectionPreviewScreen
import com.admoai.sample.ui.screens.previews.WaitingPreviewScreen
import com.admoai.sample.ui.screens.previews.FreeMinutesPreviewScreen
import com.admoai.sample.ui.screens.VideoPreviewScreen
import com.admoai.sample.ui.theme.AdmoaikotlinTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the AdMoai SDK with OkHttp engine to avoid TLS issues
        val config = SDKConfig(
            baseUrl = "https://mock.api.admoai.com",
            enableLogging = true,
            networkClientEngine = OkHttp.create()
        )
        Admoai.initialize(config)

        setContent {
            AdmoaikotlinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdMoaiNavHost(viewModel)
                }
            }
        }
    }
}

/**
 * Navigation routes for the app
 */
object Routes {
    const val DECISION_REQUEST = "decision_request"
    const val PLACEMENT_PICKER = "placement_picker"
    const val GEO_TARGETING = "geo_targeting"
    const val REQUEST_PREVIEW = "request_preview"
    const val RESPONSE_DETAILS = "response_details"
    const val LOCATION_TARGETING = "location_targeting"
    const val CUSTOM_TARGETING = "custom_targeting"
    const val TIMEZONE_PICKER = "timezone_picker"
    const val VIDEO_AD_DEMO = "video_ad_demo"
    const val COMPOSE_INTEGRATION = "compose_integration"
    
    // Preview screen routes
    const val PROMOTIONS_PREVIEW = "promotions_preview"
    const val HOME_PREVIEW = "home_preview"
    const val SEARCH_PREVIEW = "search_preview"
    const val MENU_PREVIEW = "menu_preview"
    const val WAITING_PREVIEW = "waiting_preview"
    const val FREE_MINUTES_PREVIEW = "free_minutes_preview"
    const val VEHICLE_SELECTION_PREVIEW = "vehicle_selection_preview"
    const val RIDE_SUMMARY_PREVIEW = "ride_summary_preview"
    const val VIDEO_PREVIEW = "video_preview"
}

/**
 * Main navigation host for the AdMoai sample application.
 */
@Composable
fun AdMoaiNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    
    // Creative detail modal state
    val showingCreativeDetail = viewModel.showingCreativeDetail.collectAsState()
    val selectedAdData = viewModel.selectedAdData.collectAsState()
    
    // Show creative detail modal when visible
    if (showingCreativeDetail.value) {
        CreativeDetailScreen(
            adData = selectedAdData.value,
            onDismiss = { viewModel.hideCreativeDetail() },
            onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) }
        )
    }
    
    NavHost(
        navController = navController,
        startDestination = Routes.DECISION_REQUEST
    ) {
        composable(Routes.DECISION_REQUEST) {
            DecisionRequestScreen(
                viewModel = viewModel,
                onShowRequest = {
                    // Update the request JSON preview and navigate
                    viewModel.updateRequestJsonPreview()
                    navController.navigate(Routes.REQUEST_PREVIEW)
                },
                onRequestPreview = {
                    // Launch ad request and navigate to demo screen with live data
                    viewModel.loadAds()
                    
                    // Navigate to the appropriate demo screen based on selected placement
                    val placementKey = viewModel.placementKey.value
                    when (placementKey) {
                        "home" -> navController.navigate("${Routes.HOME_PREVIEW}/$placementKey")
                        "search" -> navController.navigate("${Routes.SEARCH_PREVIEW}/$placementKey")
                        "menu" -> navController.navigate("${Routes.MENU_PREVIEW}/$placementKey")
                        "promotions" -> navController.navigate("${Routes.PROMOTIONS_PREVIEW}/$placementKey")
                        "waiting" -> navController.navigate("${Routes.WAITING_PREVIEW}/$placementKey")
                        "freeMinutes" -> navController.navigate("${Routes.FREE_MINUTES_PREVIEW}/$placementKey")
                        "vehicleSelection" -> navController.navigate("${Routes.VEHICLE_SELECTION_PREVIEW}/$placementKey")
                        "rideSummary" -> navController.navigate("${Routes.RIDE_SUMMARY_PREVIEW}/$placementKey")
                        else -> navController.navigate("${Routes.HOME_PREVIEW}/$placementKey") // Default fallback
                    }
                },
                onPlacementClick = {
                    navController.navigate(Routes.PLACEMENT_PICKER)
                },
                onGeoTargetingClick = {
                    navController.navigate(Routes.GEO_TARGETING)
                },
                onLocationTargetingClick = {
                    navController.navigate(Routes.LOCATION_TARGETING)
                },
                onCustomTargetingClick = {
                    navController.navigate(Routes.CUSTOM_TARGETING)
                },
                onTimezonePickerClick = {
                    navController.navigate(Routes.TIMEZONE_PICKER)
                },
                onVideoAdDemoClick = {
                    navController.navigate(Routes.VIDEO_AD_DEMO)
                },
                onComposeIntegrationClick = {
                    navController.navigate(Routes.COMPOSE_INTEGRATION)
                },
                onNavigateBack = {
                    // Currently at the root, no back navigation
                }
            )
        }
        
        composable(Routes.PLACEMENT_PICKER) {
            PlacementPickerScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.GEO_TARGETING) {
            GeoTargetingScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.LOCATION_TARGETING) {
            LocationTargetingScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.CUSTOM_TARGETING) {
            CustomTargetingScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.TIMEZONE_PICKER) {
            TimezonePickerScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.REQUEST_PREVIEW) {
            RequestPreviewScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.RESPONSE_DETAILS) {
            ResponseDetailsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Preview screens
        composable(
            route = "${Routes.PROMOTIONS_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                PromotionsPreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    onAdClick = { clickedAdData -> viewModel.showCreativeDetail(clickedAdData) },
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) }
                )
            }
        }
        
        // Home preview screen
        composable(
            route = "${Routes.HOME_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                HomePreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    onThemeToggle = { /* Toggle theme */ },
                    onAdClick = { clickedAdData -> viewModel.showCreativeDetail(clickedAdData) },
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) }
                )
            }
        }

        // Search preview screen
        composable(
            route = "${Routes.SEARCH_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                SearchPreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    // Removed onThemeToggle and onAdClick as they're not used for search placement
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) }
                )
            }
        }

        // Menu preview screen
        composable(
            route = "${Routes.MENU_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                MenuPreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    onAdClick = { clickedAdData -> viewModel.showCreativeDetail(clickedAdData) },
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) }
                )
            }
        }

        // Waiting preview screen
        composable(
            route = "${Routes.WAITING_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                WaitingPreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    onAdClick = { clickedAdData -> viewModel.showCreativeDetail(clickedAdData) },
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) },
                    onThemeToggle = { /* Theme toggle action */ }
                )
            }
        }

        // Free Minutes preview screen
        composable(
            route = "${Routes.FREE_MINUTES_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                FreeMinutesPreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    onAdClick = { clickedAdData -> viewModel.showCreativeDetail(clickedAdData) },
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) },
                    onThemeToggle = { /* Theme toggle action */ }
                )
            }
        }

        // Vehicle Selection preview screen
        composable(
            route = "${Routes.VEHICLE_SELECTION_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                VehicleSelectionPreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    onAdClick = { clickedAdData -> viewModel.showCreativeDetail(clickedAdData) },
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) },
                    onThemeToggle = { /* Theme toggle action */ }
                )
            }
        }

        composable(Routes.VIDEO_AD_DEMO) {
            VideoAdDemoScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVideoPreview = { placementKey ->
                    navController.navigate("${Routes.VIDEO_PREVIEW}/$placementKey")
                }
            )
        }
        
        composable(Routes.COMPOSE_INTEGRATION) {
            ComposeIntegrationScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Video preview screen
        composable(
            route = "${Routes.VIDEO_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            
            VideoPreviewScreen(
                viewModel = viewModel,
                placementKey = placementKey,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "${Routes.RIDE_SUMMARY_PREVIEW}/{placementKey}",
            arguments = listOf(navArgument("placementKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val placementKey = backStackEntry.arguments?.getString("placementKey") ?: ""
            val placement = viewModel.getPlacementByKey(placementKey)
            val adData = viewModel.getAdDataForPlacement(placementKey)
            
            // Observe loading state
            val isLoading by viewModel.isLoading.collectAsState()
            
            placement?.let {
                RideSummaryPreviewScreen(
                    viewModel = viewModel,
                    placement = it,
                    adData = adData,
                    isLoading = isLoading,
                    onBackClick = { navController.popBackStack() },
                    onDetailsClick = { navController.navigate(Routes.RESPONSE_DETAILS) },
                    onRefreshClick = { viewModel.loadAds() },
                    onAdClick = { clickedAdData -> viewModel.showCreativeDetail(clickedAdData) },
                    onTrackEvent = { eventType, url -> viewModel.trackAdEvent(eventType, url) },
                    onThemeToggle = { /* Theme toggle action */ }
                )
            }
        }
    }
}

/**
 * Format the decision response to display it in the UI
 */
private fun formatResponse(response: DecisionResponse): String {
    val sb = StringBuilder()
    sb.append("Response success: ${response.success}\n")
    
    response.data?.let { data ->
        if (data.isEmpty()) {
            sb.append("No ads received.")
            return sb.toString()
        }
        
        sb.append("Ads received: ${data.size}\n\n")
        
        data.forEachIndexed { index, adData ->
        sb.append("Ad #${index + 1}\n")
        sb.append("  Placement: ${adData.placement}\n")
        
        if (adData.creatives.isNotEmpty()) {
            sb.append("  Creatives: ${adData.creatives.size}\n")
            
            adData.creatives.forEachIndexed { creativeIndex, creative ->
                sb.append("  Creative #${creativeIndex + 1}:\n")
                
                // Format the advertiser info if available
                sb.append("    Advertiser: ${creative.advertiser.name}\n")
                
                // Format the content items
                sb.append("    Contents: ${creative.contents.size} items\n")
                creative.contents.forEach { content ->
                    sb.append("      - Type: ${content.type}, Value: ${content.value}\n")
                }
                
                // Format the tracking info
                val tracking = creative.tracking
                sb.append("    Tracking URLs:\n")
                sb.append("      - Impression URLs: ${tracking.impressions?.size ?: 0}\n")
                sb.append("      - Click URLs: ${tracking.clicks?.size ?: 0}\n")
            }
        } else {
            sb.append("  No creative data\n")
        }
        
        sb.append("\n")
    }
    } ?: sb.append("No ads received.")
    
    return sb.toString()
}
