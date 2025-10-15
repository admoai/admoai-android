package com.admoai.sample.ui.screens.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem

/**
 * Free Minutes placement preview screen
 * 
 * Placeholder screen for the Free Minutes placement.
 * UX/UI implementation is in progress.
 */
@Composable
fun FreeMinutesPreviewScreen(
    viewModel: MainViewModel,
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onThemeToggle: () -> Unit,
    onAdClick: (AdData) -> Unit,
    onTrackEvent: (String, String) -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }

    // Handle refresh button click
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefreshClick()
        }
    }
    
    // Reset isRefreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Navigation bar
            PreviewNavigationBar(
                placement = placement,
                onBackClick = onBackClick,
                onDetailsClick = onDetailsClick,
                onRefreshClick = { isRefreshing = true },
                isRefreshing = isRefreshing
            )
            
            // Placeholder content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Free Minutes",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Placement preview coming soon",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (adData != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ad data loaded successfully",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
