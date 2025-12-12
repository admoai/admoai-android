package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * Advertisement creative returned from the AdMoai API.
 *
 * @property contents Content elements that make up the creative
 * @property advertiser Advertiser information
 * @property template Template information with key and style
 * @property tracking Tracking URLs for impressions, clicks, and custom events
 * @property metadata Additional creative metadata
 * @property delivery Delivery method for video ads ("vast_tag", "vast_xml", "json")
 * @property vast VAST data containing tag URL or XML content for video ads
 */
@Serializable
data class Creative(
    val contents: List<Content>,
    val advertiser: Advertiser,
    val template: TemplateInfo? = null, 
    val tracking: TrackingInfo,
    val metadata: CreativeMetadata? = null,
    val delivery: String? = null,
    val vast: VastData? = null
)
