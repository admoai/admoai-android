package com.admoai.sample.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.model.DestinationItem
import kotlin.math.roundToInt

/**
 * Screen for adding and managing destination targets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationTargetingScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val destinationTargets by viewModel.destinationTargets.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Destination Targeting") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Clear all destinations
                    if (destinationTargets.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearDestinationTargets() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All Destinations"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Description text
            Text(
                text = "Add latitude, longitude, and minimum confidence threshold to target predicted destinations. Confidence values range from 0.0 to 1.0. You can add multiple destinations. For demo purposes, you can generate random coordinates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            
            // Add random destination button
            Button(
                onClick = { viewModel.addRandomDestination() },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Add Random Destination")
            }
            
            if (destinationTargets.isEmpty()) {
                // Show empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No destinations added yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // List of destination targets
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(destinationTargets) { destinationItem ->
                        DestinationTargetItem(
                            destination = destinationItem,
                            onRemove = {
                                viewModel.updateDestinationTargets(
                                    destinationTargets.filter { it != destinationItem }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationTargetItem(
    destination: DestinationItem,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
            
            // Destination info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Lat: ${(destination.latitude * 1000).roundToInt() / 1000.0}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "Lng: ${(destination.longitude * 1000).roundToInt() / 1000.0}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "Confidence: ${(destination.minConfidence * 100).roundToInt() / 100.0}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Remove button
            TextButton(
                onClick = onRemove,
                modifier = Modifier.width(100.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("Remove")
            }
        }
    }
}
