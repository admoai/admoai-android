package com.admoai.sdk.model.response

import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.common.JourneyOptTolerantSerializer
import kotlinx.serialization.Serializable

/**
 * Read-only Journey Takeover metadata attached to a served creative (`creative.journey`).
 *
 * All fields are nullable and decoded tolerantly — the SDK forwards and surfaces this metadata but
 * never interprets it. All Journey runtime logic (progression, completion, billing) is engine-owned.
 *
 * @property dealId Journey deal public id (always present on a real serve)
 * @property instanceId Runtime journey instance id (always present on a real serve)
 * @property definitionKey Journey definition key
 * @property stageId Stage public id
 * @property stageKey Stage key (may be absent)
 * @property stageNodeId Stage-node public id
 * @property sessionId Session id echoed back by the engine
 * @property optStatus Opt state for this serve (open set: unknown -> null)
 * @property isCompletion True only on a `final_stage` completing serve
 * @property pricingModel Open-set pricing model string (cpm/cpc/cpv/cpcv/fixed/cpt/standard/...)
 * @property fallbackBillingMode Open-set fallback billing mode (bill_per_stage/no_charge/...)
 */
@Serializable
data class CreativeJourney(
    val dealId: String? = null,
    val instanceId: String? = null,
    val definitionKey: String? = null,
    val stageId: String? = null,
    val stageKey: String? = null,
    val stageNodeId: String? = null,
    val sessionId: String? = null,
    @Serializable(with = JourneyOptTolerantSerializer::class)
    val optStatus: JourneyOpt? = null,
    val isCompletion: Boolean? = null,
    val pricingModel: String? = null,
    val fallbackBillingMode: String? = null
)
