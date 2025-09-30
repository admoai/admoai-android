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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

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
        val url = URL("http://10.0.2.2:8080/endpoint?scenario=$scenario")
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
    onNavigateBack: () -> Unit
) {
    val videoDelivery by viewModel.videoDelivery.collectAsStateWithLifecycle()
    val videoEndCard by viewModel.videoEndCard.collectAsStateWithLifecycle()
    val isSkippable by viewModel.isSkippable.collectAsStateWithLifecycle()
    
    // Compute current scenario
    val currentScenario = remember(videoDelivery, videoEndCard, isSkippable) {
        getLocalMockScenario(videoDelivery, videoEndCard, isSkippable)
    }
    
    var showVideoPreview by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf<VideoPreviewMode>(VideoPreviewMode.FullScreen) }
    var showModeDialog by remember { mutableStateOf(false) }
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
                    
                    TextButton(
                        onClick = {
                            previewMode = VideoPreviewMode.FullScreen
                            showModeDialog = false
                            showVideoPreview = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Full Screen")
                    }
                    
                    TextButton(
                        onClick = {
                            previewMode = VideoPreviewMode.Inline
                            showModeDialog = false
                            showVideoPreview = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inline Preview (Below)")
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
    
    // Show video preview based on mode
    if (showVideoPreview && currentScenario != null) {
        when (previewMode) {
            VideoPreviewMode.FullScreen -> {
                AlertDialog(
                    onDismissRequest = { showVideoPreview = false },
                    title = { Text("🎬 Video Demo Launching") },
                    text = { 
                        Column {
                            Text(
                                text = "Ready to launch video demo!",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Scenario: $currentScenario")
                            Text("Delivery: ${videoDelivery.uppercase()}")
                            Text("End Card: ${videoEndCard.replace("_", " ")}")
                            Text("Skippable: ${if (isSkippable) "Yes (5s)" else "No"}")
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Endpoint: http://localhost:8080/endpoint?scenario=$currentScenario",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "⚠️ Full video player integration coming in next iteration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showVideoPreview = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            VideoPreviewMode.Inline -> {
                AlertDialog(
                    onDismissRequest = { showVideoPreview = false },
                    title = { Text("📱 Inline Preview") },
                    text = { 
                        Text("Inline preview mode coming soon!\nThis will display the video player embedded within the screen.") 
                    },
                    confirmButton = {
                        TextButton(onClick = { showVideoPreview = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}
