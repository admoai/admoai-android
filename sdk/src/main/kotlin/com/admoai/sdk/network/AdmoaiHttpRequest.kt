package com.admoai.sdk.network

import kotlinx.serialization.Serializable

/**
 * HTTP request details for debugging and inspection.
 */
@Serializable 
data class AdmoaiHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? // JSON string for POST/PUT, null for GET
)
