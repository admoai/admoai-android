package com.admoai.sample.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Expandable consent section with GDPR toggle
 */
@Composable
fun ConsentSection(viewModel: MainViewModel) {
    val gdprConsent by viewModel.gdprConsent.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    
    // Animate the expand/collapse arrow rotation
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "arrow_rotation"
    )
    
    SectionContainer(title = "Consent") {
        // Section header row that can be clicked to expand/collapse
        Row(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Consent Settings",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(arrowRotation)
            )
        }
        
        // Expandable content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                // GDPR Consent row with shield icon
                SectionRow(
                    icon = Icons.Default.Security,
                    label = "GDPR Consent",
                    value = {
                        Switch(
                            checked = gdprConsent,
                            onCheckedChange = { viewModel.setGdprConsent(it) }
                        )
                    }
                )
                
                // Help text about consent
                Text(
                    text = "GDPR consent toggles whether user consent information is sent with ad requests. " +
                          "When enabled, requests will include consent data in compliance with EU regulations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
