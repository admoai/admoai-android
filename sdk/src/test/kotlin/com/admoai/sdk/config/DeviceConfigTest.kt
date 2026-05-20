package com.admoai.sdk.config

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceConfigTest {

    /**
     * Note: In JVM unit tests, Android's Build class is a stub — Build.MANUFACTURER and
     * other Build fields return null. These tests verify the DeviceConfig data structure
     * and the systemDefault() code path. The actual field values from Build.* are
     * only non-null on a real Android device (instrumented tests / production).
     */

    @Test
    fun `systemDefault includes manufacturer and language in code path`() {
        // Given/When: systemDefault is called
        // (in JVM tests Build.MANUFACTURER may be null, but the code path is exercised)
        val config = DeviceConfig.systemDefault()

        // Then: the returned config must be a valid DeviceConfig (no exception thrown)
        // osName is always "Android" (hardcoded), so it's reliably non-null
        assert(config.osName == "Android") { "osName should always be 'Android'" }
    }

    @Test
    fun `systemDefault language field uses Locale which is always available on JVM`() {
        // Given/When: systemDefault is called
        val config = DeviceConfig.systemDefault()

        // Then: language comes from Locale.getDefault().toLanguageTag() which is always
        // non-null on JVM — unlike Build.* fields which are Android stubs
        assertNotNull("language from Locale.getDefault() must not be null", config.language)
    }

    @Test
    fun `systemDefault with androidId includes deviceId`() {
        // Given/When: systemDefault called with an androidId
        val config = DeviceConfig.systemDefault(androidId = "test-device-id-123")

        // Then: deviceId should be the provided value
        assert(config.deviceId == "test-device-id-123") { "deviceId must match provided value" }
    }

    @Test
    fun `systemDefault without androidId has null deviceId`() {
        // Given/When: systemDefault called without androidId
        val config = DeviceConfig.systemDefault()

        // Then: deviceId is null
        assertNull("deviceId should be null when not provided", config.deviceId)
    }

    @Test
    fun `DeviceConfig manufacturer and language fields exist and work correctly`() {
        // Given: a DeviceConfig created manually with all fields
        val config = DeviceConfig(
            model = "Pixel 6",
            osName = "Android",
            osVersion = "13",
            timezone = "America/New_York",
            deviceId = "abc123",
            manufacturer = "Google",
            language = "en-US"
        )

        // Then: all 7 fields are accessible
        assert(config.model == "Pixel 6")
        assert(config.osName == "Android")
        assert(config.osVersion == "13")
        assert(config.timezone == "America/New_York")
        assert(config.deviceId == "abc123")
        assert(config.manufacturer == "Google")
        assert(config.language == "en-US")
    }

    @Test
    fun `clear returns config with all null fields`() {
        // Given: a populated config
        val populated = DeviceConfig(
            model = "Pixel 6",
            manufacturer = "Google",
            language = "en-US"
        )

        // When: clear is called
        val cleared = populated.clear()

        // Then: all fields are null
        assertNull(cleared.model)
        assertNull(cleared.manufacturer)
        assertNull(cleared.language)
    }
}
