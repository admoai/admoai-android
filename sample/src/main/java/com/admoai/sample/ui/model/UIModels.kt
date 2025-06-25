package com.admoai.sample.ui.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * UI model for a placement in the example app
 * Combines properties from both previous implementations
 * to address compilation errors across screens
 */
data class PlacementItem(
    // Properties from both implementations
    val id: String,
    val name: String,
    val key: String,
    val title: String,
    val icon: String,
    val count: Int? = 1,
    val description: String = ""
)

/**
 * UI model for a location in the targeting UI
 */
data class LocationItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double
)

/**
 * UI model for custom targeting key-value pair
 */
data class CustomTargetItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val key: String,
    val value: String
)

/**
 * UI model for a city in geo targeting
 */
data class GeoTargetItem(
    val id: String,
    val name: String,
    val isSelected: Boolean = false
)

/**
 * Tabs for the response details screen
 */
enum class DetailsTab {
    CONTENTS,
    INFO,
    TRACKING,
    VALIDATION,
    JSON
}

/**
 * Sections in the main decision request screen
 */
enum class ConfigSection {
    PLACEMENT,
    TARGETING,
    USER,
    APP,
    DEVICE,
    DATA_COLLECTION
}
