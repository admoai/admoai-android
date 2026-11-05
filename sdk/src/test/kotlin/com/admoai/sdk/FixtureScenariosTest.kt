package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.Slide
import com.admoai.sdk.model.response.getContent
import com.admoai.sdk.model.response.matchedPoints
import com.admoai.sdk.model.response.slides
import io.ktor.client.engine.cio.CIO
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Shared fixture scenarios (`@admoai/sdk-scenarios` schemaVersion 2, admoai/adhub#4733).
 *
 * Feature: A publisher's app reads the standard native formats from a decision response
 *   As a publisher's tech team
 *   I want the SDK to decode the engine's golden wire files for every standard format
 *   So that what my app reads (format, version, template source, contents, slides, trackers)
 *   is exactly what the platform promises, in this SDK and in the other two
 *
 * The scenarios are DATA: `fixtureScenarios` in `e2e-scenarios.json`, vendored from adhub with the
 * golden decision-wire files they decode (`fixtures/golden-wire/`). This file is only the
 * interpreter, and it is strict in one direction: a manifest key it does not know FAILS, so a newer
 * manifest can never pass silently on an SDK that cannot check it.
 *
 * Each golden file is served by a mock server and read back through `Admoai.requestAds`, so it is
 * decoded by the exact parser configuration the SDK uses for a real decision response.
 * Mirrors the iOS reference suite (`FixtureScenariosTests.swift`).
 */
@RunWith(Parameterized::class)
class FixtureScenariosTest(
    private val id: String,
    @Suppress("unused") private val title: String,
    private val entry: JsonObject?,
    private val loadError: String?
) {

    companion object {
        private fun resource(path: String): String? =
            FixtureScenariosTest::class.java.classLoader!!.getResourceAsStream(path)?.bufferedReader()?.readText()

        /**
         * One row per fixture scenario, so each F-id reports on its own. An unreadable manifest, or
         * one with no `fixtureScenarios`, is a failing LOAD row rather than zero rows, which would
         * pass.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
        fun scenarios(): List<Array<Any?>> {
            fun failure(message: String): List<Array<Any?>> =
                listOf(arrayOf("LOAD", "the fixture scenarios load", null, message))
            val text =
                resource("e2e-scenarios.json") ?: return failure("e2e-scenarios.json is not on the test resource path")
            return try {
                val entries = Json.parseToJsonElement(text).jsonObject["fixtureScenarios"] as? JsonArray
                if (entries == null || entries.isEmpty()) {
                    failure("the manifest has no fixtureScenarios")
                } else {
                    entries.map { element ->
                        val obj = element.jsonObject
                        arrayOf(obj.string("id") ?: "?", obj.string("title").orEmpty(), obj, null)
                    }
                }
            } catch (e: Exception) {
                failure("e2e-scenarios.json cannot be read: $e")
            }
        }
    }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        Admoai.resetForTesting()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Admoai.resetForTesting()
    }

    @Test
    fun `fixture scenario`() = runTest {
        if (loadError != null) failWith(loadError)
        val scenario = entry!!
        val fixture = scenario["fixture"] as? JsonObject ?: failWith("$id: no fixture block")
        val golden = fixture.string("golden") ?: failWith("$id: the fixture names no golden file")
        val expect = scenario["expect"] as? JsonObject ?: failWith("$id: no expect block")

        // The wire, as the golden file has it, then edited.
        val text = resource("fixtures/golden-wire/$golden.json") ?: failWith("$id: $golden.json is not vendored")
        val wire =
            applyEdits(
                id,
                fixture["edits"] as? JsonArray ?: JsonArray(emptyList()),
                Json.parseToJsonElement(text).jsonObject
            )

        val creative = decodeThroughTheSdk(wire)
        for ((key, value) in expect) check(key, value, creative)
    }

    /** Served and read back exactly as a 200 from the engine is: the SDK's own client and parser. */
    private suspend fun decodeThroughTheSdk(wire: JsonObject): Creative {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(wire.toString())
        )
        Admoai.initialize(
            SDKConfig(
                baseUrl = "http://127.0.0.1:${server.port}/",
                apiVersion = "2025-11-01",
                networkClientEngine = CIO.create()
            )
        )
        val sdk = Admoai.getInstance()
        val response = sdk.requestAds(sdk.createRequestBuilder().addPlacement("fixture").build()).first()
        server.takeRequest(3, TimeUnit.SECONDS)
        val creatives = response.data?.firstOrNull()?.creatives ?: emptyList()
        assertEquals("$id: exactly one creative", 1, creatives.size)
        return creatives.first()
    }

    private fun check(key: String, value: JsonElement, creative: Creative) {
        when (key) {
            "decodes" -> assertEquals("$id: decodes is true", true, (value as? JsonPrimitive)?.booleanOrNull)

            "metadata" -> checkMetadata(value as? JsonObject, creative)

            "templateKey" -> assertEquals("$id: templateKey", value.stringOrNull(), creative.template?.key)

            "contentKeys" -> {
                val plain = creative.contents.map { it.key }.toSet() - setOf("slides", "matched_points")
                assertEquals("$id: contentKeys, got ${plain.sorted()}", strings(value).toSet(), plain)
            }

            "contentKeysAbsent" -> strings(value).forEach { absent ->
                assertNull("$id: $absent is absent", creative.contents.getContent(absent))
            }

            "clickKeys" -> assertEquals(
                "$id: clickKeys",
                strings(value),
                creative.tracking.clicks.orEmpty().map {
                    it.key
                }
            )

            "customKeys" -> assertEquals(
                "$id: customKeys",
                strings(value),
                creative.tracking.custom.orEmpty().map {
                    it.key
                }
            )

            "videoEventKeys" ->
                assertEquals(
                    "$id: videoEventKeys",
                    strings(value),
                    creative.tracking.videoEvents.orEmpty().map {
                        it.key
                    }
                )

            "slides" -> checkSlides(value as? JsonArray ?: JsonArray(emptyList()), creative.slides)

            "noSkippableSetting" -> {
                assertEquals("$id: noSkippableSetting is true", true, (value as? JsonPrimitive)?.booleanOrNull)
                assertNull("$id: isSkippable is absent", creative.metadata?.isSkippable)
                assertNull("$id: skipOffsetSeconds is absent", creative.metadata?.skipOffsetSeconds)
            }

            "matchedPoints" -> assertEquals(
                "$id: matchedPoints count",
                (value as? JsonPrimitive)?.intOrNull,
                creative.matchedPoints.size
            )

            else -> failWith("$id: unknown expect key $key")
        }
    }

    private fun checkMetadata(expected: JsonObject?, creative: Creative) {
        val metadata = creative.metadata
        for ((key, value) in expected ?: failWith("$id: metadata expectation is not an object")) {
            when (key) {
                "effectiveStandardFormatKey" -> expectOptional(key, metadata?.effectiveStandardFormatKey, value)

                "effectiveStandardFormatVersion" -> expectOptional(key, metadata?.effectiveStandardFormatVersion, value)

                "templateSource" -> expectOptional(key, metadata?.templateSource, value)

                else -> failWith(
                    "$id: unknown metadata expect key $key " +
                        "(known: effectiveStandardFormatKey, effectiveStandardFormatVersion, templateSource)"
                )
            }
        }
    }

    /** JSON null expects the property to be `null`; anything else must equal it, type included. */
    private fun expectOptional(key: String, actual: Any?, expected: JsonElement) {
        val primitive = expected as? JsonPrimitive
        when {
            expected is JsonNull -> assertNull("$id: metadata.$key is absent, got $actual", actual)
            primitive == null -> failWith("$id: metadata.$key expectation $expected has the wrong type")
            actual is String -> assertEquals("$id: metadata.$key", primitive.contentOrNull, actual)
            actual is Int -> assertEquals("$id: metadata.$key", primitive.intOrNull, actual)
            else -> failWith("$id: metadata.$key is $actual, expected $expected")
        }
    }

    private fun checkSlides(expected: JsonArray, slides: List<Slide>) {
        assertEquals("$id: slide count, got ${slides.size}", expected.size, slides.size)
        expected.forEachIndexed { index, element ->
            val want = element.jsonObject
            val slide = slides.getOrNull(index) ?: return@forEachIndexed
            val label = "$id: slide ${index + 1}"
            want.keys.filterNot { it in setOf("slideId", "position", "contentKeys", "viewKeys", "clickKeys") }
                .forEach { failWith("$label: unknown slide expect key $it") }
            assertEquals("$label slideId", want.string("slideId"), slide.slideId)
            assertEquals("$label position", (want["position"] as? JsonPrimitive)?.intOrNull, slide.position)
            assertEquals(
                "$label contentKeys",
                strings(want["contentKeys"]).toSet(),
                slide.contents.map {
                    it.key
                }.toSet()
            )
            assertEquals(
                "$label viewKeys",
                strings(want["viewKeys"]).toSet(),
                slide.tracking?.views.orEmpty().map { it.key }.toSet()
            )
            assertEquals(
                "$label clickKeys",
                strings(want["clickKeys"]).toSet(),
                slide.tracking?.clicks.orEmpty().map { it.key }.toSet()
            )
        }
    }

    private fun strings(value: JsonElement?): List<String> =
        (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}

/** An assertion failure the compiler knows never returns. */
private fun failWith(message: String): Nothing = throw AssertionError(message)

private fun JsonObject.string(key: String): String? = this[key].stringOrNull()

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

// MARK: - Edits

/**
 * Applies `fixture.edits` in order. `at` names which object(s) of the decision wire change: the
 * response root, the first creative, its `metadata`, every element of its `contents`, or every
 * element of the `value` array of the content entry keyed `slides`. An edit this interpreter does
 * not understand fails the scenario.
 */
private fun applyEdits(id: String, edits: JsonArray, root: JsonObject): JsonObject {
    var current = root
    for (element in edits) {
        val edit = element.jsonObject
        val change = makeChange(id, edit)
        current = when (val target = edit.string("at")) {
            "response" -> current.changed(change)

            "creative" -> editCreative(id, current) { it.changed(change) }

            "metadata" -> editCreative(id, current) { creative ->
                val metadata = creative["metadata"] as? JsonObject ?: JsonObject(emptyMap())
                creative.changed { it["metadata"] = metadata.changed(change) }
            }

            "content" -> editCreative(id, current) { creative ->
                val contents = creative["contents"] as? JsonArray ?: JsonArray(emptyList())
                creative.changed { m ->
                    m["contents"] = JsonArray(contents.map { (it as? JsonObject)?.changed(change) ?: it })
                }
            }

            "slide" -> editCreative(id, current) { creative ->
                val contents = creative["contents"] as? JsonArray ?: JsonArray(emptyList())
                val index = contents.indexOfFirst { (it as? JsonObject)?.string("key") == "slides" }
                if (index < 0) failWith("$id: edit at slide: the creative has no slides entry")
                val entry = contents[index].jsonObject
                val slides = entry["value"] as? JsonArray ?: JsonArray(emptyList())
                val edited = entry.changed { m ->
                    m["value"] = JsonArray(slides.map { (it as? JsonObject)?.changed(change) ?: it })
                }
                creative.changed { m ->
                    m["contents"] = JsonArray(contents.toMutableList().also { it[index] = edited })
                }
            }

            else -> failWith("$id: unknown edit target at=$target")
        }
    }
    return current
}

private fun makeChange(id: String, edit: JsonObject): (MutableMap<String, JsonElement>) -> Unit {
    edit.string("remove")?.let { key -> return { it.remove(key) } }
    edit.string("add")?.let { key ->
        val value = edit["value"] ?: JsonNull
        return { it[key] = value }
    }
    failWith("$id: an edit with neither remove nor add: $edit")
}

private fun JsonObject.changed(change: (MutableMap<String, JsonElement>) -> Unit): JsonObject =
    JsonObject(toMutableMap().also(change))

/** `data[0].creatives[0]`, the creative every fixture scenario is about. */
private fun editCreative(id: String, root: JsonObject, body: (JsonObject) -> JsonObject): JsonObject {
    val data = root["data"] as? JsonArray
    val first = data?.firstOrNull() as? JsonObject
    val creatives = first?.get("creatives") as? JsonArray
    val creative = creatives?.firstOrNull() as? JsonObject
    if (data == null || first == null || creatives == null || creative == null) {
        failWith("$id: the golden file has no data[0].creatives[0]")
    }
    val newCreatives = JsonArray(listOf(body(creative)) + creatives.drop(1))
    val newFirst = first.changed { it["creatives"] = newCreatives }
    return root.changed { it["data"] = JsonArray(listOf<JsonElement>(newFirst) + data.drop(1)) }
}
