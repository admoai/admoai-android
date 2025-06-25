package com.admoai.sample.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.components.AppInfoSection
import com.admoai.sample.ui.components.DataCollectionSection
import com.admoai.sample.ui.components.DeviceInfoSection
import com.admoai.sample.ui.components.PlacementSection
import com.admoai.sample.ui.components.TargetingSection
import com.admoai.sample.ui.components.UserSection

/**
 * Main configuration screen for building ad requests
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionRequestScreen(
    viewModel: MainViewModel,
    onShowRequest: () -> Unit = {},
    onRequestPreview: () -> Unit = {},
    onPlacementClick: () -> Unit = {},
    onGeoTargetingClick: () -> Unit = {},
    onLocationTargetingClick: () -> Unit = {},
    onCustomTargetingClick: () -> Unit = {},
    onTimezonePickerClick: () -> Unit = {},
    onComposeIntegrationClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val isLoading by viewModel.isLoading.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decision Request") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // View HTTP Request button
                TextButton(
                    onClick = {
                        viewModel.updateRequestJsonPreview()
                        onShowRequest()
                    }
                ) {
                    Text("View HTTP Request")
                }
                
                // Request and Preview button
                Button(
                    onClick = {
                        viewModel.loadAds()
                        onRequestPreview()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Request and Preview")
                }
            }
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Description text
                Text(
                    text = "This interface demonstrates how to build a decision request. The actual implementation will be handled by the SDK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                
                // Placement section
                PlacementSection(
                    viewModel = viewModel,
                    onPlacementClick = onPlacementClick
                )
                
                // Targeting section
                TargetingSection(
                    viewModel = viewModel,
                    onGeoTargetingClick = onGeoTargetingClick,
                    onLocationTargetingClick = onLocationTargetingClick,
                    onCustomTargetingClick = onCustomTargetingClick
                )
                
                // User section with integrated consent settings
                UserSection(
                    viewModel = viewModel,
                    onTimezonePickerClick = onTimezonePickerClick
                )
                
                // App info section (read only)
                AppInfoSection(viewModel = viewModel)
                
                // Device info section (read only)
                DeviceInfoSection(viewModel = viewModel)
                
                // Data collection section
                DataCollectionSection(viewModel = viewModel)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // SDK Compose Integration Demo
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
                            text = "SDK Compose Integration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "See how to use the SDK's declarative Compose API with rememberAdState",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = onComposeIntegrationClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("View Compose Demo")
                        }
                    }
                }
                
                // Add some bottom padding to ensure content doesn't get hidden behind the fixed bottom buttons
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(80.dp))
            }
        }
    )
}
