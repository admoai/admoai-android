package com.admoai.sample.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Screen to display the formatted JSON of the Decision Request
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestPreviewScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    // Use getHttpRequest() for complete HTTP representation including headers
    val requestJson by viewModel.requestJson.collectAsState() // Keep this for reactivity and to check if empty
    val httpRequestText = viewModel.getHttpRequest() // Get formatted HTTP request
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Preview") },
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
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (!requestJson.isNullOrEmpty()) {
                // Show JSON with monospace font in a scrollable column
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = httpRequestText,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // Show placeholder if no request is available
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "No request data available. Return to the main screen and try again.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
