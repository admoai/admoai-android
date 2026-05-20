package com.admoai.sdk.model.response

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvertiserTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `given server omits advertiser id when parsing then id is null`() {
        // Given: JSON payload without the id field
        val payload = """{"name":"Acme Corp","legalName":"Acme Corporation","logoUrl":"https://example.com/logo.png"}"""

        // When: SDK parses the response
        val advertiser = json.decodeFromString<Advertiser>(payload)

        // Then: id should be null, not an empty string
        assertNull(advertiser.id)
    }

    @Test
    fun `given server provides advertiser id when parsing then id matches`() {
        // Given: JSON payload with an id field
        val payload = """{"id":"adv_01ABC","name":"Acme Corp"}"""

        // When: SDK parses the response
        val advertiser = json.decodeFromString<Advertiser>(payload)

        // Then: id should equal the provided value
        assertEquals("adv_01ABC", advertiser.id)
    }

    @Test
    fun `given fully populated advertiser payload when parsing then all fields parsed correctly`() {
        // Given: complete advertiser JSON
        val payload = """{"id":"adv_XYZ","name":"Ride Corp","legalName":"Ride Corp LLC","logoUrl":"https://cdn.example.com/logo.svg"}"""

        // When: SDK parses the response
        val advertiser = json.decodeFromString<Advertiser>(payload)

        // Then: all fields should be populated
        assertEquals("adv_XYZ", advertiser.id)
        assertEquals("Ride Corp", advertiser.name)
        assertEquals("Ride Corp LLC", advertiser.legalName)
        assertEquals("https://cdn.example.com/logo.svg", advertiser.logoUrl)
    }

    @Test
    fun `given empty advertiser object when parsing then all fields are null`() {
        // Given: minimal empty advertiser JSON
        val payload = """{}"""

        // When: SDK parses the response
        val advertiser = json.decodeFromString<Advertiser>(payload)

        // Then: all optional fields should be null
        assertNull(advertiser.id)
        assertNull(advertiser.name)
        assertNull(advertiser.legalName)
        assertNull(advertiser.logoUrl)
    }
}
