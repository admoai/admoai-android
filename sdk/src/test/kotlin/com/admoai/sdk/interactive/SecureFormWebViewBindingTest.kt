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
        val tests = testNames(source)

        assertTrue("the feature file has scenarios", titles.isNotEmpty())
        assertEquals("scenarios without an instrumented test", emptySet<String>(), titles - tests)
        assertEquals("instrumented tests without a scenario", emptyList<String>(), tests.filter { it !in titles })
        assertEquals("one scenario, one test", tests.size, tests.toSet().size)
    }

    /**
     * The name of the function each `@Test` is attached to: the next `fun` line after the annotation,
     * with only other annotations between. A `@Test` followed by another `@Test` (or by the end of the
     * file) before any function is attached to nothing and fails, so it cannot hide behind a neighbour.
     */
    private fun testNames(source: List<String>): List<String> = source.indices
        .filter { source[it].trim() == "@Test" }
        .map { i ->
            val attached = source.drop(i + 1).map { it.trim() }
                .firstOrNull { it.isNotEmpty() && (!it.startsWith("@") || it == "@Test") }
                ?.takeIf { it.startsWith("fun `") }
            requireNotNull(attached) { "the @Test on line ${i + 1} is not attached to a test function" }
            attached.removePrefix("fun `").substringBefore("`")
        }
}
