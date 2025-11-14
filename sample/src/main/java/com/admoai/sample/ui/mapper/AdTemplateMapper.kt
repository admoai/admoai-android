package com.admoai.sample.ui.mapper

import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.CreativeMetadata

/**
 * Helper class for mapping ad template keys and styles to appropriate UI components
 */
object AdTemplateMapper {
    
    /**
     * Ad template type constants
     */
    object TemplateType {
        const val WIDE_WITH_COMPANION = "wideWithCompanion"
        const val IMAGE_WITH_TEXT = "imageWithText"
        const val TEXT_ONLY = "textOnly"
        const val CAROUSEL_3_SLIDES = "carousel3Slides"
        const val WIDE_IMAGE_ONLY = "wideImageOnly"
        const val STANDARD = "standard"
        const val NORMAL_VIDEOS = "normal_videos"
        const val REWARD_VIDEOS = "reward_videos"
    }
    
    /**
     * Ad template style constants
     */
    object TemplateStyle {
        // Styles for wideWithCompanion template (home placement)
        const val WIDE_IMAGE_ONLY = "wideImageOnly"
        const val IMAGE_LEFT = "imageLeft"
        
        // Styles for imageWithText template (search placement)
        const val IMAGE_RIGHT = "imageRight"
    }
    
    /**
     * Get the template key for a given ad
     * 
     * Extracts the template key from the Creative.template field if available.
     * For the "home" placement, falls back to "wideWithCompanion" if not found.
     *
     * @param adData The ad data to extract template key from
     * @return The template key or null if not found
     */
    fun getTemplateKey(adData: AdData?): String? {
        // Extract first creative if available
        val firstCreative = adData?.creatives?.firstOrNull()
        
        // Get template key from the new template field
        return firstCreative?.template?.key ?: run {
            // Fallback for backward compatibility
            "wideWithCompanion"
        }
    }
    
    /**
     * Get the template style for a given ad
     * This extracts the style from the template field or falls back to metadata
     */
    fun getTemplateStyle(adData: AdData?): String? {
        // Extract first creative if available
        val firstCreative = adData?.creatives?.firstOrNull()
        
        // First try to get style from the template field, then fall back to metadata
        return firstCreative?.template?.style ?: firstCreative?.metadata?.style
    }
    
    /**
     * Check if an ad has a specific template key
     */
    fun hasTemplateKey(adData: AdData?, key: String): Boolean {
        return getTemplateKey(adData) == key
    }
    
    /**
     * Check if an ad has a specific template style
     */
    fun hasTemplateStyle(adData: AdData?, style: String): Boolean {
        return getTemplateStyle(adData) == style
    }
    
    /**
     * Check if an ad uses the wideImageOnly style
     */
    fun isWideImageOnlyStyle(adData: AdData?): Boolean {
        return hasTemplateStyle(adData, TemplateStyle.WIDE_IMAGE_ONLY)
    }
    
    /**
     * Check if an ad uses the imageLeft style
     */
    fun isImageLeftStyle(adData: AdData?): Boolean {
        return hasTemplateStyle(adData, TemplateStyle.IMAGE_LEFT)
    }
    
    /**
     * Check if an ad uses the imageRight style
     */
    fun isImageRightStyle(adData: AdData?): Boolean {
        return hasTemplateStyle(adData, TemplateStyle.IMAGE_RIGHT)
    }
    
    /**
     * Check if an ad has the imageWithText template
     */
    fun isImageWithTextTemplate(adData: AdData): Boolean {
        return getTemplateKey(adData) == TemplateType.IMAGE_WITH_TEXT
    }
    
    /**
     * Check if an ad has the wideImageOnly template
     */
    fun isWideImageOnlyTemplate(adData: AdData): Boolean {
        return getTemplateKey(adData) == TemplateType.WIDE_IMAGE_ONLY
    }
    
    /**
     * Check if the ad is using the textOnly template
     */
    fun isTextOnlyTemplate(adData: AdData?): Boolean {
        return hasTemplateKey(adData, TemplateType.TEXT_ONLY)
    }
    
    /**
     * Check if an ad is using the carousel3Slides template
     */
    fun isCarouselTemplate(adData: AdData?): Boolean {
        return hasTemplateKey(adData, TemplateType.CAROUSEL_3_SLIDES)
    }
    
    /**
     * Check if an ad is using the standard template
     */
    fun isStandardTemplate(adData: AdData?): Boolean {
        return hasTemplateKey(adData, TemplateType.STANDARD)
    }
    
    /**
     * Check if an ad is using the normal_videos template
     * This checks for either:
     * 1. Explicit template.key = "normal_videos" in response, OR
     * 2. Presence of companion content fields (companionHeadline, companionSubtext, companionCta)
     */
    fun isNormalVideosTemplate(adData: AdData?): Boolean {
        // First check explicit template
        if (hasTemplateKey(adData, TemplateType.NORMAL_VIDEOS)) {
            return true
        }
        
        // Fall back to checking for companion content presence
        val creative = adData?.creatives?.firstOrNull() ?: return false
        val hasCompanionHeadline = creative.contents?.any { it.key == "companionHeadline" } == true
        val hasCompanionCta = creative.contents?.any { it.key == "companionCta" } == true
        
        // If it has companion content, treat it as normal_videos
        return hasCompanionHeadline || hasCompanionCta
    }
    
    /**
     * Check if an ad is using the reward_videos template
     * This checks for either:
     * 1. Explicit template.key = "reward_videos" in response, OR
     * 2. Presence of endcard content fields (companionEndcardHeadline, companionEndcardCta, overlayAtPercentage)
     */
    fun isRewardVideosTemplate(adData: AdData?): Boolean {
        // First check explicit template
        if (hasTemplateKey(adData, TemplateType.REWARD_VIDEOS)) {
            return true
        }
        
        // Fall back to checking for reward video endcard content presence
        val creative = adData?.creatives?.firstOrNull() ?: return false
        val hasEndcardHeadline = creative.contents?.any { it.key == "companionEndcardHeadline" } == true
        val hasEndcardCta = creative.contents?.any { it.key == "companionEndcardCta" } == true
        val hasOverlayPercentage = creative.contents?.any { it.key == "overlayAtPercentage" } == true
        
        // If it has endcard content, treat it as reward_videos
        return (hasEndcardHeadline || hasEndcardCta) && hasOverlayPercentage
    }
    
    /**
     * Get the click-through URL for an ad creative
     */
    fun getClickThroughUrl(creative: Creative?): String? {
        return creative?.contents?.find { it.key == "clickThroughURL" }?.value?.toString()?.removeSurrounding("\"")
    }
    
    /**
     * Extract formatted string content for a key from ad creative
     * 
     * @param creative The creative to extract from
     * @param key The content key to look for
     * @return Formatted string value or null if not found
     */
    fun getContentValue(creative: Creative?, key: String): String? {
        return creative?.contents?.find { it.key == key }?.value?.toString()?.removeSurrounding("\"")
    }
    
    /**
     * Determine if this placement supports clickthrough
     * (Currently supports: home, vehicleSelection, rideSummary, promotions, waiting)
     */
    fun supportsClickthrough(placement: String): Boolean {
        return placement == "home" || placement == "vehicleSelection" || placement == "rideSummary" || placement == "promotions" || placement == "waiting"
    }
}
