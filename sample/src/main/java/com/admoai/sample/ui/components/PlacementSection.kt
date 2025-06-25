package com.admoai.sample.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Section to configure the ad placement
 */
@Composable
fun PlacementSection(
    viewModel: MainViewModel,
    onPlacementClick: () -> Unit = {}
) {
    val placementKey by viewModel.placementKey.collectAsState()
    
    SectionContainer(title = "Placement") {
        // Placement key row (clickable to open picker)
        SectionRow(
            icon = Icons.Outlined.Key,
            label = "Key",
            value = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = viewModel.availablePlacements.firstOrNull { 
                            it.key == placementKey 
                        }?.name ?: placementKey,
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
            onClick = onPlacementClick
        )
        
        // Format row (read-only)
        SectionRow(
            icon = Icons.Outlined.ViewModule,
            label = "Format",
            value = { Text("Native", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        
        // Help text
        Text(
            text = "This demo uses a single placement object, but you can include multiple ones. " +
                  "For each, you can specify the number of creatives to return and filter by advertiser and template.\n" +
                  "Currently, AdMoai supports only the native format.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Container for a section with iOS-style header and content
 */
@Composable
fun SectionContainer(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        // iOS-style section header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            
            // Light divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        
        // Section content with a more subtle background
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.5.dp
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * Generic row for a section with icon, label and value
 */
@Composable
fun SectionRow(
    icon: Any,
    label: String,
    value: @Composable () -> Unit,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Row icon and label
        when (icon) {
            is androidx.compose.ui.graphics.vector.ImageVector -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        
        // Spacer
        Column(modifier = Modifier.weight(1f)) {}
        
        // Value content
        value()
    }
}
