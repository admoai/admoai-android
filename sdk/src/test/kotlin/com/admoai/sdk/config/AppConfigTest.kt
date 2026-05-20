package com.admoai.sdk.config

import org.junit.Assert.assertNull
import org.junit.Test

class AppConfigTest {

    @Test
    fun `systemDefault with null context returns empty AppConfig`() {
        // Given: no Android context available (JVM test / null context)
        // When: systemDefault is called with null
        val config = AppConfig.systemDefault(context = null)

        // Then: all fields should be null (graceful fallback)
        assertNull("appName should be null with no context", config.appName)
        assertNull("appVersion should be null with no context", config.appVersion)
        assertNull("packageName should be null with no context", config.packageName)
        assertNull("buildNumber should be null with no context", config.buildNumber)
        assertNull("language should be null with no context", config.language)
    }

    @Test
    fun `clear returns config with all null fields`() {
        // Given: a populated config
        val populated = AppConfig(
            appName = "My App",
            appVersion = "1.0.0",
            packageName = "com.example.app",
            buildNumber = "42",
            language = "en-US"
        )

        // When: clear is called
        val cleared = populated.clear()

        // Then: all fields are null
        assertNull(cleared.appName)
        assertNull(cleared.appVersion)
        assertNull(cleared.packageName)
        assertNull(cleared.buildNumber)
        assertNull(cleared.language)
    }

    @Test
    fun `AppConfig accepts all 5 fields`() {
        // Given/When: AppConfig created with all fields
        val config = AppConfig(
            appName = "AdMoai Example",
            appVersion = "2.1.0",
            packageName = "com.admoai.example",
            buildNumber = "100",
            language = "pt-BR"
        )

        // Then: all fields accessible
        assert(config.appName == "AdMoai Example")
        assert(config.appVersion == "2.1.0")
        assert(config.packageName == "com.admoai.example")
        assert(config.buildNumber == "100")
        assert(config.language == "pt-BR")
    }
}
