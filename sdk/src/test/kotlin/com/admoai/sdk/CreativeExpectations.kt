package com.admoai.sdk

import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.slides
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The creative expectations the shared manifest (`@admoai/sdk-scenarios`) states ONCE and both of
 * its interpreters check: `fixtureScenarios` (offline, [FixtureScenariosTest]) and the live
 * `scenarios` (against an engine, `ManifestRunner`). Same key, same semantics, one implementation,
 * so the offline and the live claim cannot drift apart.
 *
 * Every function throws [AssertionError] (never returns) when the creative disagrees.
 */
internal fun expectationFailure(message: String): Nothing = throw AssertionError(message)

internal fun expectedStrings(value: JsonElement?): List<String> =
    (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

/** `templateKey`: the served `template.key`. */
internal fun checkTemplateKey(label: String, expected: JsonElement?, creative: Creative) {
    val want = (expected as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (want != creative.template?.key) {
        expectationFailure("$label: templateKey expected $want, got ${creative.template?.key}")
    }
}

/** `contentKeys`: the exact SET of plain content keys; slides and matched points are not plain contents. */
internal fun checkContentKeys(label: String, expected: JsonElement?, creative: Creative) {
    val plain = creative.contents.map { it.key }.toSet() - setOf("slides", "matched_points")
    val want = expectedStrings(expected).toSet()
    if (want != plain) expectationFailure("$label: contentKeys expected ${want.sorted()}, got ${plain.sorted()}")
}

/** `styleIn`: a non-empty list the served `template.style` belongs to. */
internal fun checkStyleIn(label: String, expected: JsonElement?, creative: Creative) {
    val allowed = expectedStrings(expected)
    if (allowed.isEmpty()) expectationFailure("$label: styleIn must be a non-empty list of strings")
    val style = creative.template?.style
    if (style == null || style !in allowed) expectationFailure("$label: template.style $style is not one of $allowed")
}

/** `journeyStageKey`: the served creative's `journey.stageKey`. */
internal fun checkJourneyStageKey(label: String, expected: JsonElement?, creative: Creative) {
    val want = (expected as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: expectationFailure("$label: journeyStageKey must be a string")
    if (creative.journey?.stageKey != want) {
        expectationFailure("$label: journey.stageKey expected $want, got ${creative.journey?.stageKey}")
    }
}

/**
 * `metadata`: each listed key must equal the served value; JSON `null` means the key is absent.
 */
internal fun checkMetadata(label: String, expected: JsonElement?, creative: Creative) {
    val metadata = creative.metadata
    val wanted = expected as? JsonObject ?: expectationFailure("$label: metadata expectation is not an object")
    for ((key, value) in wanted) {
        when (key) {
            "effectiveStandardFormatKey" -> expectOptional(label, key, metadata?.effectiveStandardFormatKey, value)

            "effectiveStandardFormatVersion" ->
                expectOptional(label, key, metadata?.effectiveStandardFormatVersion, value)

            "templateSource" -> expectOptional(label, key, metadata?.templateSource, value)

            "assetContext" -> expectOptional(label, key, metadata?.assetContext, value)

            else -> expectationFailure(
                "$label: unknown metadata expect key $key (known: effectiveStandardFormatKey, " +
                    "effectiveStandardFormatVersion, templateSource, assetContext)"
            )
        }
    }
}

/** JSON null expects the property to be `null`; anything else must equal it, type included. */
private fun expectOptional(label: String, key: String, actual: Any?, expected: JsonElement) {
    val primitive = expected as? JsonPrimitive
    when {
        expected is JsonNull ->
            if (actual != null) expectationFailure("$label: metadata.$key is absent, got $actual")

        primitive == null -> expectationFailure("$label: metadata.$key expectation $expected has the wrong type")

        // The JSON type is part of the expectation: "1" never matches 1, nor 1 "1".
        actual is String ->
            if (!primitive.isString || primitive.content != actual) {
                expectationFailure("$label: metadata.$key expected ${primitive.contentOrNull}, got $actual")
            }

        actual is Int ->
            if (primitive.isString || primitive.intOrNull != actual) {
                expectationFailure("$label: metadata.$key expected ${primitive.intOrNull}, got $actual")
            }

        else -> expectationFailure("$label: metadata.$key is $actual, expected $expected")
    }
}

/** `clickKeys`: the exact set of creative-level click tracker keys (order not asserted, duplicates are). */
internal fun checkClickKeys(label: String, expected: JsonElement?, creative: Creative) {
    val want = expectedStrings(expected).sorted()
    val got = creative.tracking.clicks.orEmpty().map { it.key }.sorted()
    if (want != got) expectationFailure("$label: clickKeys expected $want, got $got")
}

private val SLIDE_EXPECT_KEYS = setOf("slideId", "position", "contentKeys", "viewKeys", "clickKeys")

/**
 * `slides`: the ordered, exact list of the served creative's slides, read through the public slide
 * API. Each element states `slideId`, `position` and the exact sets `contentKeys`, `viewKeys`,
 * `clickKeys`.
 */
internal fun checkSlides(label: String, expected: JsonElement?, creative: Creative) {
    val wanted = expected as? JsonArray ?: expectationFailure("$label: slides expectation is not an array")
    val slides = creative.slides
    if (wanted.size !=
        slides.size
    ) {
        expectationFailure("$label: slide count expected ${wanted.size}, got ${slides.size}")
    }
    wanted.forEachIndexed { index, element ->
        val want = element.jsonObject
        val slide = slides[index]
        val at = "$label: slide ${index + 1}"
        want.keys.filterNot {
            it in SLIDE_EXPECT_KEYS
        }.forEach { expectationFailure("$at: unknown slide expect key $it") }
        val wantId = (want["slideId"] as? JsonPrimitive)?.contentOrNull
        if (wantId != slide.slideId) expectationFailure("$at slideId expected $wantId, got ${slide.slideId}")
        val wantPosition = (want["position"] as? JsonPrimitive)?.intOrNull
        if (wantPosition !=
            slide.position
        ) {
            expectationFailure("$at position expected $wantPosition, got ${slide.position}")
        }
        sameSet("$at contentKeys", want["contentKeys"], slide.contents.map { it.key })
        sameSet("$at viewKeys", want["viewKeys"], slide.tracking?.views.orEmpty().map { it.key })
        sameSet("$at clickKeys", want["clickKeys"], slide.tracking?.clicks.orEmpty().map { it.key })
    }
}

private fun sameSet(what: String, expected: JsonElement?, served: List<String>) {
    val want = expectedStrings(expected).toSet()
    if (want != served.toSet()) expectationFailure("$what expected ${want.sorted()}, got ${served.toSet().sorted()}")
}

/**
 * The URL a manifest `slideTracking` beacon (`{slide, view}`, `{slide, click}`, `{click}`,
 * `{custom}`) names on [creative], or null when the creative has no such tracker.
 */
internal fun resolveBeacon(beacon: JsonObject, creative: Creative): String? {
    fun key(name: String) = (beacon[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    (beacon["slide"] as? JsonPrimitive)?.let { slideN ->
        val slide = creative.slides.getOrNull((slideN.intOrNull ?: return null) - 1) ?: return null
        key("view")?.let { k -> return slide.tracking?.views?.firstOrNull { it.key == k }?.url }
        key("click")?.let { k -> return slide.tracking?.clicks?.firstOrNull { it.key == k }?.url }
        return null
    }
    key("click")?.let { k -> return creative.tracking.clicks?.firstOrNull { it.key == k }?.url }
    key("custom")?.let { k -> return creative.tracking.custom?.firstOrNull { it.key == k }?.url }
    return null
}

/**
 * The inverse of [resolveBeacon] for the slide trackers: which `{slide, view|click}` a dispatched
 * URL is, matched against the creative's own slide tracker URLs, or null when it is none of them.
 */
internal fun describeBeacon(url: String, creative: Creative): String? {
    creative.slides.forEachIndexed { index, slide ->
        slide.tracking?.views.orEmpty().firstOrNull {
            it.url == url
        }?.let { return "{slide ${index + 1}, view ${it.key}}" }
        slide.tracking?.clicks.orEmpty().firstOrNull {
            it.url == url
        }?.let { return "{slide ${index + 1}, click ${it.key}}" }
    }
    creative.tracking.clicks.orEmpty().firstOrNull { it.url == url }?.let { return "{click ${it.key}}" }
    creative.tracking.custom.orEmpty().firstOrNull { it.url == url }?.let { return "{custom ${it.key}}" }
    return null
}

internal fun describeExpected(beacon: JsonObject): String {
    val slide = (beacon["slide"] as? JsonPrimitive)?.content
    val view = (beacon["view"] as? JsonPrimitive)?.contentOrNull
    val click = (beacon["click"] as? JsonPrimitive)?.contentOrNull
    val custom = (beacon["custom"] as? JsonPrimitive)?.contentOrNull
    return when {
        slide != null && view != null -> "{slide $slide, view $view}"
        slide != null && click != null -> "{slide $slide, click $click}"
        click != null -> "{click $click}"
        custom != null -> "{custom $custom}"
        else -> beacon.toString()
    }
}

/**
 * A live `slideTracking`'s `expect.beacons`: the first-party beacons the SDK dispatched, in
 * dispatch order, are exactly the expected list. Each dispatched URL is mapped back to the
 * tracker of [creative] it is, so a failure names slides and keys, not opaque URLs.
 */
internal fun checkDispatchedBeacons(
    label: String,
    expected: JsonElement?,
    creative: Creative,
    dispatched: List<String>
) {
    val wanted = (expected as? JsonArray ?: JsonArray(emptyList())).map { describeExpected(it.jsonObject) }
    val got = dispatched.map { describeBeacon(it, creative) ?: "an unknown beacon $it" }
    if (wanted != got) expectationFailure("$label: beacons, in dispatch order, expected $wanted, got $got")
}

/**
 * `engineAcceptsEveryBeacon`: the engine answered every tracking GET that was sent, and every
 * answer is below 400 (a click's 302 is an acceptance). Shared by `pinTracking` and `slideTracking`.
 */
internal fun checkEngineAcceptedEveryBeacon(sentCount: Int, answers: List<Pair<String, Int>>) {
    if (answers.size != sentCount) expectationFailure("the engine answered every beacon (${answers.size}/$sentCount)")
    val rejected = answers.filter { it.second !in 200..399 }
    if (rejected.isNotEmpty()) {
        expectationFailure(
            "the engine accepts every beacon (rejected: ${rejected.map {
                it.second
            }})"
        )
    }
}

/** One validated step of a live `slideTracking`: the single action the runner replays. */
internal sealed interface SlideStep {
    /** `slideView: n` -> `trackSlideView` on the 1-based slide [position]. */
    data class SlideView(val position: Int) : SlideStep

    /** `slideClick: n` -> `trackSlideClick` on the 1-based slide [position]. */
    data class SlideClick(val position: Int) : SlideStep

    /** `fireClick: null` -> the default creative click; `fireClick: "key"` -> that click key. */
    data class FireClick(val key: String?) : SlideStep
}

/**
 * Reads one `slideTracking` step, refusing anything malformed with an [expectationFailure] that
 * shows the step: it must name exactly one action, a slide position must be a positive whole
 * number, and `fireClick` must be JSON null or a string (a number or object is not a click key).
 */
internal fun parseSlideStep(id: String, step: JsonObject): SlideStep {
    if (step.size != 1) expectationFailure("$id: slideTracking step $step must name exactly one action")
    fun position(key: String): Int {
        val value = step.getValue(key) as? JsonPrimitive
        val n = value?.takeIf { !it.isString }?.intOrNull
        if (n == null || n < 1) {
            expectationFailure("$id: slideTracking step $step: $key must be a positive whole number")
        }
        return n
    }
    return when {
        step.containsKey("slideView") -> SlideStep.SlideView(position("slideView"))

        step.containsKey("slideClick") -> SlideStep.SlideClick(position("slideClick"))

        step.containsKey("fireClick") -> when (val v = step.getValue("fireClick")) {
            is JsonNull -> SlideStep.FireClick(null)

            is JsonPrimitive ->
                if (v.isString) SlideStep.FireClick(v.content) else fireClickRefused(id, step)

            else -> fireClickRefused(id, step)
        }

        else -> expectationFailure("$id: slideTracking step $step names no known action")
    }
}

private fun fireClickRefused(id: String, step: JsonObject): Nothing =
    expectationFailure("$id: slideTracking step $step: fireClick must be null or a click key string")
