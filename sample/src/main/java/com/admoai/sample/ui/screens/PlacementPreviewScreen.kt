package com.admoai.sample.ui.screens

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.Creative
import com.admoai.sample.ui.MainViewModel

/**
 * Screen to display the placement-specific JSON preview and creative summary
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementPreviewScreen(
    viewModel: MainViewModel,
    placementKey: String,
    onNavigateBack: () -> Unit = {}
) {
    val requestJson by viewModel.requestJson.collectAsState()
    val response by viewModel.response.collectAsState()
    
    // Generate placement-specific JSON preview when navigating to this screen
    LaunchedEffect(placementKey) {
        viewModel.updatePlacementPreviewJson(placementKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Placement: $placementKey") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Preview section
                Text(
                    text = "Request JSON Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (!requestJson.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = requestJson,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No request data available for placement: $placementKey",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // Creative summary section
                Text(
                    text = "Creative Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                response?.data?.find { it.placement == placementKey }?.let { adData ->
                    adData.creatives.forEach { creative ->
                        CreativeSummaryCard(creative)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } ?: run {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No creative data available for placement: $placementKey. Request ads first to see creatives.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreativeSummaryCard(creative: Creative) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title (usually advertiser name)
            creative.advertiser.name?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Contents summary
            Text(
                text = "Contents:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            creative.contents.forEach { content ->
                val contentType = content.type.toString()
                val contentValue = when (content.type) {
                    ContentType.TEXT, ContentType.TEXTAREA, ContentType.MARKDOWN -> "Text: ${content.value}"
                    ContentType.IMAGE, ContentType.IMAGES -> "Image URL: ${content.value}"
                    ContentType.URL -> "URL: ${content.value}"
                    ContentType.COLOR -> "Color: ${content.value}"
                    else -> "Value: ${content.value}"
                }
                Text(
                    text = "• $contentType: $contentValue",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tracking info
            Text(
                text = "Tracking:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            val impressions = creative.tracking.impressions ?: emptyList()
            Text(
                text = "• ${impressions.size} impression URLs",
                style = MaterialTheme.typography.bodyMedium
            )
            
            val clicks = creative.tracking.clicks ?: emptyList()
            Text(
                text = "• ${clicks.size} click URLs",
                style = MaterialTheme.typography.bodyMedium
            )
            
            val customEvents = creative.tracking.custom ?: emptyList()
            Text(
                text = "• ${customEvents.size} custom event URLs",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
