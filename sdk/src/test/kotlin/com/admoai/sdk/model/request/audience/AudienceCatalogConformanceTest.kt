package com.admoai.sdk.model.request.audience

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Standard audience catalog has one source: adhub's `ui/platform/lib/audience-signals/catalog.ts`,
 * published as `catalog.generated.json` (admoai/adhub#6616). `src/test/resources/audience-catalog.json`
 * is that file vendored byte for byte; refresh it from adhub `main` when the catalog changes.
 *
 * This test fails when the SDK's typed helpers drift from it: a key without a helper, a value missing,
 * extra or reordered, or a request shape the helper does not produce.
 */
class AudienceCatalogConformanceTest {

    private val presets = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader?.getResource("audience-catalog.json")) {
            "audience-catalog.json is missing from the test resources"
        }.readText()
    ).jsonObject["presets"]!!.jsonArray.map { it.jsonObject }

    private val enumWireValues: Map<String, List<String>> = mapOf(
        AudienceKeys.AGE_GROUP to AgeGroup.values().map { it.wireValue },
        AudienceKeys.GENDER to Gender.values().map { it.wireValue },
        AudienceKeys.ACCOUNT_TYPE to AccountType.values().map { it.wireValue },
        AudienceKeys.SUBSCRIPTION_STATUS to SubscriptionStatus.values().map { it.wireValue },
        AudienceKeys.ACCOUNT_TENURE to AccountTenure.values().map { it.wireValue },
        AudienceKeys.TRIP_FREQUENCY_30D to TripFrequency30d.values().map { it.wireValue },
        AudienceKeys.LAST_TRIP_RECENCY to LastTripRecency.values().map { it.wireValue }
    )

    @Test
    fun `every catalog key has a helper and nothing else does`() {
        val catalogKeys = presets.map { it["key"]!!.jsonPrimitive.content }.toSet()
        assertEquals(catalogKeys, enumWireValues.keys + AudienceKeys.PUBLISHER_SEGMENTS)
    }

    @Test
    fun `every closed key sends exactly the catalog values in catalog order`() {
        presets.filter { it["requestShape"]!!.jsonPrimitive.content == "string" }.forEach { preset ->
            val key = preset["key"]!!.jsonPrimitive.content
            val catalogValues = preset["values"]!!.jsonArray.map { it.jsonObject["value"]!!.jsonPrimitive.content }
            assertEquals("values of $key", catalogValues, enumWireValues[key])
        }
    }

    @Test
    fun `publisher segments is the only list-shaped key and has no fixed values`() {
        val listShaped = presets.filter { it["requestShape"]!!.jsonPrimitive.content == "string_array" }
        assertEquals(listOf(AudienceKeys.PUBLISHER_SEGMENTS), listShaped.map { it["key"]!!.jsonPrimitive.content })
        assertEquals(0, listShaped.single()["values"]?.jsonArray?.size ?: 0)
    }
}
