package com.admoai.sample.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.CreativeMetadata
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sample.ui.MainViewModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.ui.platform.LocalUriHandler

/**
 * Screen that displays the response details after requesting ads
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponseDetailsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val decisionResponse by viewModel.decisionResponse.collectAsState()
    val formattedResponse by viewModel.formattedResponse.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    val tabs = listOf("Contents", "Info", "Tracking", "Validation", "JSON")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Response Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
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
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { 
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            
            when (selectedTabIndex) {
                0 -> ContentsTab(decisionResponse)
                1 -> InfoTab(decisionResponse)
                2 -> TrackingTab(decisionResponse)
                3 -> ValidationTab(decisionResponse)
                4 -> JsonResponseTab(formattedResponse)
            }
        }
    }
}

@Composable
fun JsonResponseTab(formattedResponse: String) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = formattedResponse,
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ContentsTab(decisionResponse: DecisionResponse?) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Creative Contents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (decisionResponse != null) {
                val dataList = decisionResponse.data
                if (dataList != null && dataList.isNotEmpty()) {
                    dataList.forEach { ad ->
                        if (ad.creatives.isNotEmpty()) {
                            val creative = ad.creatives.firstOrNull()
                            if (creative != null && creative.contents.isNotEmpty()) {
                                creative.contents.forEach { content ->
                                    ContentItem(content)
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            } else {
                                Text("No content available in the creative")
                            }
                        } else {
                            Text("No creative available in the ad")
                        }
                    }
                } else {
                    Text("No ad data available in the response")
                }
            } else {
                Text("No response data available")
            }
        }
    }
}

@Composable
private fun ContentItem(content: Content) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = content.key ?: "Unknown key",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = content.type?.name ?: "Unknown type",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val contentValue = (content.value as? JsonPrimitive)?.contentOrNull
            val isImage = content.type == ContentType.IMAGE || 
                         (content.key?.contains("image", ignoreCase = true) == true) ||
                         (contentValue?.let { url ->
                             url.contains("http") && (url.contains(".jpg") || url.contains(".jpeg") || 
                             url.contains(".png") || url.contains(".gif") || url.contains(".webp"))
                         } == true)
            
            when {
                isImage && !contentValue.isNullOrBlank() -> {
                    SubcomposeAsyncImage(
                        model = contentValue,
                        contentDescription = "Content image for ${content.key}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val state = painter.state
                        when (state) {
                            is coil.compose.AsyncImagePainter.State.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            is coil.compose.AsyncImagePainter.State.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow, // Using available icon as placeholder
                                            contentDescription = "Image placeholder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Image",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                    
                    // Show URL below image for reference
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = contentValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textDecoration = TextDecoration.Underline,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2
                    )
                }
                content.type == ContentType.COLOR -> {
                    val colorValue = (content.value as? JsonPrimitive)?.contentOrNull ?: "#000000"
                    val color = try {
                        Color(android.graphics.Color.parseColor(colorValue))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = colorValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                content.type == ContentType.TEXT -> {
                    Text(
                        text = (content.value as? JsonPrimitive)?.contentOrNull ?: "No text value",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                content.type == ContentType.URL -> {
                    val urlValue = (content.value as? JsonPrimitive)?.contentOrNull ?: "No URL"
                    Text(
                        text = urlValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textDecoration = TextDecoration.Underline
                    )
                }
                else -> {
                    Text(
                        text = (content.value as? JsonPrimitive)?.contentOrNull ?: "No value",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun InfoTab(decisionResponse: DecisionResponse?) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Ad Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (decisionResponse != null) {
                // Response level info
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Response Info",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        InfoRow("Success", decisionResponse.success.toString())
                        InfoRow("Ads Count", "${decisionResponse.data?.size ?: 0}")
                        InfoRow("Errors Count", "${decisionResponse.errors?.size ?: 0}")
                        InfoRow("Warnings Count", "${decisionResponse.warnings?.size ?: 0}")
                    }
                }
                
                // Ad level info
                decisionResponse.data?.forEachIndexed { index, ad ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Ad ${index + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            InfoRow("Placement", ad.placement)
                            InfoRow("Creatives Count", "${ad.creatives.size}")
                            
                            // Creative info
                            ad.creatives.forEachIndexed { creativeIndex, creative ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = "Creative ${creativeIndex + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                InfoRow("Contents Count", "${creative.contents.size}")
                                InfoRow("Advertiser", creative.advertiser.name ?: "Unknown")
                                
                                // Display template information using the new fields
                                // Using safe calls to avoid smart cast issues across module boundaries
                                val templateKey = creative.template?.key
                                val templateStyle = creative.template?.style
                                
                                if (templateKey != null) {
                                    InfoRow("Template Key", templateKey)
                                    if (templateStyle != null) {
                                        InfoRow("Template Style", templateStyle)
                                    }
                                } else {
                                    // Fallback to metadata template ID if template object isn't available
                                    InfoRow("Template ID", creative.metadata.templateId)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("No response data available")
            }
        }
    }
}

@Composable
fun InfoRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun TrackingTab(decisionResponse: DecisionResponse?) {
    val uriHandler = LocalUriHandler.current
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Tracking URLs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (decisionResponse != null) {
                val dataList = decisionResponse.data
                if (dataList != null && dataList.isNotEmpty()) {
                    dataList.forEach { ad ->
                        ad.creatives.forEachIndexed { creativeIndex, creative ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Creative ${creativeIndex + 1} - ${ad.placement}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    // Impression tracking
                                    creative.tracking.impressions?.forEach { trackingDetail ->
                                        TrackingUrlItem(
                                            label = "Impression (${trackingDetail.key})",
                                            url = trackingDetail.url,
                                            onClick = { uriHandler.openUri(trackingDetail.url) }
                                        )
                                    }
                                    
                                    // Click tracking
                                    creative.tracking.clicks?.forEach { trackingDetail ->
                                        TrackingUrlItem(
                                            label = "Click (${trackingDetail.key})",
                                            url = trackingDetail.url,
                                            onClick = { uriHandler.openUri(trackingDetail.url) }
                                        )
                                    }
                                    
                                    // Custom tracking
                                    creative.tracking.custom?.forEach { trackingDetail ->
                                        TrackingUrlItem(
                                            label = "Custom: ${trackingDetail.key}",
                                            url = trackingDetail.url,
                                            onClick = { uriHandler.openUri(trackingDetail.url) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("No tracking data available")
                }
            } else {
                Text("No response data available")
            }
        }
    }
}

@Composable
fun TrackingUrlItem(
    label: String,
    url: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Open URL",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun ValidationTab(decisionResponse: DecisionResponse?) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Validation Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (decisionResponse != null) {
                // Success status
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (decisionResponse.success) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Request Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = if (decisionResponse.success) "Success" else "Failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (decisionResponse.success) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Errors
                val errorsList = decisionResponse.errors
                if (errorsList != null && errorsList.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Errors",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            errorsList.forEach { error ->
                                Text(
                                    text = "• $error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                // Warnings
                val warningsList = decisionResponse.warnings
                if (warningsList != null && warningsList.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Warnings",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            warningsList.forEach { warning ->
                                Text(
                                    text = "• $warning",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                // Response metadata and summary
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Response Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Data Count: ${decisionResponse.data?.size ?: 0}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        decisionResponse.metadata?.let { _ ->
                            Text(
                                text = "Metadata: Available",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Text("No validation data available")
            }
        }
    }
}