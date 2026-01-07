package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Section to configure targeting options (geo, location, custom)
 */
@Composable
fun TargetingSection(
    viewModel: MainViewModel,
    onGeoTargetingClick: () -> Unit = {},
    onLocationTargetingClick: () -> Unit = {},
    onDestinationTargetingClick: () -> Unit = {},
    onCustomTargetingClick: () -> Unit = {}
) {
    val geoTargets by viewModel.geoTargets.collectAsState()
    val locationTargets by viewModel.locationTargets.collectAsState()
    val destinationTargets by viewModel.destinationTargets.collectAsState()
    val customTargets by viewModel.customTargets.collectAsState()
    
    // Count selected geo targets
    val selectedGeoCount = geoTargets.count { it.isSelected }
    
    SectionContainer(title = "Targeting") {
        // Add descriptive text explaining targeting purpose
        Text(
            text = "Targeting allows you to specify criteria to filter the creatives returned by Admoai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        // Geo Targeting row
        SectionRow(
            icon = Icons.Outlined.Public,
            label = "Geo Targeting",
            value = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selectedGeoCount == 0) "None" else "$selectedGeoCount cities",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = onGeoTargetingClick
        )
        
        // Location Targeting row
        SectionRow(
            icon = Icons.Outlined.LocationOn,
            label = "Location Targeting",
            value = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (locationTargets.isEmpty()) "None" else "${locationTargets.size} locations",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = onLocationTargetingClick
        )
        
        // Destination Targeting row
        SectionRow(
            icon = Icons.Outlined.LocationOn,
            label = "Destination Targeting",
            value = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (destinationTargets.isEmpty()) "None" else "${destinationTargets.size} destinations",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = onDestinationTargetingClick
        )
        
        // Custom Targeting row
        SectionRow(
            icon = Icons.Outlined.Tune,
            label = "Custom Targeting",
            value = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (customTargets.isEmpty()) "None" else "${customTargets.size} pairs",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = onCustomTargetingClick
        )
    }
}
