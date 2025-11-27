package com.admoai.sample.config

/**
 * Centralized configuration for the Admoai Sample App.
 */
object AppConfig {
    /**
     * Base URL for the Admoai Decision API.
     */
    const val API_BASE_URL = "https://api.mock.admoai.com"
    
    /**
     * API version header value
     * 
     * Optional: Specifies the API version for request/response format compatibility.
     * The Decision API uses this to maintain backward compatibility across versions.
     * 
     * Format: YYYY-MM-DD (e.g., "2025-11-01")
     */
    const val API_VERSION = "2025-11-01"
    
    /**
     * Enable SDK logging for debugging
     * 
     * Set to false in production builds
     */
    const val ENABLE_LOGGING = true
}
