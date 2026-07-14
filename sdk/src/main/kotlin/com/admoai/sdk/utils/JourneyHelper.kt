package com.admoai.sdk.utils

import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.response.Creative

/**
 * Read-only accessors for Journey Takeover metadata on a [Creative]. Keeps [Creative] lean and
 * mirrors the [VideoHelper]/[OMHelper] extension pattern. The SDK only surfaces this data — it
 * never runs Journey logic locally.
 */

/**
 * Whether this creative is a Journey Takeover serve.
 *
 * Guards against a false positive from an empty/garbage `journey` object: tolerant decoding turns
 * `"journey": {}` into a non-null all-null [com.admoai.sdk.model.response.CreativeJourney], so a bare
 * null-check is insufficient. A real serve always carries `dealId`/`instanceId`.
 */
fun Creative.isJourneyAd(): Boolean =
    !journey?.dealId.isNullOrBlank() || !journey?.instanceId.isNullOrBlank()

/** Whether this serve completes the Journey (`final_stage` strategy). */
fun Creative.isJourneyCompletion(): Boolean = journey?.isCompletion == true

fun Creative.journeyDealId(): String? = journey?.dealId
fun Creative.journeyInstanceId(): String? = journey?.instanceId
fun Creative.journeyDefinitionKey(): String? = journey?.definitionKey
fun Creative.journeyStageId(): String? = journey?.stageId
fun Creative.journeyStageKey(): String? = journey?.stageKey
fun Creative.journeyStageNodeId(): String? = journey?.stageNodeId
fun Creative.journeySessionId(): String? = journey?.sessionId
fun Creative.journeyOptStatus(): JourneyOpt? = journey?.optStatus
fun Creative.journeyPricingModel(): String? = journey?.pricingModel
fun Creative.journeyFallbackBillingMode(): String? = journey?.fallbackBillingMode
