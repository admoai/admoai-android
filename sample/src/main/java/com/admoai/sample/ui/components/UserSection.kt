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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * Section to configure user information
 */
@Composable
fun UserSection(
    viewModel: MainViewModel,
    onTimezonePickerClick: () -> Unit = {}
) {
    val userId by viewModel.userId.collectAsState()
    val userIp by viewModel.userIp.collectAsState()
    val userTimezone by viewModel.userTimezone.collectAsState()
    val gdprConsent by viewModel.gdprConsent.collectAsState()
    
    SectionContainer(title = "User") {
        // User ID row
        SectionRow(
            icon = Icons.Outlined.Person,
            label = "ID",
            value = {
                var text by remember(userId) { mutableStateOf(userId) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { 
                        text = it
                        viewModel.setUserId(it)
                    },
                    singleLine = true,
                    label = { Text("User ID") }
                )
            }
        )
        
        // User IP row
        SectionRow(
            icon = Icons.Outlined.Public,
            label = "IP",
            value = {
                var text by remember(userIp) { mutableStateOf(userIp) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { 
                        text = it
                        viewModel.setUserIp(it)
                    },
                    singleLine = true,
                    label = { Text("User IP") }
                )
            }
        )
        
        // User Timezone row
        SectionRow(
            icon = Icons.Outlined.AccessTime,
            label = "Timezone",
            value = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userTimezone,
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
            onClick = onTimezonePickerClick
        )
        
        // Consent Settings row with expandable content
        var isConsentExpanded by remember { mutableStateOf(false) }
        
        // Animate the expand/collapse arrow rotation
        val arrowRotation by animateFloatAsState(
            targetValue = if (isConsentExpanded) 90f else 0f,
            label = "arrow_rotation"
        )
        
        // Clickable row to expand/collapse consent settings
        Row(
            modifier = Modifier
                .clickable { isConsentExpanded = !isConsentExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Consent Settings",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isConsentExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(arrowRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Expandable consent settings content
        AnimatedVisibility(
            visible = isConsentExpanded,
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
                
                // GDPR consent help text
                Text(
                    text = "GDPR consent toggles whether user consent information is sent with ad requests. " +
                          "When enabled, requests will include consent data in compliance with EU regulations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 8.dp)
                )
            }
        }
        
        // Help text
        Text(
            text = "The user ID and IP address enable frequency capping and geo-targeting respectively. " +
                  "The timezone enables day/hour parting for time-based ad delivery. " +
                  "GDPR consent must be enabled to serve ads with frequency capping.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
