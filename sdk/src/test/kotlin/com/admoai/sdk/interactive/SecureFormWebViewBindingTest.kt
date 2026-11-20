package com.admoai.sdk.interactive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Binds `SecureFormWebView.feature` to its instrumented suite, which the JVM cannot run: every
 * scenario of the feature must name a test in `SecureFormWebViewTest` (androidTest), and the other
 * way round. A scenario added without a test, or a test whose scenario was renamed away, fails here.
 */
class SecureFormWebViewBindingTest {

    @Test
    fun `every scenario has an instrumented test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/interactive/SecureFormWebView.feature")
        val titles = feature.readLines()
            .map { it.trim() }
            .filter { it.startsWith("Scenario:") || it.startsWith("Scenario Outline:") }
            .map { it.substringAfter(":").trim() }
            .toSet()
        val source = File("src/androidTest/kotlin/com/admoai/sdk/interactive/SecureFormWebViewTest.kt").readLines()
        val tests = source.indices
            .filter { source[it].trim() == "@Test" }
            .map { i -> source.drop(i + 1).first { it.trim().startsWith("fun `") } }
            .map { it.trim().removePrefix("fun `").substringBefore("`") }
            .toSet()

        assertTrue("the feature file has scenarios", titles.isNotEmpty())
        assertEquals("scenarios without an instrumented test", emptySet<String>(), titles - tests)
        assertEquals("instrumented tests without a scenario", emptySet<String>(), tests - titles)
    }
}
