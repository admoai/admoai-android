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
import com.admoai.sample.ui.model.LocationItem
import kotlin.math.roundToInt

/**
 * Screen for adding and managing location targets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTargetingScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val locationTargets by viewModel.locationTargets.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Targeting") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Clear all locations
                    if (locationTargets.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearLocationTargets() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All Locations"
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
                text = "Add latitude and longitude coordinates to target specific locations. You can add multiple coordinates to target different areas. For demo purposes, you can generate random coordinates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            
            // Add random location button
            Button(
                onClick = { viewModel.addRandomLocation() },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Add Random Location")
            }
            
            if (locationTargets.isEmpty()) {
                // Show empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No locations added yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // List of location targets
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(locationTargets) { locationItem ->
                        LocationTargetItem(
                            location = locationItem,
                            onRemove = {
                                viewModel.updateLocationTargets(
                                    locationTargets.filter { it != locationItem }
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
fun LocationTargetItem(
    location: LocationItem,
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
            
            // Location info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Lat: ${(location.latitude * 1000).roundToInt() / 1000.0}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "Lng: ${(location.longitude * 1000).roundToInt() / 1000.0}",
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
