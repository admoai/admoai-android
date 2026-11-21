package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.AdvertiserOrDefaultSerializer
import com.admoai.sdk.serialization.ContentListSerializer
import com.admoai.sdk.serialization.CreativeJourneyOrNullSerializer
import com.admoai.sdk.serialization.CreativeMetadataOrNullSerializer
import com.admoai.sdk.serialization.InteractiveExperienceSerializer
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
 * @property interactiveExperience What a tap on this creative opens when it is more than a link
 *   (per creative; null = not an interactive creative). [InteractiveExperience.Unsupported] means
 *   this SDK cannot open it: do not render the creative
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
    val journey: CreativeJourney? = null,
    @Serializable(with = InteractiveExperienceSerializer::class)
    val interactiveExperience: InteractiveExperience? = null
) {

    // Binary compatibility: integrators construct and copy this class, so the 9-field constructor
    // and copy that apps compiled before Lead Generation link against must keep existing. HIDDEN
    // keeps them in the bytecode but out of source resolution, so new code always binds to the
    // primary constructor and the generated copy.
    @Deprecated("Binary compatibility with callers compiled before Lead Generation", level = DeprecationLevel.HIDDEN)
    constructor(
        contents: List<Content> = emptyList(),
        advertiser: Advertiser = Advertiser(),
        template: TemplateInfo? = null,
        tracking: TrackingInfo = TrackingInfo(),
        metadata: CreativeMetadata? = null,
        delivery: String? = null,
        vast: VastData? = null,
        verificationScriptResources: List<VerificationScriptResource>? = null,
        journey: CreativeJourney? = null
    ) : this(
        contents, advertiser, template, tracking, metadata, delivery, vast, verificationScriptResources, journey,
        null
    )

    @Deprecated("Binary compatibility with callers compiled before Lead Generation", level = DeprecationLevel.HIDDEN)
    fun copy(
        contents: List<Content> = this.contents,
        advertiser: Advertiser = this.advertiser,
        template: TemplateInfo? = this.template,
        tracking: TrackingInfo = this.tracking,
        metadata: CreativeMetadata? = this.metadata,
        delivery: String? = this.delivery,
        vast: VastData? = this.vast,
        verificationScriptResources: List<VerificationScriptResource>? = this.verificationScriptResources,
        journey: CreativeJourney? = this.journey
    ): Creative = Creative(
        contents, advertiser, template, tracking, metadata, delivery, vast, verificationScriptResources, journey,
        interactiveExperience
    )
}
