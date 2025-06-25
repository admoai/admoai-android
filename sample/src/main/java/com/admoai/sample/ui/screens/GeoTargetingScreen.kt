package com.admoai.sample.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.model.GeoTargetItem
import com.admoai.sample.ui.MainViewModel

/**
 * Screen for selecting geo targeting options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoTargetingScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val geoTargets by viewModel.geoTargets.collectAsState()

    // Create a working copy of selections
    val selections = geoTargets.map { it.copy() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geo Targeting") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                text = "This is a sample list of cities. The actual implementation must use the correct Geoname IDs for the cities and countries you want to target.\n\nThe user's IP address automatically determines the Geoname ID for their city and country, but setting geo-targeting will override these values.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            
            // List of geo targets
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize()
            ) {
                items(selections) { geoTarget ->
                    GeoTargetItem(
                        geoTarget = geoTarget,
                        onToggleSelection = { selected ->
                            // Update the selection
                            viewModel.updateGeoTargets(
                                geoTargets.map { 
                                    if (it.id == geoTarget.id) it.copy(isSelected = selected) 
                                    else it 
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GeoTargetItem(
    geoTarget: GeoTargetItem,
    onToggleSelection: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = geoTarget.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ID: ${geoTarget.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Checkbox(
            checked = geoTarget.isSelected,
            onCheckedChange = { selected ->
                onToggleSelection(selected)
            }
        )
    }
}
