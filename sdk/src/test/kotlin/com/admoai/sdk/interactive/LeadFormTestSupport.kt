package com.admoai.sdk.interactive

import android.view.View
import com.admoai.sdk.model.response.InteractiveExperience
import io.mockk.mockk
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Shared fakes and contract fixtures of the Lead Generation controller tests. */

internal const val CONTRACT_BRIDGE_DIR = "src/test/resources/interactive-experience-contract/bridge"

/** A [BridgeTransport] that records what the controller loads and sends. */
internal class FakeTransport : BridgeTransport {
    val fakeView: View = mockk(relaxed = true)
    override val view: View? = fakeView
    val loads = mutableListOf<String>()
    val sent = mutableListOf<String>()
    var destroyed = 0
    var firstListener: ((String) -> Unit)? = null
    var listener: (String) -> Unit = {}
    var gone: () -> Unit = {}

    override fun load(url: String) {
        loads += url
    }

    override fun send(text: String) {
        sent += text
    }

    override fun setOnMessage(listener: (String) -> Unit) {
        if (firstListener == null) firstListener = listener
        this.listener = listener
    }

    override fun setOnRendererGone(listener: () -> Unit) {
        gone = listener
    }

    override fun destroy() {
        destroyed++
    }

    private fun parsed() = sent.map { Json.parseToJsonElement(it).jsonObject }

    fun sentTypes() = parsed().map { it.getValue("type").jsonPrimitive.content }

    fun sentOf(type: String) = parsed().filter { it.getValue("type").jsonPrimitive.content == type }

    /**
     * The page speaks [text], addressed to the channel of the last form this transport loaded (the
     * channel the controller put in the launch URL); with nothing loaded, as written.
     */
    fun say(text: String) {
        val loaded = loads.lastOrNull()?.substringAfter("channel=", "")?.takeIf { it.isNotEmpty() }
        if (loaded == null) {
            listener(text)
            return
        }
        val message = Json.parseToJsonElement(text).jsonObject
        listener(JsonObject(message + ("channel" to JsonPrimitive(loaded))).toString())
    }

    /** The page speaks: the contract's own runtime fixture, as the page would send it. */
    fun page(name: String) = say(File("$CONTRACT_BRIDGE_DIR/runtime-to-app/$name.json").readText())
}

/** A scheduler with manual time: posts run at once, delayed work runs when [advance] reaches it. */
internal class FakeScheduler : LeadFormScheduler {
    private class Timer(val at: Long, val block: () -> Unit) {
        var cancelled = false
    }

    private var now = 0L
    private val timers = mutableListOf<Timer>()

    override fun post(block: () -> Unit) = block()

    override fun postDelayed(delayMs: Long, block: () -> Unit): () -> Unit {
        val timer = Timer(now + delayMs, block)
        timers += timer
        return { timer.cancelled = true }
    }

    fun advance(ms: Long) {
        now += ms
        val due = timers.filter { !it.cancelled && it.at <= now }.sortedBy { it.at }
        timers.removeAll(due)
        due.forEach { if (!it.cancelled) it.block() }
    }
}

internal object LeadFormFixtures {
    private val context = Json.parseToJsonElement(File("$CONTRACT_BRIDGE_DIR/context.json").readText()).jsonObject
    val instanceId = context.getValue("instanceId").jsonPrimitive.content
    val channel = context.getValue("channel").jsonPrimitive.content
    const val LAUNCH_URL = "https://api.tenant.example.invalid/interactive-experiences/runtime/index.html"
    const val TOKEN = "tok_secret_value_123"

    @OptIn(ExperimentalInteractiveExperienceApi::class)
    fun descriptor(expires: Long = 1_000_000L) = InteractiveExperience.LeadGeneration(
        formId = "form_01KG000000000000000000F001",
        formInstanceId = instanceId,
        preparedVersionId = "form_version_01KG000000000000000000V002",
        expiresAtEpochMillis = expires,
        prefillAttributeKeys = listOf(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL),
        launchUrl = LAUNCH_URL,
        preparationToken = TOKEN
    )

    fun runtimeFixture(name: String): JsonObject =
        Json.parseToJsonElement(File("$CONTRACT_BRIDGE_DIR/runtime-to-app/$name.json").readText()).jsonObject

    /** The contract's `activated` fixture with its requested keys and events replaced. */
    fun activated(keys: List<String>? = null, events: List<String>? = null): String {
        val base = runtimeFixture("activated")
        val payload = base.getValue("payload").jsonObject
        val allEvents = payload.getValue("events") as JsonArray
        val newPayload = buildJsonObject {
            payload.forEach { (k, v) ->
                when (k) {
                    "requestedAttributeKeys" ->
                        if (keys ==
                            null
                        ) {
                            put(k, v)
                        } else {
                            put(k, buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } })
                        }

                    "events" ->
                        if (events == null) {
                            put(k, v)
                        } else {
                            put(
                                k,
                                JsonArray(
                                    allEvents.filter {
                                        it.jsonObject.getValue("name").jsonPrimitive.content in
                                            events
                                    }
                                )
                            )
                        }

                    else -> put(k, v)
                }
            }
        }
        return JsonObject(base + ("payload" to newPayload)).toString()
    }

    /** The URL the `activated` fixture lists for [event]. */
    fun eventUrl(event: String): String =
        (runtimeFixture("activated").getValue("payload").jsonObject.getValue("events") as JsonArray)
            .map { it.jsonObject }
            .first { it.getValue("name").jsonPrimitive.content == event }
            .getValue("url").jsonPrimitive.content

    fun eventMessage(name: String): String {
        val base = runtimeFixture("event")
        return JsonObject(base + ("payload" to buildJsonObject { put("name", name) })).toString()
    }
}
