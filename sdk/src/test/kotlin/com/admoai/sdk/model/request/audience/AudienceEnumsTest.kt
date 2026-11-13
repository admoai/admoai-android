package com.admoai.sdk.model.request.audience

import org.junit.Assert.assertEquals
import org.junit.Test

class AudienceEnumsTest {

    private fun <E : Enum<E>> assertCatalog(expected: List<String>, values: Array<E>, wire: (E) -> String) {
        val actual = values.map(wire)
        assertEquals(expected, actual)
        assertEquals("wire values must be unique", actual.size, actual.toSet().size)
    }

    @Test
    fun `AgeGroup wire values`() = assertCatalog(
        listOf("under_18", "18_24", "25_34", "35_44", "45_54", "55_64", "65_plus"),
        AgeGroup.values()
    ) { it.wireValue }

    @Test
    fun `Gender wire values`() = assertCatalog(
        listOf("female", "male", "other", "prefer_not_to_say"),
        Gender.values()
    ) { it.wireValue }

    @Test
    fun `AccountType wire values`() = assertCatalog(
        listOf("personal", "business", "family", "student", "other"),
        AccountType.values()
    ) { it.wireValue }

    @Test
    fun `SubscriptionStatus wire values`() = assertCatalog(
        listOf("none", "trial", "active", "paused", "expired"),
        SubscriptionStatus.values()
    ) { it.wireValue }

    @Test
    fun `AccountTenure wire values`() = assertCatalog(
        listOf("0_30d", "31_90d", "91_365d", "366d_plus"),
        AccountTenure.values()
    ) { it.wireValue }

    @Test
    fun `TripFrequency30d wire values`() = assertCatalog(
        listOf("0", "1_2", "3_9", "10_plus"),
        TripFrequency30d.values()
    ) { it.wireValue }

    @Test
    fun `LastTripRecency wire values`() = assertCatalog(
        listOf("never", "0_7d", "8_30d", "31_90d", "91d_plus"),
        LastTripRecency.values()
    ) { it.wireValue }
}
