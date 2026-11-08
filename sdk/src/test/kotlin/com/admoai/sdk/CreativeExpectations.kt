package com.admoai.sdk

import com.admoai.sdk.model.response.Creative
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

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

        actual is String ->
            if (primitive.contentOrNull != actual) {
                expectationFailure("$label: metadata.$key expected ${primitive.contentOrNull}, got $actual")
            }

        actual is Int ->
            if (primitive.intOrNull != actual) {
                expectationFailure("$label: metadata.$key expected ${primitive.intOrNull}, got $actual")
            }

        else -> expectationFailure("$label: metadata.$key is $actual, expected $expected")
    }
}
