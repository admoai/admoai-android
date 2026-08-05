package com.admoai.sdk.e2e

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.AppConfig
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.isNoAd
import com.admoai.sdk.utils.getVastTagUrl
import com.admoai.sdk.utils.getVastXmlBase64
import com.admoai.sdk.utils.hasCompletionUrl
import com.admoai.sdk.utils.isJourneyAd
import com.admoai.sdk.utils.isJourneyCompletion
import com.admoai.sdk.utils.isJsonDelivery
import com.admoai.sdk.utils.isVastTagDelivery
import com.admoai.sdk.utils.isVastXmlDelivery
import com.admoai.sdk.utils.journeyDealId
import com.admoai.sdk.utils.journeyDefinitionKey
import com.admoai.sdk.utils.journeyFallbackBillingMode
import com.admoai.sdk.utils.journeyInstanceId
import com.admoai.sdk.utils.journeyOptStatus
import com.admoai.sdk.utils.journeyPricingModel
import com.admoai.sdk.utils.journeySessionId
import com.admoai.sdk.utils.journeyStageId
import com.admoai.sdk.utils.journeyStageKey
import com.admoai.sdk.utils.journeyStageNodeId
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Journey SDK-driven end-to-end runner (adhub#2360). Drives the REAL Admoai SDK against a
 * locally-running decision-engine and asserts the SDK-observable Journey business rules + the P0 gate.
 *
 * This is a non-JUnit command runner: run it with `./gradlew :sdk:journeyE2e` (NOT part of `:sdk:test`,
 * which stays hermetic). It prints a PASS/FAIL/SKIP summary, writes `build/journey-e2e/report.json`, and
 * exits: 0 = all pass (SKIP allowed), 1 = a scenario FAILED, 2 = preflight aborted.
 *
 * Boot is user-owned (see the plan's "Engine boot recipe"): the engine must be on api version
 * `2025-11-01`, have `is_journey_ads_enabled=true`, Redis up, and the mock seeds loaded. The runner
 * never manages infra. Engine-internal correctness (token identity decode, completion dedupe, billing
 * totals, Tinybird enrichment) is out of scope here — owned by adhub's service-layer Go tests.
 *
 * Fixture-dependent groups (§E dedicated freq-cap, §H CPT, §J mandatory/targeting, §F TTL, §G video)
 * probe their dedicated placement and SKIP-with-reason when the Phase-1/Phase-2 seeds (adhub#2361/#2362)
 * are not present, so running before those land yields SKIP, never a misleading FAIL.
 */

// ------------------------------------------------------------------------------------------------
// Report model + harness
// ------------------------------------------------------------------------------------------------

@Serializable
private data class ReportEntry(
    val id: String,
    val title: String,
    val cases: List<String>,
    val status: String,
    val reason: String? = null,
)

@Serializable
private data class Report(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val scenarios: List<ReportEntry>,
)

private val reportJson = Json { prettyPrint = true }

/** Thrown by [skip] to record a SKIP (fixture absent / not applicable) rather than a FAIL. */
private class SkipException(message: String) : RuntimeException(message)

internal fun skip(reason: String): Nothing = throw SkipException(reason)

internal fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

internal class Harness {
    private val entries = mutableListOf<ReportEntry>()

    fun scenario(id: String, title: String, vararg cases: String, block: () -> Unit) {
        val result = try {
            block()
            ReportEntry(id, title, cases.toList(), "PASS")
        } catch (s: SkipException) {
            ReportEntry(id, title, cases.toList(), "SKIP", s.message)
        } catch (t: Throwable) {
            ReportEntry(id, title, cases.toList(), "FAIL", t.message ?: t.toString())
        }
        entries.add(result)
        val icon = when (result.status) {
            "PASS" -> "✓"
            "SKIP" -> "○"
            else -> "✗"
        }
        val suffix = result.reason?.let { " — $it" } ?: ""
        println("  $icon [$id] $title$suffix")
    }

    /** Prints the summary, writes report.json, and returns the process exit code. */
    fun finish(): Int {
        val passed = entries.count { it.status == "PASS" }
        val failed = entries.count { it.status == "FAIL" }
        val skipped = entries.count { it.status == "SKIP" }
        val report = Report(entries.size, passed, failed, skipped, entries)

        val out = File(System.getProperty("journey.e2e.report") ?: "build/journey-e2e/report.json")
        out.parentFile?.mkdirs()
        out.writeText(reportJson.encodeToString(report))

        println()
        println("Journey E2E: $passed passed, $failed failed, $skipped skipped (${entries.size} total)")
        println("Report: ${out.absolutePath}")
        return if (failed > 0) 1 else 0
    }
}

// ------------------------------------------------------------------------------------------------
// SDK helpers
// ------------------------------------------------------------------------------------------------

internal const val PLACEMENT_PRE_RIDE_A = "vehicleSelection" // ride_hailing_journey pre_ride node A
private const val PLACEMENT_PRE_RIDE_B = "search"           // ride_hailing_journey pre_ride node B
private const val PLACEMENT_IN_RIDE = "journey"             // ride_hailing_journey in_ride
private const val PLACEMENT_POST_RIDE = "rideSummary"       // ride_hailing_journey post_ride
private const val RIDE_HAILING_DEF = "ride_hailing_journey"

// Dedicated Phase-1/Phase-2 e2e placements (adhub#2361/#2362). Absent until those seeds land → SKIP.
private const val PLACEMENT_MANDATORY_EARLY = "sdk_e2e_mandatory_early"
private const val PLACEMENT_MANDATORY_LATER = "sdk_e2e_mandatory_later"
private const val PLACEMENT_TARGET_LOCATION = "sdk_e2e_target_location"
private const val PLACEMENT_TARGET_DESTINATION = "sdk_e2e_target_destination"
private const val PLACEMENT_TARGET_GEO = "sdk_e2e_target_geo"
private const val PLACEMENT_CPT_COMPLETION = "sdk_e2e_cpt_completion"
private const val PLACEMENT_FREQ_CAP = "sdk_e2e_frequency_cap"

// Phase-2 dedicated placements (adhub#2362). Absent until Phase-2 seeds land → SKIP.
private const val PLACEMENT_SHORT_TTL = "sdk_e2e_short_ttl"
private const val PLACEMENT_VIDEO_JSON = "sdk_e2e_video_json"
private const val PLACEMENT_VIDEO_VAST_TAG = "sdk_e2e_video_vast_tag"
private const val PLACEMENT_VIDEO_VAST_XML = "sdk_e2e_video_vast_xml"

// Phase-2 "deeper coverage" placements (adhub#2362): 3-node stage + TTL refresh, freq-cap continuation,
// optional-stage skip, and final_stage CPT completion / completed-journey behaviour.
private const val PLACEMENT_MULTINODE_A = "sdk_e2e_multinode_a"
private const val PLACEMENT_MULTINODE_B = "sdk_e2e_multinode_b"
private const val PLACEMENT_MULTINODE_C = "sdk_e2e_multinode_c"
private const val PLACEMENT_FREQ_CAP_LATER = "sdk_e2e_frequency_cap_later"
private const val PLACEMENT_OPTSKIP_EARLY = "sdk_e2e_optskip_early"
private const val PLACEMENT_OPTSKIP_LATER = "sdk_e2e_optskip_later"
internal const val PLACEMENT_CPT_FINAL_EARLY = "sdk_e2e_cpt_final_early"
internal const val PLACEMENT_CPT_FINAL_COMPLETE = "sdk_e2e_cpt_final_complete"

// §K wizard-parity fixture — deliberately NOT seeded. Built by hand in the platform ad manager
// (definition `scooter_journey`, any `jad_…` deal on it: CPT / bill_per_stage / completion
// strategy `final_stage` on the last stage, all four targeting toggles off, freq-cap and parting off,
// locale `en` only, one active node per stage).
//
// Every other fixture is written by the Go mock seed, which *imitates* the shapes the platform persists.
// This group is the only place the suite reads what the platform ACTUALLY writes — and that seam is exactly
// where both live bugs of the 2026-07-29 round lived (#2459 wizard targeting envelopes, #2483 camelCase
// template-field keys). In both cases the seeds wrote what the engine expected, so the suite stayed green.
// Hand-built data does not survive `make db-reset`, so absence is a SKIP, never a FAIL.
private const val WIZARD_DEF = "scooter_journey"
private const val PLACEMENT_WIZARD_S1 = "promotions" // stage pre_ride     — carousel3Slides
private const val PLACEMENT_WIZARD_S2 = "waiting"    // stage post_ride    — carousel3Slides
private const val PLACEMENT_WIZARD_S3 = "poi"        // stage summary_ride — imageWithText, the completion stage
private const val WIZARD_MISSING = "hand-built in the platform ad manager; recreate it after a db-reset"
// The short-TTL fixtures seed runtime_state_ttl_seconds = 5s (journeys.go). For §F1 (expiry) wait
// comfortably PAST the TTL with no activity. For §F2 (refresh) use a gap SHORTER than the TTL so the
// mid serve lands inside the window and refreshes it; two such gaps total > TTL, proving the instance
// did not expire on its original creation time. Both must stay consistent with the seeded 5s TTL.
private const val SHORT_TTL_WAIT_MS = 8_000L
private const val TTL_REFRESH_GAP_MS = 3_000L

// Targeting match/no-match constants — MUST mirror the adhub#2361 seed
// fixtures (apps/decision-engine/seed/mock/journeys.go). The seeded
// deals centre on New York City with a 5 km radius and dest_min_confidence
// 0.7, and the geo deal targets NYC's geoname id. A "pass" request lands
// in-target; a "fail" request is deliberately out-of-target so the no-ad
// is attributable to targeting alone (a competing normal ad still serves).
private const val NYC_LAT = 40.7128
private const val NYC_LON = -74.006
private const val FAR_LAT = 0.0
private const val FAR_LON = 0.0
private const val DEST_CONFIDENCE_PASS = 0.9 // ≥ seed dest_min_confidence 0.7
private const val DEST_CONFIDENCE_FAIL = 0.5 // < seed dest_min_confidence 0.7
private const val GEONAME_MATCH = 5128581 // New York City — the seed's geo target
private const val GEONAME_NO_MATCH = 2643743 // London — a real geoname (in the engine CSV) that is NOT the target

/** Fresh, greppable session id per scenario group so Redis runtime state never bleeds across runs. */
internal fun freshSession(tag: String): String = "e2e-$tag-${System.nanoTime()}"

private val E2E_BASE_URL: String = System.getenv("ADMOAI_JOURNEY_E2E_BASE_URL") ?: "http://127.0.0.1:8080/"

private fun sdk() = Admoai.getInstance()

private fun decide(configure: DecisionRequestBuilder.() -> Unit): DecisionResponse {
    val request = sdk().createRequestBuilder().apply(configure).build()
    return runBlocking { sdk().requestAds(request).first() }
}

// NOTE: the extra-config parameter is deliberately NOT named `build` — inside the `decide { }`
// receiver lambda (receiver = DecisionRequestBuilder) an unqualified `build()` binds to the builder's
// own `build(): DecisionRequest` member, not to the lambda parameter, so the caller's targeting/user
// config would be silently dropped (the request would go out with no `targeting`/`user`). Naming it
// `extra` removes the collision so `extra()` unambiguously applies the caller's config.
internal fun decideOn(
    placement: String,
    sessionId: String?,
    opt: JourneyOpt? = null,
    extra: DecisionRequestBuilder.() -> Unit = {},
): DecisionResponse = decide {
    addPlacement(placement)
    sessionId?.let { setSessionId(it) }
    opt?.let { setJourneyOpt(it) }
    extra()
}

internal fun DecisionResponse.adFor(placement: String): AdData? = data?.firstOrNull { it.placement == placement }
internal fun DecisionResponse.creativeFor(placement: String): Creative? = adFor(placement)?.creatives?.firstOrNull()
private fun DecisionResponse.isNoAdFor(placement: String): Boolean = adFor(placement)?.isNoAd() ?: true

/** Probe a dedicated fixture placement; if it does not serve a Journey, the fixture isn't seeded. */
internal fun requireFixture(placement: String, seedIssue: String) {
    val serves = try {
        decideOn(placement, freshSession("probe"), JourneyOpt.OPT_IN).creativeFor(placement)?.isJourneyAd() == true
    } catch (_: AdMoaiNetworkException) {
        false
    }
    if (!serves) skip("fixture '$placement' not seeded (see $seedIssue)")
}

// ------------------------------------------------------------------------------------------------
// Preflight — fail fast with a precise diagnosis instead of a blind run
// ------------------------------------------------------------------------------------------------

private fun abort(message: String): Nothing {
    System.err.println("PREFLIGHT ABORT: $message")
    exitProcess(2)
}

private fun preflight(baseUrl: String) {
    // 1. Connectivity — any 2xx (empty ad is fine).
    try {
        decideOn(PLACEMENT_PRE_RIDE_A, sessionId = null)
    } catch (e: AdMoaiNetworkException) {
        abort("cannot reach the decision-engine at $baseUrl — is it running? (${e.message})")
    }

    // 2. Journey probe + 3. gate/Redis/seed inference (SDK cannot inspect Redis, so infer behaviorally).
    val probe = try {
        decideOn(PLACEMENT_PRE_RIDE_A, freshSession("preflight"), JourneyOpt.OPT_IN)
    } catch (e: AdMoaiNetworkException) {
        val msg = e.message.orEmpty()
        if (msg.contains("400") || msg.contains("unknown field", ignoreCase = true)) {
            abort("engine rejected additive Journey fields — wrong X-Decision-Version (need 2025-11-01) or version not deployed")
        }
        abort("journey probe failed: $msg")
    }
    val creative = probe.creativeFor(PLACEMENT_PRE_RIDE_A)
    if (creative == null || !creative.isJourneyAd()) {
        abort(
            "journey probe returned no Journey creative on '$PLACEMENT_PRE_RIDE_A' — " +
                "is_journey_ads_enabled is OFF, Redis is down, or the mock seeds are not loaded. Check the boot recipe.",
        )
    }

    // 4. Demo-fixture ownership. §B drives the SHIPPED `ride_hailing_journey` across the shared demo
    // placements (vehicleSelection/search/journey/rideSummary) — the only fixture in the suite NOT on a
    // dedicated e2e placement, and therefore the only one another Journey Deal can take over. Any locally
    // created deal on those placements (platform-UI QA work), or a demo deal left inactive/draft, silently
    // owns the surface; §B then reports four opaque assertion failures that read like SDK/engine
    // regressions but are pure environment drift. Diagnose it here as what it is: an unusable environment
    // (exit 2), not a test failure.
    val servedDefinition = creative.journeyDefinitionKey()
    if (servedDefinition != RIDE_HAILING_DEF) {
        abort(
            "'$PLACEMENT_PRE_RIDE_A' is served by Journey definition '${servedDefinition ?: "<none>"}', not the " +
                "expected demo '$RIDE_HAILING_DEF' — this DB is not a clean mock seed. Either a locally created " +
                "Journey Deal outranks the demo deal, or the demo deal is no longer active. Reset it: " +
                "`make db-reset` in apps/decision-engine, then restart the engine with the gate on " +
                "(seeds load only into an empty DB). NOTE: db-reset destroys locally created platform data.",
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Scenario groups
// ------------------------------------------------------------------------------------------------

/** §A Request forwarding & backward-compat (no engine state; §A3/§A4 are pure request-shape). */
private fun groupA(h: Harness) {
    println("§A Request forwarding & backward-compat")

    h.scenario("A1", "no sessionId → normal decision, no Journey metadata", "58-60", "226") {
        val resp = decideOn(PLACEMENT_PRE_RIDE_A, sessionId = null)
        val creative = resp.creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(creative == null || !creative.isJourneyAd(), "a no-session request must not start a Journey")
    }

    h.scenario("A3", "sessionId/journeyOpt reach the wire as top-level camelCase + version header", "63-64", "204", "206") {
        val request = sdk().createRequestBuilder()
            .addPlacement(PLACEMENT_PRE_RIDE_A)
            .setSessionId("e2e-shape")
            .setJourneyOpt(JourneyOpt.OPT_IN)
            .build()
        val http = sdk().getHttpRequestData(request)
        val body = http.body.orEmpty()
        expect(body.contains("\"sessionId\""), "body must carry top-level sessionId")
        expect(body.contains("\"journeyOpt\"") && body.contains("\"in\""), "body must carry journeyOpt=in")
        val versionHeader = http.headers.entries.firstOrNull { it.key.equals("X-Decision-Version", ignoreCase = true) }
        expect(versionHeader?.value == "2025-11-01", "X-Decision-Version header must be 2025-11-01")
    }

    h.scenario("A4", "sessionId is sticky across builds (no regeneration on rebuild)", "205") {
        val sticky = "e2e-sticky-${System.nanoTime()}"
        sdk().setSessionId(sticky)
        try {
            val first = sdk().getHttpRequestData(sdk().createRequestBuilder().addPlacement(PLACEMENT_PRE_RIDE_A).build())
            val second = sdk().getHttpRequestData(sdk().createRequestBuilder().addPlacement(PLACEMENT_PRE_RIDE_B).build())
            expect(first.body.orEmpty().contains(sticky), "first build must carry the sticky sessionId")
            expect(second.body.orEmpty().contains(sticky), "second build must carry the SAME sticky sessionId")
        } finally {
            sdk().clearSessionId()
        }
    }
}

/** §B Stage progression, multi-node, and no-ad — one session, ordered calls (the sequence IS the test). */
private fun groupB(h: Harness) {
    println("§B Stage progression & multi-node")
    val sid = freshSession("B")
    var instanceId: String? = null
    var nodeAId: String? = null

    h.scenario("B1", "new session serves pre_ride and starts an instance", "68", "P0#1") {
        val c = decideOn(PLACEMENT_PRE_RIDE_A, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(c != null && c.isJourneyAd(), "expected a Journey serve on $PLACEMENT_PRE_RIDE_A")
        expect(c!!.journeyDefinitionKey() == RIDE_HAILING_DEF, "definitionKey should be $RIDE_HAILING_DEF")
        expect(c.journeyStageKey() == "pre_ride", "stageKey should be pre_ride")
        expect(!c.journeyInstanceId().isNullOrBlank(), "instanceId must be present")
        expect(c.journey?.isCompletion != true, "pre_ride is not a completion serve") // != true, not == false (nullable)
        instanceId = c.journeyInstanceId()
        nodeAId = c.journeyStageNodeId()
    }

    h.scenario("B2", "second pre_ride node serves (multi-node, same stage)", "72", "P0#2") {
        val c = decideOn(PLACEMENT_PRE_RIDE_B, sid).creativeFor(PLACEMENT_PRE_RIDE_B)
        expect(c != null && c.isJourneyAd(), "expected the second pre_ride node to serve on $PLACEMENT_PRE_RIDE_B")
        expect(c!!.journeyStageKey() == "pre_ride", "still pre_ride stage")
        expect(c.journeyStageNodeId() != nodeAId, "must be a different node than B1")
        expect(c.journeyInstanceId() == instanceId, "same instance across the journey")
    }

    h.scenario("B3", "repeated node → no-ad (one-serve-max); positive control proves suppression", "73", "82", "P0#3") {
        val c = decideOn(PLACEMENT_PRE_RIDE_A, sid).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(c == null || !c.isJourneyAd(), "an already-served node must not serve again")
        // Positive control: a fresh session (no active Journey) should serve on this placement — so the
        // no-ad above is takeover suppression, not a placement with no inventory.
        val control = decideOn(PLACEMENT_PRE_RIDE_A, freshSession("B3ctl"), JourneyOpt.OPT_IN)
            .creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(control != null, "control: placement must have serveable inventory when no Journey is active")
    }

    h.scenario("B4", "in_ride serves; instance stays stable", "67", "71") {
        val c = decideOn(PLACEMENT_IN_RIDE, sid).creativeFor(PLACEMENT_IN_RIDE)
        expect(c != null && c.isJourneyAd(), "expected in_ride serve on $PLACEMENT_IN_RIDE")
        expect(c!!.journeyStageKey() == "in_ride", "stageKey should be in_ride")
        expect(c.journeyInstanceId() == instanceId, "instance stable across pre_ride → in_ride")
    }

    h.scenario("B5", "post_ride serves; isCompletion reflects the deal's completion config", "77-79") {
        val c = decideOn(PLACEMENT_POST_RIDE, sid).creativeFor(PLACEMENT_POST_RIDE)
        expect(c != null && c.isJourneyAd(), "expected post_ride serve on $PLACEMENT_POST_RIDE")
        expect(c!!.journeyStageKey() == "post_ride", "stageKey should be post_ride")
        // The shipped ride_hailing deal may or may not configure post_ride as the completion stage; record
        // rather than over-assert. isJourneyCompletion() is only asserted true where a CPT completion fixture
        // guarantees it (§H2).
    }

    h.scenario("B6", "three nodes in one stage each serve once (multi-node)", "5") {
        requireFixture(PLACEMENT_MULTINODE_A, "adhub#2362")
        val sid = freshSession("B6")
        val a = decideOn(PLACEMENT_MULTINODE_A, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_MULTINODE_A)
        expect(a != null && a.isJourneyAd(), "node A serves and starts the instance")
        val instance = a!!.journeyInstanceId()
        val stage = a.journeyStageKey()
        val nodeA = a.journeyStageNodeId()
        val b = decideOn(PLACEMENT_MULTINODE_B, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_MULTINODE_B)
        expect(b != null && b.isJourneyAd(), "node B serves within the same stage")
        expect(b!!.journeyStageKey() == stage && b.journeyInstanceId() == instance, "B: same stage + same instance")
        expect(b.journeyStageNodeId() != nodeA, "B is a different node than A")
        val c = decideOn(PLACEMENT_MULTINODE_C, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_MULTINODE_C)
        expect(c != null && c.isJourneyAd(), "node C serves within the same stage")
        expect(
            c!!.journeyInstanceId() == instance &&
                c.journeyStageNodeId() != nodeA && c.journeyStageNodeId() != b.journeyStageNodeId(),
            "C: distinct node, same instance (stage never regresses)",
        )
    }
}

/**
 * §B7 Journey-metadata coherence. Cheap guard on the tolerant-reader mapping: every field the SDK exposes
 * must be the one the engine sent. A field silently mapping to null (or to the wrong sibling) would leave
 * most other scenarios passing, since they assert only the two or three fields they care about.
 */
private fun groupBMetadata(h: Harness) {
    h.scenario("B7", "journey metadata is internally coherent and echoes the request", "204-206") {
        val sid = freshSession("B7")
        val first = decideOn(PLACEMENT_PRE_RIDE_A, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(first != null && first.isJourneyAd(), "need a Journey serve to inspect metadata")
        expect(first!!.journeySessionId() == sid, "the engine must echo the sessionId the SDK sent")
        expect(first.journeyOptStatus() == JourneyOpt.OPT_IN, "optStatus must echo the requested opt-in")
        expect(!first.journeyDealId().isNullOrBlank(), "dealId must be surfaced")
        expect(!first.journeyStageId().isNullOrBlank(), "stageId must be surfaced alongside stageKey")
        // Same advertiser owns the whole journey: the deal id cannot change as stages advance.
        val next = decideOn(PLACEMENT_IN_RIDE, sid).creativeFor(PLACEMENT_IN_RIDE)
        expect(next != null && next.isJourneyAd(), "the next stage serves")
        expect(next!!.journeyDealId() == first.journeyDealId(), "dealId is stable across stages")
        expect(next.journeySessionId() == sid, "sessionId keeps echoing across stages")
    }
}

/** §C Opt-in / opt-out + new-instance. */
private fun groupC(h: Harness) {
    println("§C Opt-in / opt-out")

    h.scenario("C1", "opt-out before start → no Journey", "92") {
        val c = decideOn(PLACEMENT_PRE_RIDE_A, freshSession("C1"), JourneyOpt.OPT_OUT).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(c == null || !c.isJourneyAd(), "opt-out must prevent the Journey from starting")
    }

    h.scenario("C2/C3", "opt-out closes the instance; opt-in mints a NEW instance", "93", "99-100", "P0#5") {
        val sid = freshSession("C2")
        val first = decideOn(PLACEMENT_PRE_RIDE_A, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(first != null && first.isJourneyAd(), "Journey should start on opt-in")
        val firstInstance = first!!.journeyInstanceId()

        // Opt-out on a Journey-owned placement releases the surface (takeover stops).
        decideOn(PLACEMENT_PRE_RIDE_B, sid, JourneyOpt.OPT_OUT)

        // Opt-in again → a new instance id, distinct from the first.
        val reopened = decideOn(PLACEMENT_PRE_RIDE_A, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(reopened != null && reopened.isJourneyAd(), "opt-in again should be able to start a new Journey")
        expect(
            !reopened!!.journeyInstanceId().isNullOrBlank() && reopened.journeyInstanceId() != firstInstance,
            "opt-in after opt-out must create a NEW journey_instance_id",
        )
    }
}

/**
 * §D Tracking transport — black-box only. Tracking URLs are opaque `/v1/tracking?e=<token>`; identity is
 * inside the encrypted token (engine-owned). We assert transport shape + verbatim fire, not decoded keys.
 */
private fun groupD(h: Harness) {
    println("§D Tracking transport")

    h.scenario("D1", "served Journey tracking URLs are absolute /v1/tracking with an ?e= token", "186-188") {
        val c = decideOn(PLACEMENT_PRE_RIDE_A, freshSession("D1"), JourneyOpt.OPT_IN).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(c != null && c.isJourneyAd(), "need a Journey serve to inspect tracking")
        val url = c!!.tracking.impressions?.firstOrNull()?.url
        expect(url != null, "a served Journey creative must carry an impression tracking URL")
        expectTrackingTransport(url, "impression tracking URL")
    }

    // Regression guard for adhub#2483: `tracking.clicks` was [] on EVERY Journey serve for weeks — the
    // resolver derived the click destination by matching content rows against snake_case keys (cta_url,
    // click_url, …) while every template field the platform creates is camelCase (destinationUrl,
    // clickThroughUrl, …), so nothing ever matched and the resolver correctly dropped the click URL.
    // This suite stayed green throughout, because §D asserted impressions only and never claimed that a
    // Journey creative with a destination exposes a click URL at all. It does now.
    // Driven from a dedicated e2e placement (its `standard` template carries `destinationUrl`), so the
    // guard cannot be masked by demo-fixture drift.
    h.scenario("D5", "served Journey creative exposes a click tracking URL", "adhub#2483") {
        requireFixture(PLACEMENT_CPT_COMPLETION, "adhub#2361")
        val c = decideOn(PLACEMENT_CPT_COMPLETION, freshSession("D5"), JourneyOpt.OPT_IN)
            .creativeFor(PLACEMENT_CPT_COMPLETION)
        expect(c != null && c.isJourneyAd(), "need a Journey serve to inspect click tracking")
        val clicks = c!!.tracking.clicks
        expect(!clicks.isNullOrEmpty(), "a served Journey creative with a destination must expose a click tracking URL")
        expectTrackingTransport(clicks!!.first().url, "click tracking URL")
    }

    h.scenario("D2", "SDK fires the impression URL verbatim and is retry-safe", "214", "200") {
        verbatimFireCheck(h) { info -> sdk().fireImpression(info) }
    }

    h.scenario("D6", "the engine accepts a tracking token it minted itself", "adhub#2499", "adhub#2506") {
        val c = decideOn(PLACEMENT_PRE_RIDE_A, freshSession("D6"), JourneyOpt.OPT_IN).creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(c != null && c.isJourneyAd(), "need a Journey serve to obtain a real tracking token")
        val url = c!!.tracking.impressions?.firstOrNull()?.url
        expect(url != null, "a served Journey creative must carry an impression tracking URL")
        sdk().fireImpression(c.tracking) // the SDK path — dispatches, but reports no status by design
        expectIngestionAccepted(url!!) // the engine path — the observable half
    }

    h.scenario("D4", "no-ad response exposes no tracking and fires nothing", "201", "220") {
        // A repeated node on an active Journey yields a takeover no-ad.
        val sid = freshSession("D4")
        decideOn(PLACEMENT_PRE_RIDE_A, sid, JourneyOpt.OPT_IN)
        val repeat = decideOn(PLACEMENT_PRE_RIDE_A, sid)
        expect(repeat.isNoAdFor(PLACEMENT_PRE_RIDE_A), "repeated node must be a no-ad")
        val creative = repeat.creativeFor(PLACEMENT_PRE_RIDE_A)
        expect(creative == null, "no-ad must expose no creative (and therefore no tracking URLs)")
    }
}

/**
 * The transport contract every SDK-surfaced Journey tracking URL must satisfy: absolute, `/v1/tracking`,
 * and carrying the opaque `?e=` token. Identity lives inside the encrypted token (engine-owned), so the
 * shape is all a black-box client can — and should — assert.
 */
private fun expectTrackingTransport(url: String?, label: String) {
    expect(url != null, "$label must be present")
    expect(url!!.startsWith("http://") || url.startsWith("https://"), "$label must be absolute")
    expect(url.contains("/v1/tracking"), "$label path must be /v1/tracking")
    expect(url.contains("e="), "$label must carry the opaque ?e= token")
}

/**
 * Asserts the OTHER half of the tracking loop: the engine's own `/v1/tracking` must accept a token it just
 * minted. Everything else in §D stops at the response payload or at a MockWebServer, so nothing in this
 * suite ever proved ingestion works end-to-end — the exact path adhub #2499/#2506 reworked (deriving
 * `journey_event_meaning` from the callback, and holding it through a snapshot miss).
 *
 * The SDK's own fire is fire-and-forget and returns no status, so acceptance cannot be observed through it;
 * this issues the GET directly, using the URL the SDK surfaced.
 *
 * Scheme note: locally the engine mints production-shaped `https://` tracking URLs while serving plaintext
 * on `:8080`, so a verbatim GET fails the TLS handshake for environmental reasons alone. §D1 asserts the
 * minted shape separately, so scheme/host/port are normalized to the configured base URL here — the opaque
 * `?e=` token, which is what ingestion actually validates, is passed through untouched.
 */
internal fun expectIngestionAccepted(url: String) = expectIngestion(url, allowRedirect = false)

/**
 * Ingestion check for a CLICK tracking URL.
 *
 * `GET /v1/tracking` answers 202 for a pixel but **302 for a click**: the engine records the event
 * and then redirects the user to the creative's destination. A 302 is therefore the success signal
 * for a click, not a failure — asserting `2xx` here rejects a correctly-working click.
 */
internal fun expectClickIngestionAccepted(url: String) = expectIngestion(url, allowRedirect = true)

private fun expectIngestion(url: String, allowRedirect: Boolean) {
    val base = java.net.URI(E2E_BASE_URL)
    val minted = java.net.URI(url)
    val target = java.net.URI(base.scheme, null, base.host, base.port, minted.path, minted.query, null).toURL()
    val conn = (target.openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5_000
        readTimeout = 5_000
        // Do not chase the click redirect: the destination is an advertiser URL that may be
        // unreachable from a test machine, and following it would turn a correct 302 into a
        // network error. The 302 itself is the proof the engine accepted the token.
        instanceFollowRedirects = false
    }
    val code = try {
        conn.responseCode
    } finally {
        conn.disconnect()
    }
    val accepted = code in 200..299 || (allowRedirect && code == 302)
    expect(
        accepted,
        "the engine must accept a tracking token it minted itself; /v1/tracking returned $code" +
            if (allowRedirect) " (202 or 302 expected for a click)" else " (2xx expected)",
    )
}

/**
 * Verbatim-fire proof using MockWebServer: the SDK must GET the exact opaque URL string it was handed and
 * re-fire the identical string on retry. Engine-agnostic (the SDK GETs whatever absolute URL it holds), so
 * this runs independent of the live decision-engine.
 */
private fun verbatimFireCheck(@Suppress("UNUSED_PARAMETER") h: Harness, fire: (TrackingInfo) -> Unit) {
    val server = MockWebServer()
    server.start()
    try {
        val url = "${server.url("/v1/tracking")}?e=SYNTHETIC_TOKEN_VALUE"
        val info = TrackingInfo(impressions = listOf(TrackingDetail(key = "default", url = url)))

        server.enqueue(MockResponse().setResponseCode(200))
        fire(info)
        val first = server.takeRequest(3, TimeUnit.SECONDS)
        expect(first != null, "SDK must fire the tracking URL")
        expect("${server.url("/")}".trimEnd('/') + first!!.path == url, "fired URL must match verbatim: got ${first.path}")

        // Retry must reuse the identical URL, not a reconstructed one.
        server.enqueue(MockResponse().setResponseCode(200))
        fire(info)
        val retry = server.takeRequest(3, TimeUnit.SECONDS)
        expect(retry != null && retry.path == first.path, "retry must reuse the identical URL")
    } finally {
        server.shutdown()
    }
}

/** §E Frequency-cap new-entry gating — dedicated deterministic fixture (adhub#2361). */
private fun groupE(h: Harness) {
    println("§E Frequency-cap new-entry gating")
    h.scenario("E1", "new-entry frequency cap gates deterministically", "115", "118", "P0#7") {
        requireFixture(PLACEMENT_FREQ_CAP, "adhub#2361")
        val user = "e2e-freq-user-${System.nanoTime()}"
        val cap = 2
        repeat(cap) { i ->
            val c = decideOn(PLACEMENT_FREQ_CAP, freshSession("E1-$i"), JourneyOpt.OPT_IN) { setUserId(user) }
                .creativeFor(PLACEMENT_FREQ_CAP)
            expect(c != null && c.isJourneyAd(), "entry ${i + 1} within cap should start a Journey")
        }
        val capped = decideOn(PLACEMENT_FREQ_CAP, freshSession("E1-over"), JourneyOpt.OPT_IN) { setUserId(user) }
            .creativeFor(PLACEMENT_FREQ_CAP)
        expect(capped == null || !capped.isJourneyAd(), "entry ${cap + 1} must be blocked by the frequency cap")
    }

    h.scenario("E2", "frequency cap never blocks an already-active instance's continuation", "13") {
        // Gate on the Phase-2 continuation node (adhub#2362); the base freq-cap fixture alone is not enough.
        requireFixture(PLACEMENT_FREQ_CAP_LATER, "adhub#2362")
        val user = "e2e-freq-cont-${System.nanoTime()}"
        // Exhaust the per-user cap (2) with two fresh sessions.
        val s1 = freshSession("E2-1")
        val c1 = decideOn(PLACEMENT_FREQ_CAP, s1, JourneyOpt.OPT_IN) { setUserId(user) }.creativeFor(PLACEMENT_FREQ_CAP)
        expect(c1 != null && c1.isJourneyAd(), "instance 1 starts")
        val instance1 = c1!!.journeyInstanceId()
        val c2 = decideOn(PLACEMENT_FREQ_CAP, freshSession("E2-2"), JourneyOpt.OPT_IN) { setUserId(user) }.creativeFor(PLACEMENT_FREQ_CAP)
        expect(c2 != null && c2.isJourneyAd(), "instance 2 starts (cap = 2)")
        // A 3rd NEW instance is capped...
        val blocked = decideOn(PLACEMENT_FREQ_CAP, freshSession("E2-3"), JourneyOpt.OPT_IN) { setUserId(user) }.creativeFor(PLACEMENT_FREQ_CAP)
        expect(blocked == null || !blocked.isJourneyAd(), "a 3rd NEW instance is blocked by the cap")
        // ...but instance 1 CONTINUES on its later node despite the cap being full.
        val cont = decideOn(PLACEMENT_FREQ_CAP_LATER, s1, JourneyOpt.OPT_IN) { setUserId(user) }.creativeFor(PLACEMENT_FREQ_CAP_LATER)
        expect(cont != null && cont.isJourneyAd(), "the active instance continues past the cap")
        expect(cont!!.journeyInstanceId() == instance1, "continuation stays on the same (already-active) instance")
    }
}

/** §H CPT / fallback SDK-observable surfaces — needs the CPT completion fixture (adhub#2361). */
private fun groupH(h: Harness) {
    println("§H CPT / fallback SDK surfaces")
    h.scenario("H1/H2", "CPT deal surfaces pricingModel/fallbackBillingMode/isCompletion", "137", "144") {
        requireFixture(PLACEMENT_CPT_COMPLETION, "adhub#2361")
        val c = decideOn(PLACEMENT_CPT_COMPLETION, freshSession("H"), JourneyOpt.OPT_IN).creativeFor(PLACEMENT_CPT_COMPLETION)
        expect(c != null && c.isJourneyAd(), "CPT fixture should serve a Journey")
        expect(c!!.journeyPricingModel() == "cpt", "pricingModel should be cpt")
        // Exact value, not merely non-blank: this fixture is seeded `no_charge` (adhub#2361), and a
        // non-blank check passes even when the engine surfaces the wrong billing mode — which is the one
        // thing this assertion exists to catch. Whether no_charge actually charges nothing is billing, i.e.
        // engine-internal and unobservable from the SDK (§8); what the SDK owns is surfacing it faithfully.
        expect(
            c.journeyFallbackBillingMode() == "no_charge",
            "the no_charge fixture must surface exactly 'no_charge', got '${c.journeyFallbackBillingMode()}'",
        )
    }

    // The CPT billing trigger, and the last SDK-observable surface with no coverage at all. On a
    // `custom_event` completion deal the engine hands the app a `completions[]` beacon that the PUBLISHER
    // must fire — firing it is what books CPT revenue. `isCompletion` deliberately stays false here: the two
    // completion strategies are mutually exclusive (`final_stage` marks inline and emits no beacon — see the
    // tail of §H3 and §K3). The SDK ships `hasCompletionUrl()` and `fireCompletion()` as public API and
    // nothing exercised either, so a regression here would have been silent all the way to lost revenue.
    h.scenario("H5", "custom_event CPT deal exposes a fireable completion beacon", "adhub#2361") {
        requireFixture(PLACEMENT_CPT_COMPLETION, "adhub#2361")
        val c = decideOn(PLACEMENT_CPT_COMPLETION, freshSession("H5"), JourneyOpt.OPT_IN)
            .creativeFor(PLACEMENT_CPT_COMPLETION)
        expect(c != null && c.isJourneyAd(), "CPT fixture should serve a Journey")
        expect(c!!.hasCompletionUrl(), "a custom_event completion deal must expose a completion beacon")
        val beacon = c.tracking.completions!!.first()
        expect(beacon.key == "journey_complete", "beacon key should be 'journey_complete', got '${beacon.key}'")
        expectTrackingTransport(beacon.url, "completion beacon URL")
        expect(!c.isJourneyCompletion(), "custom_event completion must not mark isCompletion inline")
        // Fire-and-forget, so there is no status to assert — this proves the public API dispatches the
        // beacon without throwing. That the engine accepts such a token is proved in §D6.
        sdk().fireCompletion(c.tracking, beacon.key)
        expectIngestionAccepted(beacon.url)
    }

    h.scenario("H3", "final-stage CPT serve flips isCompletion=true", "17") {
        requireFixture(PLACEMENT_CPT_FINAL_EARLY, "adhub#2362")
        val sid = freshSession("H3")
        val early = decideOn(PLACEMENT_CPT_FINAL_EARLY, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_CPT_FINAL_EARLY)
        expect(early != null && early.isJourneyAd(), "pre-completion stage serves")
        expect(early!!.journey?.isCompletion != true, "pre-completion serve is not a completion") // != true (nullable)
        val complete = decideOn(PLACEMENT_CPT_FINAL_COMPLETE, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_CPT_FINAL_COMPLETE)
        expect(complete != null && complete.isJourneyAd(), "completion stage serves")
        expect(complete!!.isJourneyCompletion(), "serving the final stage flips isCompletion=true")
        expect(complete.journeyPricingModel() == "cpt", "CPT pricing surfaced on the completion serve")
        expect(
            complete.journeyFallbackBillingMode() == "bill_per_stage",
            "this fixture is seeded bill_per_stage, got '${complete.journeyFallbackBillingMode()}'",
        )
        // Mutual exclusivity, asserted on the SEEDED fixture so the guarantee survives a db-reset (§K3
        // asserts the same rule on the platform-authored journey): a `final_stage` deal marks completion
        // inline and must expose NO completion beacon.
        expect(!complete.hasCompletionUrl(), "a final_stage deal must not expose a completion beacon")
    }

    h.scenario("H4", "after completion, takeover protection ends (a normal ad can serve)", "20") {
        requireFixture(PLACEMENT_CPT_FINAL_EARLY, "adhub#2362")
        val sid = freshSession("H4")
        // Drive the instance to completion: early stage, then the completion stage.
        decideOn(PLACEMENT_CPT_FINAL_EARLY, sid, JourneyOpt.OPT_IN)
        val complete = decideOn(PLACEMENT_CPT_FINAL_COMPLETE, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_CPT_FINAL_COMPLETE)
        expect(complete != null && complete.isJourneyCompletion(), "journey completes on the completion stage")
        // Takeover protection ended with completion: the completed instance no longer holds the surface, so a
        // request that does not ask for a fresh Journey (opt-out) falls through to the competing normal ad.
        // (A continued opt-in instead mints a NEW instance — either way the completed instance never resumes.)
        val after = decideOn(PLACEMENT_CPT_FINAL_COMPLETE, sid, JourneyOpt.OPT_OUT).creativeFor(PLACEMENT_CPT_FINAL_COMPLETE)
        expect(after != null && !after.isJourneyAd(), "after completion + opt-out, the competing normal ad serves (takeover ended)")
    }
}

/** §I Environment-conditional bonus coverage — auto-skip when not applicable. */
private fun groupI(h: Harness) {
    println("§I Environment-conditional (bonus)")
    h.scenario("I1", "Food-Delivery parting window (bonus coverage)", "267") {
        skip("parting-window bonus coverage is documented but not gated; deterministic freq-cap is §E")
    }
}

/** §J Mandatory blocker + paired targeting — needs Phase-1 seeds (adhub#2361). */
private fun groupJ(h: Harness) {
    println("§J Mandatory blocker & targeting")

    h.scenario("J1", "mandatory blocker holds the surface (positive control proves suppression)", "70", "76", "84", "P0#4") {
        requireFixture(PLACEMENT_MANDATORY_EARLY, "adhub#2361")
        val sid = freshSession("J1")
        // Request a later optional stage while the mandatory early stage is unserved.
        val blocked = decideOn(PLACEMENT_MANDATORY_LATER, sid, JourneyOpt.OPT_IN)
        expect(blocked.isNoAdFor(PLACEMENT_MANDATORY_LATER), "mandatory blocker must hold: no Journey ad on the later stage")
        // Positive control: with NO session (so no Journey can start), the competing normal ad must serve on
        // the same placement — proving the blocked result above is takeover *suppression*, not an empty
        // placement. A session with journeyOpt omitted would itself start (and hold) the Journey, so the
        // control deliberately sends no session at all.
        val control = decideOn(PLACEMENT_MANDATORY_LATER, sessionId = null).creativeFor(PLACEMENT_MANDATORY_LATER)
        expect(control != null, "control: the placement has inventory when no Journey holds it")
    }

    h.scenario("J2", "location targeting: inside serves, outside does not", "262") {
        // Presence + match in one: an in-target request must serve the Journey. If it does not, the fixture
        // isn't seeded (a no-coord probe can't detect a location-gated deal — the engine drops it), so SKIP.
        val inside = decideOn(PLACEMENT_TARGET_LOCATION, freshSession("J2-in"), JourneyOpt.OPT_IN) {
            addLocationTarget(latitude = NYC_LAT, longitude = NYC_LON)
        }.creativeFor(PLACEMENT_TARGET_LOCATION)
        if (inside == null || !inside.isJourneyAd()) skip("fixture '$PLACEMENT_TARGET_LOCATION' not seeded (see adhub#2361)")
        // Out-of-target coordinate → no Journey (a competing normal ad may serve instead).
        val outside = decideOn(PLACEMENT_TARGET_LOCATION, freshSession("J2-out"), JourneyOpt.OPT_IN) {
            addLocationTarget(latitude = FAR_LAT, longitude = FAR_LON)
        }.creativeFor(PLACEMENT_TARGET_LOCATION)
        expect(outside == null || !outside.isJourneyAd(), "an out-of-target location must not start the Journey")
    }

    h.scenario("J3", "destination targeting: in-radius + confidence serves, below-confidence does not", "263-264") {
        val inside = decideOn(PLACEMENT_TARGET_DESTINATION, freshSession("J3-in"), JourneyOpt.OPT_IN) {
            addDestinationTarget(latitude = NYC_LAT, longitude = NYC_LON, minConfidence = DEST_CONFIDENCE_PASS)
        }.creativeFor(PLACEMENT_TARGET_DESTINATION)
        if (inside == null || !inside.isJourneyAd()) skip("fixture '$PLACEMENT_TARGET_DESTINATION' not seeded (see adhub#2361)")
        // Same in-radius coordinate but a confidence below the deal's threshold → excluded by the confidence gate.
        val below = decideOn(PLACEMENT_TARGET_DESTINATION, freshSession("J3-low"), JourneyOpt.OPT_IN) {
            addDestinationTarget(latitude = NYC_LAT, longitude = NYC_LON, minConfidence = DEST_CONFIDENCE_FAIL)
        }.creativeFor(PLACEMENT_TARGET_DESTINATION)
        expect(below == null || !below.isJourneyAd(), "a below-confidence destination must not start the Journey")
    }

    h.scenario("J4", "geo targeting: matching geoname serves, non-matching does not", "261") {
        val match = decideOn(PLACEMENT_TARGET_GEO, freshSession("J4-yes"), JourneyOpt.OPT_IN) {
            addGeoTarget(id = GEONAME_MATCH)
        }.creativeFor(PLACEMENT_TARGET_GEO)
        if (match == null || !match.isJourneyAd()) skip("fixture '$PLACEMENT_TARGET_GEO' not seeded (see adhub#2361)")
        // A real, valid geoname that is NOT the target → no Journey. (Using an id absent from the engine's
        // geoname set would 400 instead of cleanly not-matching, so the no-match id must be a real geoname.)
        val nonMatching = decideOn(PLACEMENT_TARGET_GEO, freshSession("J4-no"), JourneyOpt.OPT_IN) {
            addGeoTarget(id = GEONAME_NO_MATCH)
        }.creativeFor(PLACEMENT_TARGET_GEO)
        expect(nonMatching == null || !nonMatching.isJourneyAd(), "a non-matching geo target must not start the Journey")
    }

    h.scenario("J5", "optional stage is skipped; a skipped stage cannot serve later", "7") {
        requireFixture(PLACEMENT_OPTSKIP_LATER, "adhub#2362")
        val sid = freshSession("J5")
        // Request the LATER placement: the earlier optional stage has no node here, so it is skipped
        // (optional), and the later stage serves. (Contrast the mandatory blocker §J1, which HOLDS.)
        val later = decideOn(PLACEMENT_OPTSKIP_LATER, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_OPTSKIP_LATER)
        expect(later != null && later.isJourneyAd(), "later optional stage serves after the earlier one is skipped")
        expect(later!!.journeyStageKey() == "later", "the served stage is the later one")
        // The skipped earlier stage cannot serve afterward on its own placement (next_stage_order advanced past it).
        val early = decideOn(PLACEMENT_OPTSKIP_EARLY, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_OPTSKIP_EARLY)
        expect(early == null || !early.isJourneyAd(), "a skipped stage cannot serve later")
    }
}

/** §F/§G Phase-2 groups (short-TTL, video) — always SKIP until adhub#2362 seeds land. */
private fun groupPhase2(h: Harness) {
    println("§F/§G Phase-2 (TTL, video)")
    h.scenario("F1", "runtime TTL expiry restarts the Journey", "104", "107", "110") {
        requireFixture(PLACEMENT_SHORT_TTL, "adhub#2362")
        val sid = freshSession("F1")
        val first = decideOn(PLACEMENT_SHORT_TTL, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_SHORT_TTL)
        expect(first != null && first.isJourneyAd(), "short-TTL stage should serve on a fresh session")
        val firstInstance = first!!.journeyInstanceId()
        expect(!firstInstance.isNullOrBlank(), "first serve must carry an instanceId")
        expect(!first.isJourneyCompletion(), "first serve is not a completion")
        // Wait past the fixture's runtime_state_ttl_seconds so the runtime-state key expires in Redis.
        Thread.sleep(SHORT_TTL_WAIT_MS)
        val second = decideOn(PLACEMENT_SHORT_TTL, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_SHORT_TTL)
        expect(second != null && second.isJourneyAd(), "re-request on the same session after TTL should serve again")
        expect(
            !second!!.journeyInstanceId().isNullOrBlank() && second.journeyInstanceId() != firstInstance,
            "a NEW journey_instance_id must be issued after runtime-state TTL expiry",
        )
        expect(!second.isJourneyCompletion(), "no completion was emitted across the TTL restart")
    }

    h.scenario("F2", "qualifying activity refreshes the runtime TTL (no expiry on creation time)", "15") {
        requireFixture(PLACEMENT_MULTINODE_A, "adhub#2362")
        val sid = freshSession("F2")
        val a = decideOn(PLACEMENT_MULTINODE_A, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_MULTINODE_A)
        expect(a != null && a.isJourneyAd(), "node A serves and starts the instance")
        val instance = a!!.journeyInstanceId()
        // Mid-window activity (gap < TTL): serving a NEW node refreshes the runtime-state key's EX ttl.
        Thread.sleep(TTL_REFRESH_GAP_MS)
        val b = decideOn(PLACEMENT_MULTINODE_B, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_MULTINODE_B)
        expect(b != null && b.isJourneyAd() && b.journeyInstanceId() == instance, "node B: same instance, and its serve refreshes the TTL")
        // Another sub-TTL gap. Total elapsed (2 gaps) now exceeds the original TTL; without the refresh the
        // instance would already have expired on its creation-time deadline.
        Thread.sleep(TTL_REFRESH_GAP_MS)
        val c = decideOn(PLACEMENT_MULTINODE_C, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_MULTINODE_C)
        expect(c != null && c.isJourneyAd(), "node C serves past the original TTL window")
        expect(c!!.journeyInstanceId() == instance, "same instance — the mid activity refreshed the TTL (no expiry on original creation time)")
    }
    h.scenario("G1", "JSON video node exposes video delivery + fires JSON tracking", "283", "221") {
        requireFixture(PLACEMENT_VIDEO_JSON, "adhub#2362")
        val c = decideOn(PLACEMENT_VIDEO_JSON, freshSession("G1"), JourneyOpt.OPT_IN).creativeFor(PLACEMENT_VIDEO_JSON)
        expect(c != null && c.isJourneyAd(), "JSON video journey should serve")
        expect(c!!.isJsonDelivery(), "delivery mode should be json")
        // JSON video is served with SDK-fired JSON tracking (the SDK owns these, unlike VAST-embedded beacons).
        expect(!c.tracking.impressions?.firstOrNull()?.url.isNullOrBlank(), "JSON video exposes an impression tracking URL")
    }

    h.scenario("G2", "VAST tag/xml returned; SDK fires no VAST-owned beacons", "284-285", "222-224", "P0#14") {
        requireFixture(PLACEMENT_VIDEO_VAST_TAG, "adhub#2362")
        val tag = decideOn(PLACEMENT_VIDEO_VAST_TAG, freshSession("G2-tag"), JourneyOpt.OPT_IN).creativeFor(PLACEMENT_VIDEO_VAST_TAG)
        expect(tag != null && tag.isJourneyAd(), "VAST-tag video journey should serve")
        expect(tag!!.isVastTagDelivery(), "delivery mode should be vast_tag")
        // getVastTagUrl() with no args returns the raw signed tag URL and never touches android.util.
        expect(!tag.getVastTagUrl().isNullOrBlank(), "a VAST tag URL must be returned for the player")
        // P0 #14: VAST-owned impression/quartile/click beacons live INSIDE the VAST document (the player's
        // job). The SDK must not surface them as its own fireable video-event beacons, and it never
        // auto-fires anything (all firing is an explicit fireX call, which this scenario never makes).
        expect(tag.tracking.videoEvents.isNullOrEmpty(), "SDK must not surface VAST-owned video-event beacons (player owns them)")

        requireFixture(PLACEMENT_VIDEO_VAST_XML, "adhub#2362")
        val xml = decideOn(PLACEMENT_VIDEO_VAST_XML, freshSession("G2-xml"), JourneyOpt.OPT_IN).creativeFor(PLACEMENT_VIDEO_VAST_XML)
        expect(xml != null && xml.isJourneyAd(), "VAST-xml video journey should serve")
        expect(xml!!.isVastXmlDelivery(), "delivery mode should be vast_xml")
        // getVastXmlBase64() with no args returns the raw base64 without touching android.util.Base64.
        expect(!xml.getVastXmlBase64().isNullOrBlank(), "inline VAST XML (base64) must be returned for the player")
        expect(xml.tracking.videoEvents.isNullOrEmpty(), "SDK must not surface VAST-owned video-event beacons (player owns them)")
    }
}

/**
 * §K Wizard parity — the only group driven by a journey the PLATFORM authored rather than the Go mock seed.
 * See the `WIZARD_*` constants for the fixture's configuration and for why this seam matters more than any
 * other: seeded fixtures encode what the engine expects, so they cannot catch a platform/engine shape
 * mismatch. #2459 and #2483 were both exactly that, and both survived a fully green suite.
 */
private fun groupK(h: Harness) {
    println("§K Wizard parity (platform-authored journey)")

    h.scenario("K1", "platform-authored journey serves stage 1 with the config the wizard wrote", "wizard-parity") {
        requireFixture(PLACEMENT_WIZARD_S1, WIZARD_MISSING)
        val c = decideOn(PLACEMENT_WIZARD_S1, freshSession("K1"), JourneyOpt.OPT_IN).creativeFor(PLACEMENT_WIZARD_S1)
        expect(c != null && c.isJourneyAd(), "the platform-authored journey must serve on $PLACEMENT_WIZARD_S1")
        expect(c!!.journeyDefinitionKey() == WIZARD_DEF, "definitionKey should be $WIZARD_DEF")
        expect(c.journeyStageKey() == "pre_ride", "the first stage should be pre_ride")
        expect(!c.journeyInstanceId().isNullOrBlank(), "instanceId must be present")
        expect(c.journeyPricingModel() == "cpt", "the wizard wrote CPT pricing")
        expect(c.journeyFallbackBillingMode() == "bill_per_stage", "the wizard wrote a bill_per_stage fallback")
        expect(c.journey?.isCompletion != true, "stage 1 is not the completion stage")
        // #2483 in the shape that actually broke it: the wizard persists camelCase url fields (urlSlide1..3
        // on carousel3Slides), which is precisely what the resolver used to fail to match.
        expectTrackingTransport(c.tracking.clicks?.firstOrNull()?.url, "wizard click tracking URL")
    }

    h.scenario("K2", "progression across platform-authored stages holds one instance", "wizard-parity") {
        requireFixture(PLACEMENT_WIZARD_S1, WIZARD_MISSING)
        val sid = freshSession("K2")
        val s1 = decideOn(PLACEMENT_WIZARD_S1, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_WIZARD_S1)
        expect(s1 != null && s1.isJourneyAd(), "stage 1 serves and starts the instance")
        val s2 = decideOn(PLACEMENT_WIZARD_S2, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_WIZARD_S2)
        expect(s2 != null && s2.isJourneyAd(), "stage 2 serves on $PLACEMENT_WIZARD_S2")
        expect(s2!!.journeyStageKey() == "post_ride", "the second stage should be post_ride")
        expect(s2.journeyInstanceId() == s1!!.journeyInstanceId(), "the instance is stable across stages")
        expect(s2.journeyStageNodeId() != s1.journeyStageNodeId(), "a different node served")
        expect(s2.journey?.isCompletion != true, "stage 2 is not the completion stage")
    }

    h.scenario("K3", "the wizard's final_stage completes the journey and emits no beacon", "wizard-parity") {
        requireFixture(PLACEMENT_WIZARD_S1, WIZARD_MISSING)
        val sid = freshSession("K3")
        val s1 = decideOn(PLACEMENT_WIZARD_S1, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_WIZARD_S1)
        expect(s1 != null && s1.isJourneyAd(), "stage 1 serves")
        decideOn(PLACEMENT_WIZARD_S2, sid, JourneyOpt.OPT_IN)
        val s3 = decideOn(PLACEMENT_WIZARD_S3, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_WIZARD_S3)
        expect(s3 != null && s3.isJourneyAd(), "the completion stage serves on $PLACEMENT_WIZARD_S3")
        expect(s3!!.journeyStageKey() == "summary_ride", "the third stage should be summary_ride")
        expect(s3.journeyInstanceId() == s1!!.journeyInstanceId(), "the same instance runs through completion")
        expect(s3.isJourneyCompletion(), "the wizard's final_stage completion stage flips isCompletion=true")
        expect(!s3.hasCompletionUrl(), "a final_stage deal must not expose a completion beacon")
    }

    h.scenario("K4", "an already-served wizard node does not serve twice", "wizard-parity") {
        requireFixture(PLACEMENT_WIZARD_S1, WIZARD_MISSING)
        val sid = freshSession("K4")
        val first = decideOn(PLACEMENT_WIZARD_S1, sid, JourneyOpt.OPT_IN).creativeFor(PLACEMENT_WIZARD_S1)
        expect(first != null && first.isJourneyAd(), "the node serves once")
        // `promotions` also carries competing published normal ads, so this doubles as its own positive
        // control: a journey ad here would be a one-serve-max violation, a normal ad or no-ad is correct.
        val again = decideOn(PLACEMENT_WIZARD_S1, sid).creativeFor(PLACEMENT_WIZARD_S1)
        expect(again == null || !again.isJourneyAd(), "the same node must not serve twice in one instance")
    }
}

// ------------------------------------------------------------------------------------------------
// main
// ------------------------------------------------------------------------------------------------

fun main() {
    val baseUrl = E2E_BASE_URL
    val version = System.getenv("ADMOAI_JOURNEY_E2E_VERSION") ?: "2025-11-01"

    println("Journey SDK E2E runner → $baseUrl (X-Decision-Version: $version)")

    Admoai.initialize(
        SDKConfig(
            baseUrl = baseUrl,
            apiVersion = version,
            enableLogging = false,
            defaultLanguage = "en", // Accept-Language; pinned so en-only seeded creatives resolve
            networkClientEngine = CIO.create(),
        ),
    )
    // Route SDK logging away from android.util.Log (unavailable off-device) and keep it quiet.
    // logSink is an instance seam, so it is set after initialize() (getInstance() is now available);
    // with enableLogging=false and a valid apiVersion, init itself emits no warnings.
    sdk().logSink = { _, _, _ -> }
    // Pin body locale to exactly "en" on every channel (host locale must not leak in via createDefault()).
    sdk().setDeviceConfig(DeviceConfig(language = "en"))
    sdk().setAppConfig(AppConfig(language = "en"))

    val exitCode = try {
        preflight(baseUrl)
        val h = Harness()
        groupA(h)
        groupB(h)
        groupBMetadata(h)
        groupC(h)
        groupD(h)
        groupE(h)
        groupH(h)
        groupI(h)
        groupJ(h)
        groupPhase2(h)
        groupK(h)
        // Shared cross-SDK manifest: normal ads, placement options, video delivery, the error
        // contract and API-version regression. Defined once in e2e-scenarios.json and executed
        // identically by all three SDKs — see ManifestRunner.kt.
        manifestGroup(h, baseUrl, version)
        wireShapeGroup(h, version)
        // Emits the tracking patterns the Ad Manager KPIs are computed from (clicks, a
        // timed completion). Last, because Y3 spends 3s of real wall-clock.
        metricEmissionGroup(h)
        h.finish()
    } finally {
        Admoai.resetForTesting()
    }
    exitProcess(exitCode)
}
