package com.admoai.sample.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.components.VideoOptionsSection
import com.admoai.sample.ui.components.VideoPlayerSection
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import com.admoai.sdk.model.response.DecisionResponse

/**
 * Video preview display modes
 */
enum class VideoPreviewMode {
    FullScreen,
    Inline
}

/**
 * Maps video options to localhost mock server scenario parameter
 * Returns null if combination is not available yet
 */
private fun getLocalMockScenario(delivery: String, endCard: String, skippable: Boolean): String? {
    return when {
        delivery == "json" && endCard == "none" && !skippable -> "json_none"
        delivery == "json" && endCard == "none" && skippable -> "json_none_skippable"
        delivery == "json" && endCard == "native_endcard" && !skippable -> "json_native-end-card"
        delivery == "vast_tag" && endCard == "none" && !skippable -> "vasttag_none"
        delivery == "vast_tag" && endCard == "native_endcard" && !skippable -> "vasttag_native-end-card"
        delivery == "vast_tag" && endCard == "native_endcard" && skippable -> "vasttag_native-end-card_skippable"
        delivery == "vast_tag" && endCard == "vast_companion" && !skippable -> "vasttag_vast-companion"
        delivery == "vast_xml" && endCard == "vast_companion" && !skippable -> "vastxml_vast-companion"
        else -> null // Combination not available yet
    }
}

/**
 * Fetches mock video data from localhost server
 * Note: Using 10.0.2.2 for Android emulator (maps to host machine's localhost)
 */
private suspend fun fetchMockVideoData(scenario: String): Result<String> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://10.0.2.2:8080/endpoint?scenario=$scenario")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("VideoAdDemo", "Fetched mock data for scenario: $scenario (${responseBody.length} chars)")
            Result.success(responseBody)
        } else {
            val errorMsg = "HTTP error: $responseCode"
            Log.e("VideoAdDemo", errorMsg)
            Result.failure(Exception(errorMsg))
        }
    } catch (e: Exception) {
        Log.e("VideoAdDemo", "Error fetching mock data: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Standalone screen for experimenting with video ad overlay configurations.
 * This is a demo/sandbox environment separate from the actual ad request flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAdDemoScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToVideoPreview: (String) -> Unit = {}
) {
    val videoDelivery by viewModel.videoDelivery.collectAsStateWithLifecycle()
    val videoEndCard by viewModel.videoEndCard.collectAsStateWithLifecycle()
    val isSkippable by viewModel.isSkippable.collectAsStateWithLifecycle()
    
    // Compute current scenario (derived state, updates when inputs change)
    val currentScenario = getLocalMockScenario(videoDelivery, videoEndCard, isSkippable)
    
    var showVideoPreview by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf<VideoPreviewMode>(VideoPreviewMode.FullScreen) }
    var showModeDialog by remember { mutableStateOf(false) }
    var isLoadingMockData by remember { mutableStateOf(false) }
    var mockDataResult by remember { mutableStateOf<Result<String>?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Ad Demo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "🎬 Video Ad Demo Sandbox",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This is an interactive sandbox to experiment with video ad overlay configurations. These settings control how the Video Demo preview renders and are independent of actual ad requests.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Video options configuration
            VideoOptionsSection(viewModel = viewModel)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Video player selection
            VideoPlayerSection(viewModel = viewModel)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Current configuration card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📋 Current Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "• Delivery: ${videoDelivery.uppercase().replace("_", " ")}\n" +
                               "• End Card: ${videoEndCard.replace("_", " ").replaceFirstChar { it.uppercase() }}\n" +
                               "• Skippable: ${if (isSkippable) "Yes (5s)" else "No"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (currentScenario != null) {
                        Text(
                            text = "Mock Scenario: $currentScenario",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "⚠️ This combination is not available yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Launch button
            Button(
                onClick = { showModeDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentScenario != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Launch Video Demo")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "💡 About This Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "This is an interactive sandbox to experiment with different video ad configurations. " +
                               "Each combination uses pre-configured mock data from a local development server.\n\n" +
                               "Simply configure your options above and click 'Launch Video Demo' to see how the SDK renders " +
                               "different video formats and overlay combinations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Note: Some combinations may not be available yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    // Preview mode selection dialog
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("Select Preview Mode") },
            text = {
                Column {
                    Text("How would you like to view the video demo?")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            previewMode = VideoPreviewMode.FullScreen
                            showModeDialog = false
                            isLoadingMockData = true
                            
                            // Fetch and parse mock data
                            scope.launch {
                                currentScenario?.let { scenario ->
                                    val result = fetchMockVideoData(scenario)
                                    isLoadingMockData = false
                                    
                                    result.fold(
                                        onSuccess = { jsonString ->
                                            try {
                                                val json = Json { ignoreUnknownKeys = true }
                                                val demoResponse = json.decodeFromString<DecisionResponse>(jsonString)
                                                
                                                // Store in ViewModel
                                                viewModel.setDemoResponse(demoResponse)
                                                
                                                // Get placement key from response
                                                val placementKey = demoResponse.data?.firstOrNull()?.placement ?: "demo"
                                                
                                                // Navigate to video preview
                                                onNavigateToVideoPreview(placementKey)
                                            } catch (e: Exception) {
                                                Log.e("VideoAdDemo", "Parse error: ${e.message}", e)
                                                mockDataResult = Result.failure(Exception("JSON parse error: ${e.message}"))
                                                showResultDialog = true
                                            }
                                        },
                                        onFailure = { error ->
                                            mockDataResult = result
                                            showResultDialog = true
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Full Screen")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = {
                            previewMode = VideoPreviewMode.Inline
                            showModeDialog = false
                            isLoadingMockData = true
                            
                            // Fetch mock data for inline preview (placeholder for now)
                            scope.launch {
                                currentScenario?.let { scenario ->
                                    mockDataResult = fetchMockVideoData(scenario)
                                    isLoadingMockData = false
                                    showResultDialog = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inline Preview (Coming Soon)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Loading dialog
    if (isLoadingMockData) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Loading Mock Data") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Fetching from localhost:8080...")
                }
            },
            confirmButton = {}
        )
    }
    
    // Result dialog showing fetched data
    if (showResultDialog && mockDataResult != null) {
        AlertDialog(
            onDismissRequest = { 
                showResultDialog = false
                mockDataResult = null
            },
            title = { 
                Text(
                    if (mockDataResult?.isSuccess == true) "✅ Mock Data Fetched" 
                    else "❌ Error Fetching Data"
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Scenario: $currentScenario")
                    Text("Mode: ${previewMode.name}")
                    Text("Endpoint: https://10.0.2.2:8080/endpoint?scenario=$currentScenario")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    mockDataResult?.fold(
                        onSuccess = { data ->
                            Text(
                                text = "Response (${data.length} chars):",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = data.take(500) + if (data.length > 500) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "✨ Next: Parse this data and display in VideoPreviewScreen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onFailure = { error ->
                            Text(
                                text = "Error: ${error.message}",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💡 Make sure the mock server is running on localhost:8080",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showResultDialog = false
                    mockDataResult = null
                }) {
                    Text("Close")
                }
            }
        )
    }
}
