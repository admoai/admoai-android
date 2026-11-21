package com.admoai.sdk

import android.content.Context
import com.admoai.sdk.config.AppConfig
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.config.UserConfig
import com.admoai.sdk.interactive.ExternalLinkPurpose
import com.admoai.sdk.interactive.FormPresentation
import com.admoai.sdk.interactive.LeadFormController
import com.admoai.sdk.interactive.LeadFormHost
import com.admoai.sdk.interactive.LeadFormState
import com.admoai.sdk.interactive.LeadFormValueProvider
import com.admoai.sdk.interactive.LeadFormValues
import com.admoai.sdk.interactive.StandardAttributeKey
import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.request.Consent
import com.admoai.sdk.model.request.CustomTargetingInfo
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.request.DestinationTargetingInfo
import com.admoai.sdk.model.request.DeviceCategory
import com.admoai.sdk.model.request.LocationTargetingInfo
import com.admoai.sdk.model.request.PlacementFormat
import com.admoai.sdk.model.request.audience.AccountTenure
import com.admoai.sdk.model.request.audience.AccountType
import com.admoai.sdk.model.request.audience.ActivityFrequency30d
import com.admoai.sdk.model.request.audience.AgeGroup
import com.admoai.sdk.model.request.audience.AudienceKeys
import com.admoai.sdk.model.request.audience.Gender
import com.admoai.sdk.model.request.audience.LastActivityRecency
import com.admoai.sdk.model.request.audience.SubscriptionStatus
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.InteractiveExperience
import com.admoai.sdk.model.response.Slide
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.TrackingType
import com.admoai.sdk.model.response.getClickUrl
import com.admoai.sdk.model.response.getCompletionUrl
import com.admoai.sdk.model.response.getContent
import com.admoai.sdk.model.response.getCustomUrl
import com.admoai.sdk.model.response.getImpressionUrl
import com.admoai.sdk.model.response.getTrackingUrl
import com.admoai.sdk.model.response.getVideoEventUrl
import com.admoai.sdk.model.response.hasContents
import com.admoai.sdk.model.response.hasCreative
import com.admoai.sdk.model.response.hasTrackingFor
import com.admoai.sdk.model.response.isNoAd
import com.admoai.sdk.model.response.isRawType
import com.admoai.sdk.model.response.isType
import com.admoai.sdk.model.response.slides
import com.admoai.sdk.utils.getSkipOffset
import com.admoai.sdk.utils.getVastTagUrl
import com.admoai.sdk.utils.getVastXmlBase64
import com.admoai.sdk.utils.getVerificationResources
import com.admoai.sdk.utils.hasCompletionUrl
import com.admoai.sdk.utils.hasOMVerification
import com.admoai.sdk.utils.isJourneyAd
import com.admoai.sdk.utils.isJourneyCompletion
import com.admoai.sdk.utils.isJsonDelivery
import com.admoai.sdk.utils.isSkippable
import com.admoai.sdk.utils.isVastTagDelivery
import com.admoai.sdk.utils.isVastXmlDelivery
import com.admoai.sdk.utils.journeyDealId
import com.admoai.sdk.utils.journeyDefinitionKey
import com.admoai.sdk.utils.journeyFallbackBillingMode
import com.admoai.sdk.utils.journeyInstanceId
import com.admoai.sdk.utils.journeyOptStatus
import com.admoai.sdk.utils.journeyPricingModel
import com.admoai.sdk.utils.journeySessionId
import com.admoai.sdk.utils.journeyStageId
import com.admoai.sdk.utils.journeyStageKey
import com.admoai.sdk.utils.journeyStageNodeId
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * Compile-time proof that every API symbol the README shows a publisher actually exists.
 *
 * iOS (`DocumentedAPITests.swift`) and Flutter (`doc_examples_compile_test.dart`) have had this
 * since the Journey round; Android was the only SDK without it. That asymmetry mattered: a
 * documented method that does not exist fails at a publisher's first integration rather than in
 * our build, and this SDK is installed in hundreds of thousands of user apps.
 *
 * The bodies deliberately do almost nothing. The assertion IS compilation: rename or remove a
 * documented symbol and this file stops compiling, which fails the build. Nothing here touches the
 * network, so it stays in the hermetic gate.
 */
class DocumentedApiTest {

    @Test
    fun `documented configuration surface exists`() {
        val config = SDKConfig(
            baseUrl = "https://example.test",
            apiVersion = "2025-11-01",
            enableLogging = false,
            defaultLanguage = "en"
        )

        @Suppress("UNUSED_VARIABLE")
        val copy = config.copy(enableLogging = true)

        @Suppress("UNUSED_VARIABLE")
        val user = UserConfig(id = "u", ip = null, timezone = "UTC")

        @Suppress("UNUSED_VARIABLE")
        val app = AppConfig(language = "en")

        @Suppress("UNUSED_VARIABLE")
        val device = DeviceConfig(language = "en")
    }

    @Test
    fun `documented request-builder surface exists`() {
        val builder: DecisionRequestBuilder = DecisionRequestBuilder()
            .addPlacement("home")
            .addPlacement("promotions", count = 2, format = PlacementFormat.NATIVE)
            .addGeoTarget(5128581)
            .setGeoTargets(listOf(5128581))
            .addLocationTarget(40.7, -74.0)
            .setLocationTargets(listOf(LocationTargetingInfo(40.7, -74.0)))
            .addDestinationTarget(41.0, -73.0, 0.8)
            .setDestinationTargets(listOf(DestinationTargetingInfo(41.0, -73.0, 0.8)))
            .addCustomTarget("category", "sports")
            .addCustomTarget("count", 3)
            .addCustomTarget("flag", true)
            .addCustomTarget("raw", JsonPrimitive("x"))
            .setCustomTargets(listOf(CustomTargetingInfo("category", JsonPrimitive("news"))))
            .setUserId("u")
            .setUserIp("1.2.3.4")
            .setUserTimezone("UTC")
            .setUserConsent(true)
            .setUserConsent(Consent(gdpr = true))
            .setSessionId("trip-1")
            .setJourneyOpt(JourneyOpt.OPT_IN)
            .disableAppCollection()
            .disableDeviceCollection()

        builder.clearGeoTargeting()
        builder.clearLocationTargeting()
        builder.clearDestinationTargeting()
        builder.clearCustomTargeting()
        builder.clearTargeting()
        builder.clearUser()
        builder.clearSessionId()
        builder.clearJourneyOpt()
        builder.clearPlacements()
        builder.addPlacement("home").clearAll().addPlacement("home").build()
    }

    // sdk/README.md "Audience Signals": every helper, enum constant family and device API it shows.
    @Test
    fun `documented audience signals surface exists`() {
        DecisionRequestBuilder()
            .addPlacement(key = "home")
            .setAgeGroup(AgeGroup.AGE_25_34)
            .setGender(Gender.PREFER_NOT_TO_SAY)
            .setAccountType(AccountType.BUSINESS)
            .setSubscriptionStatus(SubscriptionStatus.NONE)
            .setAccountTenure(AccountTenure.DAYS_366_PLUS)
            .setActivityFrequency30d(ActivityFrequency30d.ZERO)
            .setLastActivityRecency(LastActivityRecency.DAYS_91_PLUS)
            .setPublisherSegments(listOf("vip", "commuter"))
            .setAgeGroup(null)
            .addCustomTarget(key = "loyalty_tier", value = "gold")
            .build()

        @Suppress("UNUSED_VARIABLE")
        val key: String = AudienceKeys.AGE_GROUP

        @Suppress("UNUSED_VARIABLE")
        val wire: String = ActivityFrequency30d.ZERO.wireValue

        // systemDefault(context) needs a real Context; referencing it proves the documented
        // overload exists without calling it.
        @Suppress("UNUSED_VARIABLE")
        val withContext: (android.content.Context) -> DeviceConfig = { DeviceConfig.systemDefault(it) }
        val manual = DeviceConfig(carrier = "Movistar", category = DeviceCategory.TABLET)

        @Suppress("UNUSED_VARIABLE")
        val copied = manual.copy(carrier = "Claro", category = DeviceCategory.PHONE)

        @Suppress("UNUSED_VARIABLE")
        val other = DeviceCategory.OTHER

        // Clear and reset, as the README's segments section shows them.
        DecisionRequestBuilder()
            .setPublisherSegments(null)
            .setPublisherSegments(emptyList())
            .clearCustomTargeting()
            .clearTargeting()
            .clearAll()
            .disableDeviceCollection()
    }

    /**
     * Reference-only: never executed. The Audience Signals README calls that need a live SDK or a
     * real Context (the worked example's `initialize(baseUrl, apiVersion)`, device config set/clear,
     * `systemDefault(context).copy(...)`, `requestAds`).
     */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun documentedAudienceSignalsSdkCalls(context: android.content.Context) {
        Admoai.initialize(baseUrl = "https://api.admoai.com", apiVersion = "2025-11-01")
        val sdk = Admoai.getInstance()
        sdk.setDeviceConfig(DeviceConfig.systemDefault(context))
        sdk.setDeviceConfig(
            DeviceConfig.systemDefault(context).copy(carrier = "Movistar", category = DeviceCategory.TABLET)
        )
        sdk.setDeviceConfig(DeviceConfig(carrier = "Movistar", category = DeviceCategory.TABLET))
        sdk.clearDeviceConfig()
        val flow = sdk.requestAds(sdk.createRequestBuilder().addPlacement(key = "home").build())
    }

    /**
     * Reference-only: never executed. Referencing the symbols is the whole assertion, and doing it
     * without a live SDK instance keeps the check hermetic.
     */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun documentedResponseSurface(creative: Creative, tracking: TrackingInfo) {
        val contents = creative.contents
        val byKey = contents.getContent("headline")
        val any = contents.hasContents()
        val typed = contents.isType("headline", ContentType.TEXT)
        val rawTyped = contents.isRawType("choice", "dropdown")

        val delivery: String? = creative.delivery
        val json = creative.isJsonDelivery()
        val vastTag = creative.isVastTagDelivery()
        val vastXml = creative.isVastXmlDelivery()
        val tagUrl = creative.getVastTagUrl(mediaType = "video/mp4", mediaDelivery = "progressive")
        val xml = creative.getVastXmlBase64()
        val skippable = creative.isSkippable()
        val skipOffset = creative.getSkipOffset()

        val verifications = creative.getVerificationResources()
        val hasOm = creative.hasOMVerification()

        val isJourney = creative.isJourneyAd()
        val completing = creative.isJourneyCompletion()
        val beacon = creative.hasCompletionUrl()
        val dealId = creative.journeyDealId()
        val instanceId = creative.journeyInstanceId()
        val definitionKey = creative.journeyDefinitionKey()
        val stageId = creative.journeyStageId()
        val stageKey = creative.journeyStageKey()
        val stageNodeId = creative.journeyStageNodeId()
        val sessionId = creative.journeySessionId()
        val optStatus = creative.journeyOptStatus()
        val pricingModel = creative.journeyPricingModel()
        val fallbackBillingMode = creative.journeyFallbackBillingMode()

        val impId = creative.metadata?.impId
        val endCardMode = creative.metadata?.endCardMode
        val skipSeconds = creative.metadata?.skipOffsetSeconds

        val impressionUrl = tracking.getImpressionUrl()
        val clickUrl = tracking.getClickUrl()
        val customUrl = tracking.getCustomUrl("expand")
        val videoUrl = tracking.getVideoEventUrl("start")
        val completionUrl = tracking.getCompletionUrl("journey_complete")
        val byType = tracking.getTrackingUrl(TrackingType.IMPRESSION, "default")
        val has = tracking.hasTrackingFor(TrackingType.CLICK, "default")
    }

    /** Reference-only: the documented tracking and no-ad surface on the SDK singleton. */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun documentedSdkSurface(sdk: Admoai, tracking: TrackingInfo) {
        sdk.fireTracking("https://example.test/v1/tracking?e=token")
        sdk.fireImpression(tracking)
        sdk.fireClick(tracking)
        sdk.fireCustomEvent(tracking, "expand")
        sdk.fireVideoEvent(tracking, "start")
        sdk.fireCompletion(tracking, "journey_complete")

        sdk.setSessionId("trip-1")
        sdk.clearSessionId()
        val session = sdk.getSessionId()
        val builder = sdk.createRequestBuilder()
        val http = sdk.getHttpRequestData(builder.addPlacement("home").build())
    }

    /** Reference-only: the README's Carousel, Branded Assets and Sponsored Pin content calls. */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun documentedStandardFormatSurface(sdk: Admoai, creative: Creative) {
        val slides: List<Slide> = creative.slides
        slides.forEach { slide ->
            val id: String = slide.slideId
            val position: Int = slide.position
            val image = slide.contents.getContent("image")
            sdk.trackSlideView(slide)
            sdk.trackSlideClick(slide, creative)
        }
        sdk.trackSlideViews(slides)

        val assetContext: String? = creative.metadata?.assetContext
        val brandedImage = creative.contents.getContent("branded_image")
        val label = creative.contents.getContent("accessible_label")
        val destination = creative.contents.getContent("destination_url")
        sdk.fireAssetTap(creative.tracking)

        val pinImage = creative.contents.getContent("pinImage")
    }

    /** Reference-only: README "Reading the error code" — the engine's codes on both exceptions. */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun documentedErrorCodeSurface(e: Throwable) {
        val codes: List<Int> = when (e) {
            is com.admoai.sdk.exception.AdMoaiValidationException -> e.errors.map { it.code }
            is com.admoai.sdk.exception.AdMoaiNetworkException -> e.errors.map { it.code }
            else -> emptyList()
        }
        val status: Int? = (e as? com.admoai.sdk.exception.AdMoaiNetworkException)?.statusCode
    }

    @Suppress("unused", "UNUSED_VARIABLE")
    private fun documentedNoAdSurface(data: com.admoai.sdk.model.response.AdData) {
        val hasOne = data.hasCreative()
        val none = data.isNoAd()
    }

    /** Reference-only: README "Lead Generation forms" — every member the section shows. */
    @Suppress("unused", "UNUSED_VARIABLE")
    private fun documentedLeadGenerationSurface(sdk: Admoai, context: Context, creative: Creative) {
        val provider = object : LeadFormValueProvider {
            override fun availableKeys(): Set<StandardAttributeKey> = setOf(
                StandardAttributeKey.FIRST_NAME,
                StandardAttributeKey.LAST_NAME,
                StandardAttributeKey.FULL_NAME,
                StandardAttributeKey.EMAIL,
                StandardAttributeKey.PHONE,
                StandardAttributeKey.AGE,
                StandardAttributeKey.GENDER
            )

            override fun lookUpValues(keys: Set<StandardAttributeKey>, completion: LeadFormValues) {
                completion.complete(emptyMap())
            }
        }
        sdk.enableInteractiveExperiences(provider)
        sdk.disableInteractiveExperiences()
        sdk.disposeAllLeadForms()
        sdk.setUserConfig(UserConfig(id = "u"))
        sdk.clearUserConfig()

        val experience: InteractiveExperience? = creative.interactiveExperience
        val show = when (experience) {
            is InteractiveExperience.LeadGeneration -> true
            is InteractiveExperience.Unsupported -> false
            null -> true
        }

        val host = object : LeadFormHost {
            override fun onRequestClose(controller: LeadFormController) = controller.dispose()

            override fun onOpenExternalLink(controller: LeadFormController, purpose: ExternalLinkPurpose, url: String) {
                val privacy = purpose == ExternalLinkPurpose.PRIVACY_POLICY
                val website = purpose == ExternalLinkPurpose.WEBSITE_CTA
            }
        }
        val controller: LeadFormController? =
            sdk.createLeadFormController(context, creative, FormPresentation.FULL_SCREEN, host)
        controller?.let {
            it.prepare()
            sdk.fireClick(creative.tracking)
            it.open()
            it.view()
            it.setVisible(true)
            it.setVisible(false)
            val consumed: Boolean = it.handleBack()
            it.retry()
            it.dispose()
            val flow: kotlinx.coroutines.flow.StateFlow<LeadFormState> = it.state
            documentedStateLabel(flow)
        }
    }

    // Outside any lambda: Android lint's Compose StateFlow detector crashes on `.value` read inside a
    // lambda in this file (it fails to read the stdlib's Kotlin metadata).
    private fun documentedStateLabel(flow: kotlinx.coroutines.flow.StateFlow<LeadFormState>): String {
        val state = flow.value
        return when (state) {
            LeadFormState.Idle, LeadFormState.Preparing, LeadFormState.Prepared,
            LeadFormState.Activating, LeadFormState.ReadyToShow, LeadFormState.Visible,
            LeadFormState.Submitting, LeadFormState.Expired, LeadFormState.Disposed -> ""

            is LeadFormState.Submitted -> state.leadId + state.submittedAt

            is LeadFormState.Failed -> state.errorCode + state.retryable
        }
    }
}
