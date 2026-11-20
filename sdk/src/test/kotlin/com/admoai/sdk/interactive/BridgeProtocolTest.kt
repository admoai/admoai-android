package com.admoai.sdk.interactive

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The scenarios of `src/main/kotlin/com/admoai/sdk/interactive/BridgeProtocol.feature`, one test
 * per scenario, named after it, driven by the vendored contract fixtures. `every scenario has a
 * test` holds the binding.
 */
class BridgeProtocolTest {

    private val root = "src/test/resources/interactive-experience-contract/bridge"

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun read(relative: String): String = File("$root/$relative").readText()

    private fun files(dir: String): List<File> =
        File("$root/$dir").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }

    private val context = parse(read("context.json"))
    private val instanceId = context.getValue("instanceId").jsonPrimitive.content
    private val channel = context.getValue("channel").jsonPrimitive.content

    private fun session() = BridgeSession(instanceId, channel)

    private fun encodeFixture(session: BridgeSession, file: File): String {
        val envelope = parse(file.readText())
        return session.encode(
            envelope.getValue("type").jsonPrimitive.content,
            envelope.getValue("payload").jsonObject
        )
    }

    /** Every app message of the contract, recorded as sent. */
    private fun sessionAfterAppMessages(): BridgeSession = session().also { s ->
        files("app-to-runtime").forEach { encodeFixture(s, it) }
    }

    /** The valid runtime fixtures in an order that respects each message's `after`. */
    private val runtimeOrder = listOf(
        "bridge-ready", "prepared", "activated", "prefill-applied", "prepare-failed", "fatal-error",
        "request-close", "request-external-url", "event", "submitting", "submit-failed", "submitted"
    )

    private fun sessionAfterEverything(): BridgeSession = sessionAfterAppMessages().also { s ->
        runtimeOrder.forEach { s.receive(read("runtime-to-app/$it.json")) }
    }

    private fun accepted(r: Received): Received.Accepted {
        if (r !is Received.Accepted) fail("expected accepted, got ${(r as Received.Refused).reason}")
        return r as Received.Accepted
    }

    private fun refused(r: Received): String {
        if (r !is Received.Refused) fail("expected refused")
        return (r as Received.Refused).reason
    }

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/interactive/BridgeProtocol.feature")
        val titles = feature.readLines()
            .map { it.trim() }
            .filter { it.startsWith("Scenario:") || it.startsWith("Scenario Outline:") }
            .map { it.substringAfter(":").trim() }
            .toSet()
        val tests = javaClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .map { it.name }
            .toSet()

        assertTrue("the feature file has scenarios", titles.isNotEmpty())
        assertEquals(emptySet<String>(), titles - tests)
    }

    @Test
    fun `every valid runtime message of the contract is accepted in its order`() {
        val contract =
            parse(File("src/main/resources/com/admoai/sdk/interactive/contract/bridge-messages.json").readText())
        val runtimeTypes = contract.getValue("messages").jsonArray
            .map { it.jsonObject }
            .filter { it.getValue("direction").jsonPrimitive.content == "runtime_to_app" }
            .map { it.getValue("type").jsonPrimitive.content }
            .toSet()
        val fixtureNames = files("runtime-to-app").map { it.nameWithoutExtension }.toSet()
        assertEquals("the order lists every runtime fixture", fixtureNames, runtimeOrder.toSet())
        assertEquals(
            "every runtime message has a fixture",
            runtimeTypes,
            fixtureNames.map { parse(read("runtime-to-app/$it.json")).getValue("type").jsonPrimitive.content }.toSet()
        )

        val s = sessionAfterAppMessages()
        runtimeOrder.forEach { name ->
            val text = read("runtime-to-app/$name.json")
            val got = accepted(s.receive(text))
            assertEquals(parse(text).getValue("type").jsonPrimitive.content, got.type)
            assertEquals(parse(text).getValue("payload"), got.payload)
        }
    }

    @Test
    fun `every invalid runtime message of the contract is refused`() {
        val invalid = files("invalid/runtime-to-app")
        assertTrue(invalid.isNotEmpty())
        invalid.forEach { file ->
            val reason = refused(sessionAfterEverything().receive(file.readText()))
            assertNotEquals("${file.name} must be refused on its own fault", "out_of_order", reason)
        }
    }

    @Test
    fun `a message for another instance or channel is refused`() {
        val prepared = parse(read("runtime-to-app/prepared.json"))
        val s = session().also {
            it.encode("initialize", parse(read("app-to-runtime/initialize.json")).getValue("payload").jsonObject)
        }
        assertEquals("wrong_instance", refused(s.receive(read("invalid/app-to-runtime/wrong-instance.json"))))
        val otherInstance = JsonObject(
            prepared +
                ("instanceId" to kotlinx.serialization.json.JsonPrimitive("form_instance_01KG000000000000000000N999"))
        )
        assertEquals("wrong_instance", refused(s.receive(otherInstance.toString())))
        assertEquals("wrong_channel", refused(s.receive(read("invalid/runtime-to-app/wrong-channel.json"))))
        // The right identity is still accepted afterwards.
        accepted(s.receive(prepared.toString()))
    }

    @Test
    fun `a message that arrives before the message it depends on is refused`() {
        val s = session()
        assertEquals("out_of_order", refused(s.receive(read("runtime-to-app/prepared.json"))))
        s.encode("initialize", parse(read("app-to-runtime/initialize.json")).getValue("payload").jsonObject)
        accepted(s.receive(read("runtime-to-app/prepared.json")))
    }

    @Test
    fun `a message over the size limit is refused before it is parsed`() {
        val s = sessionAfterEverything()
        assertEquals("too_large", refused(s.receive(read("invalid/runtime-to-app/oversized-message.json"))))
        // Not JSON at all, yet reported as too large: it was never parsed.
        assertEquals("too_large", refused(s.receive("x".repeat(BridgeProtocol.maxMessageBytes + 1))))
        // Multi-byte text counts bytes, not characters.
        assertEquals("too_large", refused(s.receive("é".repeat(BridgeProtocol.maxMessageBytes / 2 + 1))))
    }

    @Test
    fun `a page that speaks an unknown bridge version is refused`() {
        val s = session()
        assertEquals(
            "bad_payload:bridgeVersion",
            refused(s.receive(read("invalid/runtime-to-app/unknown-bridge-version.json")))
        )
        // Nothing was recorded: a bridge version this SDK knows is accepted next.
        accepted(s.receive(read("runtime-to-app/bridge-ready.json")))
    }

    @Test
    fun `messages the SDK sends match the contract byte for byte in meaning`() {
        files("app-to-runtime").forEach { file ->
            val envelope = parse(file.readText())
            val text = session().encode(
                envelope.getValue("type").jsonPrimitive.content,
                envelope.getValue("payload").jsonObject
            )
            assertEquals(file.name, envelope, parse(text))
            assertEquals(
                "field order",
                listOf("type", "instanceId", "channel", "payload"),
                parse(text).keys.toList()
            )
        }
        // And the contract's invalid app messages are never produced.
        files("invalid/app-to-runtime").filter { it.name != "wrong-instance.json" }.forEach { file ->
            val envelope = parse(file.readText())
            try {
                session().encode(
                    envelope.getValue("type").jsonPrimitive.content,
                    envelope.getValue("payload").jsonObject
                )
                fail("${file.name} must not be encodable")
            } catch (e: IllegalArgumentException) {
                assertFalse(e.message!!.contains("AAAA"))
            }
        }
    }

    @Test
    fun `text with quotes, newlines and Unicode crosses the bridge as data`() {
        val value = "He said \"hi\"\nline two 😀 </script><script>alert(1)</script> \\  "
        val text = session().encode(
            "prefill",
            JsonObject(
                mapOf("values" to JsonObject(mapOf("fullName" to kotlinx.serialization.json.JsonPrimitive(value))))
            )
        )
        val decoded = parse(text)
        assertEquals(
            value,
            decoded.getValue(
                "payload"
            ).jsonObject.getValue("values").jsonObject.getValue("fullName").jsonPrimitive.content
        )
        assertEquals(setOf("type", "instanceId", "channel", "payload"), decoded.keys)
        assertFalse("one line: the newline is escaped", text.contains('\n'))
        assertTrue(text.contains("\\\"hi\\\""))
    }

    @Test
    fun `a refused message is never echoed`() {
        val secret = "alex.secret@example.invalid"
        val s = sessionAfterEverything()
        val extraField =
            """{"type":"submitted","instanceId":"$instanceId","channel":"$channel","payload":""" +
                """{"leadId":"lead_01KG000000000000000000W001","submittedAt":"2026-11-12T15:04:05Z","$secret":"x"}}"""
        val extraValue =
            """{"type":"submitted","instanceId":"$instanceId","channel":"$channel","payload":""" +
                """{"leadId":"$secret","submittedAt":"2026-11-12T15:04:05Z"}}"""
        val malformed = """{"type":"submitted","payload":"$secret""""
        listOf(extraField, extraValue, malformed).forEach { text ->
            val reason = refused(s.receive(text))
            assertFalse(reason.contains("alex"))
            assertFalse(reason.contains("secret"))
            assertTrue("a fixed reason: $reason", Regex("^[a-z_]+(:[A-Za-z_]+)?$").matches(reason))
        }
        assertEquals("bad_payload:unknown_field", refused(s.receive(extraField)))
        assertEquals("bad_payload:leadId", refused(s.receive(extraValue)))
        assertEquals("not_json", refused(s.receive(malformed)))
    }

    @Test
    fun `a minted channel matches the contract and differs each time`() {
        val a = newChannel()
        val b = newChannel()
        assertEquals(32, a.length)
        assertTrue(BridgeProtocol.validChannel(a))
        assertNotEquals(a, b)
    }

    @Test
    fun `the prefill gender must be one of the catalog's options`() {
        fun prefill(gender: String) = JsonObject(
            mapOf(
                "values" to JsonObject(mapOf("gender" to kotlinx.serialization.json.JsonPrimitive(gender)))
            )
        )
        session().encode("prefill", prefill("female"))
        try {
            session().encode("prefill", prefill("unicorn"))
            fail("an unknown gender must not be encoded")
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message!!.contains("unicorn"))
        }
    }
}
