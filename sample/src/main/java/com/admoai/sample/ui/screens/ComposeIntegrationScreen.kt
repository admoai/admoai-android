package com.admoai.sample.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admoai.sdk.Admoai
import com.admoai.sdk.compose.AdState
import com.admoai.sdk.compose.rememberAdState
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.request.Placement
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.components.AdCard

/**
 * Screen demonstrating SDK's Compose integration with rememberAdState
 * Shows both traditional ViewModel approach and declarative Compose approach
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeIntegrationScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val placementKey by viewModel.placementKey.collectAsStateWithLifecycle()
    
    // Use the SAME DecisionRequest for both approaches to ensure identical ads
    val decisionRequest = remember(placementKey) {
        viewModel.buildRequest() // This includes all targeting, user data, etc.
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Integration Demo") },
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
                text = "SDK Compose Integration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This screen demonstrates how to use the AdMoai SDK's declarative Compose integration with rememberAdState.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // SDK Compose Integration Demo
            ComposeApproachDemo(
                decisionRequest = decisionRequest
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Comparison with ViewModel approach
            ViewModelApproachDemo(
                viewModel = viewModel,
                decisionRequest = decisionRequest // Pass the SAME request for comparison
            )
        }
    }
}

/**
 * Demonstrates the SDK's declarative Compose approach using rememberAdState
 */
@Composable
private fun ComposeApproachDemo(
    decisionRequest: DecisionRequest
) {
    // Track reload key to trigger new ad requests
    var reloadKey by remember { mutableStateOf(0) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "✨ SDK Compose Integration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Using rememberAdState composable:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // This is the key demonstration: Using the SDK's Compose integration
            // The reloadKey changes to trigger new ad requests
            val adState = rememberAdState(
                admoai = Admoai.getInstance(),
                decisionRequest = decisionRequest,
                key = reloadKey // This key changes to trigger reload
            )
            
            when (adState) {
                is AdState.Idle -> {
                    Text(
                        text = "Ready to load ad with Compose",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                is AdState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Loading with rememberAdState...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                is AdState.Success -> {
                    Column {
                        Text(
                            text = "✅ Ad loaded with Compose!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Ads: ${adState.response.data?.size ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Display the ad using the existing AdCard component
                        val adData = adState.response.data?.firstOrNull()
                        if (adData != null) {
                            AdCard(
                                adData = adData,
                                onTrackImpression = { /* Handle impression tracking */ },
                                onAdClick = { /* Handle ad click */ }
                            )
                        }
                    }
                }
                
                is AdState.Error -> {
                    Column {
                        Text(
                            text = "❌ Error with Compose",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = adState.exception.message ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add reload button for Compose approach
            Button(
                onClick = { 
                    reloadKey++ // Increment key to trigger reload
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = adState !is AdState.Loading
            ) {
                Text("Load Ad with Compose")
            }
        }
    }
}

/**
 * Shows the traditional ViewModel approach for comparison
 */
@Composable
private fun ViewModelApproachDemo(
    viewModel: MainViewModel,
    decisionRequest: DecisionRequest
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val adResponse by viewModel.response.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    // Load ads using the provided decision request
    LaunchedEffect(decisionRequest) {
        viewModel.loadAds(decisionRequest)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Traditional ViewModel Approach",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Using ViewModel + StateFlow (current sample approach):",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loading with ViewModel...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                errorMessage != null -> {
                    Column {
                        Text(
                            text = "❌ Error from ViewModel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                adResponse != null -> {
                    val response = adResponse // Create local variable to avoid smart cast issues
                    Column {
                        Text(
                            text = "✅ Ad loaded via ViewModel!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Ads: ${response?.data?.size ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Display the ad using the existing AdCard component
                        val adData = response?.data?.firstOrNull()
                        if (adData != null) {
                            AdCard(
                                adData = adData,
                                onTrackImpression = { /* Handle impression tracking */ },
                                onAdClick = { /* Handle ad click */ }
                            )
                        }
                    }
                }
                
                else -> {
                    Text(
                        text = "Ready to load ad via ViewModel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { viewModel.loadAds(decisionRequest) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Load Ad via ViewModel")
            }
        }
    }
}
