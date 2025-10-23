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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
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
 * Maps video options to localhost mock server scenario parameter
 * Returns null if combination is not available yet
 */
private fun getLocalMockScenario(delivery: String, endCard: String, skippable: Boolean): String? {
    return when {
        // JSON delivery combinations (4 total)
        delivery == "json" && endCard == "none" && !skippable -> "json_none"
        delivery == "json" && endCard == "none" && skippable -> "json_none_skippable"
        delivery == "json" && endCard == "native_endcard" && !skippable -> "json_native_endcard"
        delivery == "json" && endCard == "native_endcard" && skippable -> "json_native_endcard_skippable"
        
        // VAST Tag delivery combinations (4 total - VAST Companion not supported for VAST Tag)
        delivery == "vast_tag" && endCard == "none" && !skippable -> "vasttag_none"
        delivery == "vast_tag" && endCard == "none" && skippable -> "vasttag_none_skippable"
        delivery == "vast_tag" && endCard == "native_endcard" && !skippable -> "vasttag_native_endcard"
        delivery == "vast_tag" && endCard == "native_endcard" && skippable -> "vasttag_native_endcard_skippable"
        
        // VAST XML delivery combinations (5 total)
        delivery == "vast_xml" && endCard == "none" && !skippable -> "vastxml_none"
        delivery == "vast_xml" && endCard == "none" && skippable -> "vastxml_none_skippable"
        delivery == "vast_xml" && endCard == "native_endcard" && !skippable -> "vastxml_native_endcard"
        delivery == "vast_xml" && endCard == "native_endcard" && skippable -> "vastxml_native_endcard_skippable"
        delivery == "vast_xml" && endCard == "vast_companion" && !skippable -> "vastxml_vast_companion"
        // Missing: vastxml_vast_companion_skippable (not yet available in decision-engine)
        
        else -> null // Combination not available yet
    }
}

/**
 * Fetches mock video data from localhost decision-engine
 * Note: Using 10.0.2.2 for Android emulator (maps to host machine's localhost)
 */
private suspend fun fetchMockVideoData(scenario: String): Result<String> = withContext(Dispatchers.IO) {
    try {
        val url = URL("http://10.0.2.2:8080/v1/decision")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000  // Increased from 5s to 15s
        connection.readTimeout = 30000     // Increased from 10s to 30s
        
        // Set required headers
        connection.setRequestProperty("Accept-Language", "en")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-Decision-Version", "2025-11-01")
        connection.doOutput = true
        
        // Build request body with scenario as placement key
        val requestBody = """
            {
                "placements": [
                    {
                        "key": "$scenario",
                        "format": "video"
                    }
                ]
            }
        """.trimIndent()
        
        // Write request body
        connection.outputStream.use { outputStream ->
            outputStream.write(requestBody.toByteArray(Charsets.UTF_8))
        }
        
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
    val videoPlayer by viewModel.videoPlayer.collectAsStateWithLifecycle()
    
    // Compute current scenario (derived state, updates when inputs change)
    val currentScenario = getLocalMockScenario(videoDelivery, videoEndCard, isSkippable)
    
    var isLoadingMockData by remember { mutableStateOf(false) }
    var mockDataResult by remember { mutableStateOf<Result<String>?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                text = "Video Ad Demo Sandbox",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = buildAnnotatedString {
                    append("This is an interactive sandbox to experiment and understand video ad configurations, with examples of different video players too. These settings control how the Video Demo preview renders and ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("are independent of actual ad requests")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Video options configuration
            VideoOptionsSection(viewModel = viewModel)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Video player selection
            VideoPlayerSection(
                viewModel = viewModel,
                onOpenUrl = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Launch button
            Button(
                onClick = {
                    isLoadingMockData = true
                    
                    // Fetch and parse mock data, then navigate directly to video preview
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
                modifier = Modifier.fillMaxWidth(),
                enabled = currentScenario != null && videoPlayer.isNotEmpty() && videoPlayer != "jwplayer",
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
            
        }
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
                    Text("Fetching from decision-engine...")
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
                    if (mockDataResult?.isSuccess == true) "Mock Data Fetched" 
                    else "Error Fetching Data"
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Scenario: $currentScenario")
                    Text("Endpoint: POST https://10.0.2.2:8080/v1/decision")
                    Text("Placement Key: $currentScenario")
                    
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
                                text = "Make sure the decision-engine is running on localhost:8080",
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
