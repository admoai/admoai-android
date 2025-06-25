package com.admoai.sample.ui.model

import com.admoai.sdk.model.response.Creative

/**
 * UI model representing content for an advertisement
 * Used to simplify adapter pattern between SDK model and UI components
 */
data class AdContent(
    val headline: String,
    val description: String,
    val imageUrl: String? = null,
    val callToAction: String = "Learn More",
    val color: String? = null,
    val ctaTextColor: String? = null,
    val clickThroughUrl: String? = null
) {
    companion object {
        /**
         * Extract text content from a Creative by key
         */
        fun extractText(creative: Creative, key: String): String? {
            // Find content with matching key
            val content = creative.contents.find { it.key == key }
            // Check if it exists and convert value to string
            return content?.value?.toString()?.trim('"') // Trim quotes that might be present in JSON string
        }
        
        /**
         * Extract text content from a Creative by key
         * Alias for extractText to match naming convention in carousel implementation
         */
        fun extractTextContent(creative: Creative?, key: String): String? {
            return creative?.let { extractText(it, key) }
        }
        
        /**
         * Extract URL content from a Creative by key
         * Used specifically for URL type values
         */
        fun extractUrlContent(creative: Creative?, key: String): String? {
            return creative?.let { extractText(it, key) }
        }
    }
}
