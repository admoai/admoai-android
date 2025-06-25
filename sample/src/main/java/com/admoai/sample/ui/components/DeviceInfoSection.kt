package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Section to display device information (read-only)
 */
@Composable
fun DeviceInfoSection(viewModel: MainViewModel) {
    SectionContainer(title = "Device") {
        // Device ID
        SectionRow(
            icon = Icons.Outlined.Smartphone,
            label = "ID",
            value = { 
                Text(
                    text = viewModel.deviceId,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // Device Model
        SectionRow(
            icon = Icons.Outlined.Smartphone,
            label = "Model",
            value = { 
                Text(
                    text = "${viewModel.deviceManufacturer} ${viewModel.deviceModel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // Device OS
        SectionRow(
            icon = Icons.Outlined.DevicesOther,
            label = "OS",
            value = { 
                Text(
                    text = "${viewModel.deviceOs} ${viewModel.deviceOsVersion}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // Device Timezone
        SectionRow(
            icon = Icons.Outlined.Schedule,
            label = "Timezone",
            value = { 
                Text(
                    text = viewModel.deviceTimezone,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // Device Language
        SectionRow(
            icon = Icons.Outlined.Language,
            label = "Language",
            value = { 
                Text(
                    text = viewModel.deviceLanguage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // Help text
        Text(
            text = "The device information is collected automatically by the SDK to enhance ad targeting and relevance. " +
                  "This can be disabled via the Data Collection section below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
