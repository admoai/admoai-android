package com.admoai.sample.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.model.PlacementItem

/**
 * Screen to pick a placement from the available options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementPickerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val placements = viewModel.availablePlacements
    val currentPlacement by viewModel.placementKey.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Placement") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(placements) { placement ->
                PlacementRow(
                    placement = placement,
                    isSelected = placement.id == currentPlacement,
                    onClick = {
                        viewModel.setPlacementKey(placement.id)
                        onNavigateBack()
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun PlacementRow(
    placement: PlacementItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon based on placement
        // Map iOS SF Symbol names to Material icons
        val icon = when (placement.id) {
            "home" -> Icons.Default.Home                      // Home placement
            "search" -> Icons.Default.Search                  // Search placement
            "menu" -> Icons.Default.Menu                      // Menu placement
            "promotions" -> Icons.Default.LocalOffer          // Promotions placement
            "vehicleSelection" -> Icons.Default.DirectionsCar // Vehicle Selection placement
            "rideSummary" -> Icons.AutoMirrored.Filled.ReceiptLong       // Ride Summary placement
            "waiting" -> Icons.Default.Timer                  // Waiting placement
            "freeMinutes" -> Icons.Default.CardGiftcard       // Free Minutes placement
            "invalidPlacement" -> Icons.Default.Warning       // Invalid Placement
            else -> Icons.Default.Home
        }
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        // Placement name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = placement.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = placement.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Check icon for selected item
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
