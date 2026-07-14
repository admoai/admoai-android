package com.admoai.sdk.journey

import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.utils.isJourneyAd
import com.admoai.sdk.utils.isJourneyCompletion
import com.admoai.sdk.utils.journeyDealId
import com.admoai.sdk.utils.journeyOptStatus
import com.admoai.sdk.utils.journeyStageKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BDD coverage for parsing read-only creative.journey metadata.
 */
class JourneyResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun parse(journeyJson: String?): com.admoai.sdk.model.response.Creative {
        val journeyPart = journeyJson?.let { ",\"journey\":$it" } ?: ""
        val body = """
            {"success":true,"data":[{"placement":"p1","creatives":[
              {"contents":[],"advertiser":{},"tracking":{}$journeyPart}
            ]}]}
        """.trimIndent()
        return json.decodeFromString<DecisionResponse>(body).data!!.first().creatives.first()
    }

    @Test
    fun `parses all journey keys`() {
        val creative = parse(
            """{"dealId":"deal-1","instanceId":"inst-1","definitionKey":"def-1",
                "stageId":"stg-1","stageKey":"pre_ride","stageNodeId":"node-1",
                "sessionId":"sess-1","optStatus":"in","isCompletion":false,
                "pricingModel":"cpt","fallbackBillingMode":"bill_per_stage"}"""
        )
        assertTrue(creative.isJourneyAd())
        assertEquals("deal-1", creative.journeyDealId())
        assertEquals("pre_ride", creative.journeyStageKey())
        assertEquals(JourneyOpt.OPT_IN, creative.journeyOptStatus())
        assertEquals("cpt", creative.journey?.pricingModel)
        assertEquals("bill_per_stage", creative.journey?.fallbackBillingMode)
        assertFalse(creative.isJourneyCompletion())
    }

    @Test
    fun `optStatus out parses to OPT_OUT`() {
        assertEquals(JourneyOpt.OPT_OUT, parse("""{"dealId":"d","optStatus":"out"}""").journeyOptStatus())
    }

    @Test
    fun `unknown optStatus decodes to null without throwing`() {
        val creative = parse("""{"dealId":"d","optStatus":"paused"}""")
        assertNull(creative.journeyOptStatus())
        assertTrue(creative.isJourneyAd())
    }

    @Test
    fun `isCompletion true is surfaced`() {
        assertTrue(parse("""{"dealId":"d","isCompletion":true}""").isJourneyCompletion())
    }

    @Test
    fun `normal ad has no journey and is not a journey ad`() {
        val creative = parse(null)
        assertNull(creative.journey)
        assertFalse(creative.isJourneyAd())
        assertFalse(creative.isJourneyCompletion())
    }

    @Test
    fun `empty journey object is not a false-positive journey ad`() {
        val creative = parse("{}")
        assertFalse(creative.isJourneyAd())
    }

    @Test
    fun `journey with only instanceId still counts as journey ad`() {
        assertTrue(parse("""{"instanceId":"inst-only"}""").isJourneyAd())
    }
}
