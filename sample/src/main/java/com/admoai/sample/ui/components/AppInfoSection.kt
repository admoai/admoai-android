package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Section to display app information (read-only)
 */
@Composable
fun AppInfoSection(viewModel: MainViewModel) {
    SectionContainer(title = "App") {
        // App Name
        SectionRow(
            icon = Icons.Outlined.Apps,
            label = "Name",
            value = { 
                Text(
                    text = viewModel.appName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // App Version
        SectionRow(
            icon = Icons.Outlined.Apps,
            label = "Version",
            value = { 
                Text(
                    text = "${viewModel.appVersion} (${viewModel.appBuild})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // App Bundle ID
        SectionRow(
            icon = Icons.Outlined.Apps,
            label = "Bundle ID",
            value = { 
                Text(
                    text = viewModel.appIdentifier,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // App Language
        SectionRow(
            icon = Icons.Outlined.Language,
            label = "Language",
            value = { 
                Text(
                    text = viewModel.appLanguage ?: "unknown",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // Help text
        Text(
            text = "The app information is collected automatically by the SDK to enhance ad targeting and relevance. " +
                  "This can be disabled via the Data Collection section below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
