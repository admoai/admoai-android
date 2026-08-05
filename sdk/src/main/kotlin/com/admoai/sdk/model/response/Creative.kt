package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.AdvertiserOrDefaultSerializer
import com.admoai.sdk.serialization.ContentListSerializer
import com.admoai.sdk.serialization.CreativeJourneyOrNullSerializer
import com.admoai.sdk.serialization.CreativeMetadataOrNullSerializer
import com.admoai.sdk.serialization.TemplateInfoOrNullSerializer
import com.admoai.sdk.serialization.TrackingInfoOrDefaultSerializer
import com.admoai.sdk.serialization.VastDataOrNullSerializer
import com.admoai.sdk.serialization.VerificationResourceListSerializer
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
 * @property verificationScriptResources Open Measurement verification script resources for ad verification
 * @property journey Read-only Journey Takeover metadata; null for normal (non-Journey) ads
 */
@Serializable
data class Creative(
    @Serializable(with = ContentListSerializer::class)
    val contents: List<Content> = emptyList(),
    @Serializable(with = AdvertiserOrDefaultSerializer::class)
    val advertiser: Advertiser = Advertiser(),
    @Serializable(with = TemplateInfoOrNullSerializer::class)
    val template: TemplateInfo? = null,
    @Serializable(with = TrackingInfoOrDefaultSerializer::class)
    val tracking: TrackingInfo = TrackingInfo(),
    @Serializable(with = CreativeMetadataOrNullSerializer::class)
    val metadata: CreativeMetadata? = null,
    val delivery: String? = null,
    @Serializable(with = VastDataOrNullSerializer::class)
    val vast: VastData? = null,
    @Serializable(with = VerificationResourceListSerializer::class)
    val verificationScriptResources: List<VerificationScriptResource>? = null,
    @Serializable(with = CreativeJourneyOrNullSerializer::class)
    val journey: CreativeJourney? = null
)
