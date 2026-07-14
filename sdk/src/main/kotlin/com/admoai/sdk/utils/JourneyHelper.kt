package com.admoai.sdk.utils

import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.response.Creative

/** Read-only accessors for Journey Takeover metadata on a [Creative] (mirrors VideoHelper/OMHelper). */

/**
 * Whether this creative is a Journey serve. Checks a real identifier (not just non-null) so a
 * tolerant `"journey": {}` decoding to an all-null object is not a false positive.
 */
fun Creative.isJourneyAd(): Boolean =
    !journey?.dealId.isNullOrBlank() || !journey?.instanceId.isNullOrBlank()

/** Whether this serve completes the Journey (`final_stage` strategy). */
fun Creative.isJourneyCompletion(): Boolean = journey?.isCompletion == true

/** Whether this creative carries a completion beacon to fire (custom_event deals only). */
fun Creative.hasCompletionUrl(): Boolean = !tracking.completions.isNullOrEmpty()

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
