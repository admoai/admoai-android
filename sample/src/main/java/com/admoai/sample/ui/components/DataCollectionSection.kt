package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Section to configure data collection toggles
 */
@Composable
fun DataCollectionSection(viewModel: MainViewModel) {
    val collectAppData by viewModel.collectAppData.collectAsState()
    val collectDeviceData by viewModel.collectDeviceData.collectAsState()
    
    SectionContainer(title = "Data Collection") {
        // App data collection toggle
        SectionRow(
            icon = Icons.Outlined.AppShortcut,
            label = "Collect App Data",
            value = {
                Switch(
                    checked = collectAppData,
                    onCheckedChange = { viewModel.setCollectAppData(it) }
                )
            }
        )
        
        // Device data collection toggle
        SectionRow(
            icon = Icons.Outlined.Smartphone,
            label = "Collect Device Data",
            value = {
                Switch(
                    checked = collectDeviceData,
                    onCheckedChange = { viewModel.setCollectDeviceData(it) }
                )
            }
        )
        
        // Help text
        Text(
            text = "These toggles control whether the SDK will include app and device information in the ad request. " +
                  "When disabled, the corresponding fields will be omitted from the request JSON.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
