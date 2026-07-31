package com.admoai.sdk.e2e

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiConfigurationException
import com.admoai.sdk.exception.AdMoaiValidationException
import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.request.PlacementFormat
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.getContent
import com.admoai.sdk.model.response.hasContents
import com.admoai.sdk.utils.getSkipOffset
import com.admoai.sdk.utils.getVastTagUrl
import com.admoai.sdk.utils.getVastXmlBase64
import com.admoai.sdk.utils.isJourneyAd
import com.admoai.sdk.utils.isSkippable
import com.admoai.sdk.utils.isVastTagDelivery
import com.admoai.sdk.utils.isVastXmlDelivery
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared cross-SDK manifest interpreter.
 *
 * Executes `e2e-scenarios.json`, the cross-SDK scenario manifest. The manifest is the single
 * definition of each declarative scenario; this file is only the Kotlin interpreter for it.
 *
 * Why a manifest: the 37 hand-written journey scenarios exist three times, once per language, and
 * they had ALREADY drifted — this suite's K1 asserted less than iOS's and Flutter's, which is why a
 * pinned-ULID bug failed on only two of the three. Every hand-written scenario is three more
 * chances to diverge. Here a scenario is added once, as data, and all three SDKs execute the same
 * claims.
 *
 * Scope: declarative scenarios only. Anything procedural (log capture, concurrency, retry, TTL
 * sleeps) stays hand-written, because that genuinely differs per platform.
 */
private val manifestJson = Json { ignoreUnknownKeys = true }

private fun loadManifest(): JsonObject {
    val stream = object {}.javaClass.classLoader
        ?.getResourceAsStream("e2e-scenarios.json")
        ?: error("e2e-scenarios.json is not on the test resource path")
    return manifestJson.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun manifestGroup(h: Harness, baseUrl: String, defaultVersion: String) {
    val scenarios = loadManifest()["scenarios"]!!.jsonArray
    for (element in scenarios) {
        val entry = element.jsonObject
        h.scenario(entry.str("id")!!, entry.str("title")!!) {
            runManifestScenario(entry, baseUrl, defaultVersion)
        }
    }
}

private fun reconfigure(baseUrl: String, apiVersion: String?) {
    // configure() rather than initialize(): it swaps config on the running singleton without
    // resetting session state, which is exactly the distinction Wave 2 introduced.
    runBlocking {
        Admoai.getInstance().configure(
            SDKConfig(
                baseUrl = baseUrl,
                apiVersion = apiVersion,
                enableLogging = false,
                defaultLanguage = "en",
                networkClientEngine = CIO.create(),
            ),
        )
    }
    Admoai.getInstance().logSink = { _, _, _ -> }
}

private fun runManifestScenario(entry: JsonObject, baseUrl: String, defaultVersion: String) {
    val request = entry["request"]!!.jsonObject
    val expect = entry["expect"]!!.jsonObject

    // "none" means: no apiVersion at all, so no version header is sent.
    val rawVersion = request.str("apiVersion")
    val version = when (rawVersion) {
        null -> defaultVersion
        "none" -> null
        else -> rawVersion
    }
    reconfigure(baseUrl, version)

    try {
        val builder = Admoai.getInstance().createRequestBuilder()
        for (p in (request["placements"] as JsonArray)) {
            val placement = p.jsonObject
            builder.addPlacement(
                key = placement.str("key")!!,
                count = placement.int("count"),
                format = when (placement.str("format")) {
                    "native" -> PlacementFormat.NATIVE
                    "video" -> PlacementFormat.VIDEO
                    else -> null
                },
                advertiserId = placement.str("advertiserId"),
                templateId = placement.str("templateId"),
            )
        }
        (request["custom"] as? JsonArray)?.forEach {
            val c = it.jsonObject
            builder.addCustomTarget(c.str("key")!!, c.str("value")!!)
        }
        request.str("session")?.let { builder.setSessionId(freshSession(entry.str("id")!!.lowercase())) }
        request.str("journeyOpt")?.let {
            builder.setJourneyOpt(if (it == "in") JourneyOpt.OPT_IN else JourneyOpt.OPT_OUT)
        }

        val outcome = expect.str("outcome")!!

        // --- error outcomes ---------------------------------------------------------------
        if (outcome == "error") {
            val expectedError = expect["error"]!!.jsonObject
            val kind = expectedError.str("kind")!!
            try {
                val built = builder.build()
                runBlocking { Admoai.getInstance().requestAds(built).first() }
                expect(false, "the request is rejected (it succeeded instead)")
            } catch (e: AdMoaiConfigurationException) {
                expect(kind == "local", "a local SDK error is expected for this scenario (got $e)")
                return
            } catch (e: AdMoaiValidationException) {
                expect(kind != "local", "the SDK rejects this before any network call")
                expectedError.int("code")?.let { code ->
                    expect(
                        e.errors.any { it.code == code },
                        "the engine error code is $code (got ${e.errors.map { it.code }})",
                    )
                }
                return
            }
            return
        }

        // --- served / no-ad ---------------------------------------------------------------
        val response = runBlocking { Admoai.getInstance().requestAds(builder.build()).first() }
        val decisions = response.data ?: emptyList()

        expect.int("decisions")?.let {
            expect(
                decisions.size == it,
                "the response carries $it decision(s) (got ${decisions.size})",
            )
        }
        if (expect.bool("decisionsMatchRequest") == true) {
            val requested = (request["placements"] as JsonArray).map { it.jsonObject.str("key")!! }.toSet()
            val returned = decisions.map { it.placement }.toSet()
            expect(requested == returned, "every requested placement is keyed back: $requested vs $returned")
        }

        expect(decisions.isNotEmpty(), "at least one decision is returned")
        val decision = decisions.first()
        val creatives = decision.creatives

        if (outcome == "noAd") {
            expect(creatives.isEmpty(), "the decision is a clean no-ad (got ${creatives.size} creatives)")
            return
        }
        expect(creatives.isNotEmpty(), "a creative is served on ${decision.placement}")

        expect.int("creativesAtMost")?.let {
            expect(creatives.size <= it, "at most $it creatives are returned (got ${creatives.size})")
        }
        if (expect.bool("creativesDistinct") == true && creatives.size > 1) {
            val ids = creatives.mapNotNull { it.metadata?.creativeId }
            expect(ids.toSet().size == ids.size, "the returned creatives are distinct (got $ids)")
        }

        (expect["creative"] as? JsonObject)?.let { assertCreative(creatives.first(), it) }
    } finally {
        // Restore the suite-wide version so later hand-written groups are unaffected.
        reconfigure(baseUrl, defaultVersion)
    }
}

private fun assertCreative(creative: Creative, e: JsonObject) {
    if (e.bool("requireMetadata") == true) {
        val m = creative.metadata
        expect(m != null, "the creative carries a metadata block")
        expect(m!!.adId.isNotEmpty(), "metadata.adId is non-empty")
        expect(m.creativeId.isNotEmpty(), "metadata.creativeId is non-empty")
        expect(m.placementId.isNotEmpty(), "metadata.placementId is non-empty")
        expect(m.templateId.isNotEmpty(), "metadata.templateId is non-empty")
    }
    (e["priorityIn"] as? JsonArray)?.let { allowed ->
        val names = allowed.map { it.jsonPrimitive.content }
        val actual = creative.metadata?.priority?.name?.lowercase()
        expect(actual != null && names.contains(actual), "priority is one of $names (got $actual)")
    }
    if (e.bool("requireAdvertiser") == true) {
        val a = creative.advertiser
        expect(!a.name.isNullOrEmpty(), "advertiser.name is non-empty")
        expect(!a.legalName.isNullOrEmpty(), "advertiser.legalName is non-empty")
        expect(!a.logoUrl.isNullOrEmpty(), "advertiser.logoUrl is non-empty")
    }
    if (e.bool("requireTemplate") == true) {
        expect(!creative.template?.key.isNullOrEmpty(), "template.key is non-empty")
    }
    if (e.bool("requireContents") == true) {
        expect(creative.contents.hasContents(), "the creative carries content fields")
    }
    e.bool("journey")?.let {
        expect(creative.isJourneyAd() == it, "isJourneyAd is $it (got ${creative.isJourneyAd()})")
    }
    e.str("formatEquals")?.let {
        expect(creative.metadata?.format == it, "metadata.format is \"$it\" (got ${creative.metadata?.format})")
    }
    e.str("deliveryEquals")?.let {
        expect(creative.delivery == it, "delivery is \"$it\" (got ${creative.delivery})")
    }
    when (e.str("impressions")) {
        "required" -> expect(
            !creative.tracking.impressions.isNullOrEmpty(),
            "an impression URL is exposed",
        )
        "forbidden" -> expect(
            creative.tracking.impressions.isNullOrEmpty(),
            "NO engine-side impression URL is exposed (VAST owns it; both would double-count)",
        )
    }
    e.int("clicksAtLeast")?.let {
        val clicks = creative.tracking.clicks.orEmpty()
        expect(clicks.size >= it, "at least $it click URL(s) exposed (got ${clicks.size})")
    }
    e.int("videoEventCount")?.let {
        val events = creative.tracking.videoEvents.orEmpty()
        expect(
            events.size == it,
            "exactly $it video event URL(s) exposed (got ${events.size}: ${events.map { v -> v.key }})",
        )
    }
    (e["videoEventKeys"] as? JsonArray)?.let { keys ->
        val actual = creative.tracking.videoEvents.orEmpty().map { it.key }.toSet()
        val wanted = keys.map { it.jsonPrimitive.content }
        expect(actual.containsAll(wanted), "video events include $wanted (got ${actual.sorted()})")
    }
    if (e.bool("requireVastTagUrl") == true) {
        expect(!creative.getVastTagUrl().isNullOrEmpty(), "a VAST tag URL is exposed")
        expect(creative.isVastTagDelivery(), "isVastTagDelivery() agrees with the delivery mode")
    }
    if (e.bool("requireVastXmlDecodes") == true) {
        val b64 = creative.getVastXmlBase64()
        expect(!b64.isNullOrEmpty(), "vast.xmlBase64 is present")
        val xml = String(java.util.Base64.getDecoder().decode(b64), Charsets.UTF_8)
        expect(xml.contains("<VAST"), "the decoded payload is a VAST document")
        expect(creative.isVastXmlDelivery(), "isVastXmlDelivery() agrees with the delivery mode")
    }
    e.bool("metadataIsSkippable")?.let {
        expect(
            creative.metadata?.isSkippable == it,
            "metadata.isSkippable is $it (got ${creative.metadata?.isSkippable})",
        )
    }
    e.int("skipOffsetSecondsEquals")?.let {
        expect(
            creative.metadata?.skipOffsetSeconds == it,
            "metadata.skipOffsetSeconds is $it (got ${creative.metadata?.skipOffsetSeconds})",
        )
    }
    e.bool("helperIsSkippable")?.let {
        // Proves the helper reads ENGINE metadata rather than falling through to content fields —
        // the Wave 2 fix, which no live scenario exercised until now.
        expect(creative.isSkippable() == it, "isSkippable() is $it (got ${creative.isSkippable()})")
    }
    e.str("helperSkipOffsetEquals")?.let {
        expect(
            creative.getSkipOffset() == it,
            "getSkipOffset() is \"$it\" (got ${creative.getSkipOffset()})",
        )
    }
    e.str("endCardModeEquals")?.let {
        expect(
            creative.metadata?.endCardMode == it,
            "metadata.endCardMode is \"$it\" (got ${creative.metadata?.endCardMode})",
        )
    }
}

// §U wire shape (hand-written: needs the built request, not a served response)
//
// Procedural rather than manifest-driven: these assert what the SDK PUTS ON THE WIRE, which the
// declarative interpreter never inspects. getHttpRequestData builds body and headers without
// sending, so the whole group is offline and deterministic.

internal fun wireShapeGroup(h: Harness, defaultVersion: String) {
    h.scenario("U1", "the canonical request body uses the engine's canonical key names") {
        val request = Admoai.getInstance().createRequestBuilder()
            .addPlacement("home")
            .setUserId("u-1")
            .addGeoTarget(5128581)
            .addLocationTarget(40.7, -74.0)
            .addDestinationTarget(41.0, -73.0, 0.8)
            .build()
        val body = Admoai.getInstance().getHttpRequestData(request).body.orEmpty()

        // `minConfidence` is canonical; `min_confidence` is a back-compat alias the engine keeps
        // only for already-fielded SDKs. A new version must not emit the alias.
        expect(body.contains("\"minConfidence\""), "destination uses the canonical camelCase key")
        expect(!body.contains("min_confidence"), "the legacy snake_case alias is NOT emitted")
        expect(body.contains("\"placements\""), "placements are present")
    }

    h.scenario("U2", "decision headers carry version, language and the SDK User-Agent") {
        val request = Admoai.getInstance().createRequestBuilder().addPlacement("home").build()
        val headers = Admoai.getInstance().getHttpRequestData(request).headers

        expect(
            headers["X-Decision-Version"] == defaultVersion,
            "X-Decision-Version is $defaultVersion (got ${headers["X-Decision-Version"]})",
        )
        expect(
            headers["User-Agent"].orEmpty().startsWith("AdMoaiSDK/"),
            "a User-Agent identifying the SDK is sent (got ${headers["User-Agent"]})",
        )
        expect(
            headers["Content-Type"].orEmpty().startsWith("application/json"),
            "Content-Type is application/json (got ${headers["Content-Type"]})",
        )
    }

    h.scenario("U3", "omitted optional fields are absent from the body, not sent as null") {
        val request = Admoai.getInstance().createRequestBuilder().addPlacement("home").build()
        val body = Admoai.getInstance().getHttpRequestData(request).body.orEmpty()

        // A tolerant engine accepts nulls, but emitting them makes every payload larger and
        // muddies "the publisher did not set this" versus "the publisher cleared this".
        expect(!body.contains("\"format\""), "an unset placement format is omitted entirely")
        expect(!body.contains("\"count\""), "an unset count is omitted entirely")
        expect(!body.contains("null"), "no explicit nulls are emitted anywhere in the body")
    }

    h.scenario("U4", "disabling app and device collection removes those blocks from the wire") {
        val request = Admoai.getInstance().createRequestBuilder()
            .addPlacement("home")
            .disableAppCollection()
            .disableDeviceCollection()
            .build()
        val body = Admoai.getInstance().getHttpRequestData(request).body.orEmpty()

        expect(!body.contains("\"app\""), "the app block is absent")
        expect(!body.contains("\"device\""), "the device block is absent")
    }

    h.scenario("U5", "Journey context reaches the wire as top-level camelCase") {
        val session = freshSession("u5")
        val request = Admoai.getInstance().createRequestBuilder()
            .addPlacement("home")
            .setSessionId(session)
            .setJourneyOpt(JourneyOpt.OPT_IN)
            .build()
        val body = Admoai.getInstance().getHttpRequestData(request).body.orEmpty()

        expect(body.contains("\"sessionId\":\"$session\""), "sessionId is top-level camelCase")
        expect(body.contains("\"journeyOpt\":\"in\""), "journeyOpt serializes to the wire literal \"in\"")
    }
}
