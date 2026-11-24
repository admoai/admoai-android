package com.admoai.sdk.interactive

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scenarios of `src/main/kotlin/com/admoai/sdk/interactive/LeadFormPrefill.feature`, one test per
 * scenario, named after it, plus the contract-number and pure-prefill checks they rest on.
 */
@OptIn(ExperimentalInteractiveExperienceApi::class)
class LeadFormPrefillTest {

    private val transport = FakeTransport()
    private val scheduler = FakeScheduler()
    private val logs = mutableListOf<String>()
    private val asked = mutableListOf<Set<StandardAttributeKey>>()
    private var pending: LeadFormValues? = null
    private var now = 0L

    private val host = object : LeadFormHost {
        override fun onRequestClose(controller: LeadFormController) = Unit

        override fun onOpenExternalLink(controller: LeadFormController, purpose: ExternalLinkPurpose, url: String) =
            Unit
    }

    private fun provider(
        held: Map<StandardAttributeKey, String> = emptyMap(),
        answer: (Set<StandardAttributeKey>, LeadFormValues) -> Unit = { keys, done ->
            done.complete(held.filterKeys { it in keys })
        }
    ) = object : LeadFormValueProvider {
        override fun availableKeys(): Set<StandardAttributeKey> = held.keys

        override fun lookUpValues(keys: Set<StandardAttributeKey>, completion: LeadFormValues) {
            asked += keys
            answer(keys, completion)
        }
    }

    private fun controller(valueProvider: LeadFormValueProvider) = LeadFormController(
        descriptor = LeadFormFixtures.descriptor(),
        presentation = FormPresentation.FULL_SCREEN,
        host = host,
        transportFactory = { transport },
        clock = { now },
        channel = LeadFormFixtures.channel,
        log = { logs += it },
        valueProvider = valueProvider,
        scheduler = scheduler
    )

    private fun prepared(c: LeadFormController) {
        c.prepare()
        transport.page("bridge-ready")
        transport.page("prepared")
    }

    /** Opens [c] and plays the page's `activated` message requesting [keys]. */
    private fun activate(c: LeadFormController, keys: List<String>) {
        prepared(c)
        c.open()
        transport.listener(LeadFormFixtures.activated(keys = keys))
    }

    private fun prefillValues(): JsonObject =
        transport.sentOf("prefill").single().getValue("payload").jsonObject.getValue("values").jsonObject

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/interactive/LeadFormPrefill.feature")
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
    fun `the app is asked only for the keys the activated form requests`() {
        val c = controller(
            provider(
                mapOf(
                    StandardAttributeKey.FIRST_NAME to "Alex",
                    StandardAttributeKey.EMAIL to "alex@example.invalid",
                    StandardAttributeKey.PHONE to "+15550100"
                )
            )
        )
        activate(c, listOf("fullName", "email"))
        assertEquals(listOf(setOf(StandardAttributeKey.FULL_NAME, StandardAttributeKey.EMAIL)), asked)
        assertEquals(JsonObject(mapOf("email" to JsonPrimitive("alex@example.invalid"))), prefillValues())
    }

    @Test
    fun `the app is never asked before activation`() {
        val c = controller(provider(mapOf(StandardAttributeKey.EMAIL to "alex@example.invalid")))
        prepared(c)
        assertTrue(asked.isEmpty())
        c.open()
        assertTrue(asked.isEmpty())
        assertTrue(transport.sentOf("prefill").isEmpty())
    }

    @Test
    fun `a form that requests no key is prefilled with nothing, without asking the app`() {
        val c = controller(provider(mapOf(StandardAttributeKey.EMAIL to "alex@example.invalid")))
        activate(c, emptyList())
        assertTrue(asked.isEmpty())
        assertEquals(JsonObject(emptyMap()), prefillValues())
    }

    @Test
    fun `a slow app does not hold the form back`() {
        val c = controller(provider(answer = { _, done -> pending = done }))
        activate(c, listOf("fullName", "email"))
        scheduler.advance(BridgeContract.prefillLookupTimeoutMs - 1)
        assertTrue(transport.sentOf("prefill").isEmpty())
        scheduler.advance(1)
        assertEquals(JsonObject(emptyMap()), prefillValues())
        pending!!.complete(mapOf(StandardAttributeKey.EMAIL to "late@example.invalid"))
        assertEquals(1, transport.sentOf("prefill").size)
    }

    @Test
    fun `an answer that arrives past the lookup time is ignored even before the timeout ran`() {
        val c = controller(provider(answer = { _, done -> pending = done }))
        activate(c, listOf("fullName", "email"))
        now += BridgeContract.prefillLookupTimeoutMs + 1
        pending!!.complete(mapOf(StandardAttributeKey.EMAIL to "late@example.invalid"))
        assertEquals(JsonObject(emptyMap()), prefillValues())
        scheduler.advance(BridgeContract.prefillLookupTimeoutMs)
        assertEquals(1, transport.sentOf("prefill").size)
    }

    @Test
    fun `an app that fails leaves the fields empty`() {
        val c = controller(provider(answer = { _, _ -> throw IllegalStateException("secret@example.invalid") }))
        activate(c, listOf("fullName", "email"))
        assertEquals(JsonObject(emptyMap()), prefillValues())
        assertEquals(listOf("Lead Generation value lookup failed: IllegalStateException"), logs)
    }

    @Test
    fun `an answer the app changes after giving it reaches the form as it was given`() {
        val c = controller(provider(answer = { _, done -> pending = done }))
        activate(c, listOf("fullName", "email"))
        val values = mutableMapOf(StandardAttributeKey.EMAIL to "alex@example.invalid")
        // The answer is read on the main thread later: hold that post, and let the app change its map first.
        scheduler.holdNextPost()
        pending!!.complete(values)
        values.clear()
        scheduler.runHeld()
        assertEquals(JsonObject(mapOf("email" to JsonPrimitive("alex@example.invalid"))), prefillValues())
    }

    @Test
    fun `an answer that cannot be read leaves the fields empty`() {
        val unreadable = object : AbstractMap<StandardAttributeKey, String>() {
            override val entries: Set<Map.Entry<StandardAttributeKey, String>>
                get() = throw IllegalStateException("secret@example.invalid")
        }
        val c = controller(provider(answer = { _, done -> pending = done }))
        activate(c, listOf("fullName", "email"))
        scheduler.holdNextPost()
        pending!!.complete(unreadable)
        scheduler.runHeld()
        assertEquals(JsonObject(emptyMap()), prefillValues())
        assertEquals(LeadFormState.Activating, c.state.value)
    }

    @Test
    fun `values the contract does not allow are left out`() {
        val tooLong = "1".repeat(BridgeContract.prefillValueMaxLength + 1)
        val c = controller(
            provider(
                mapOf(
                    StandardAttributeKey.EMAIL to "   ",
                    StandardAttributeKey.PHONE to tooLong,
                    StandardAttributeKey.GENDER to "not-an-option",
                    StandardAttributeKey.FIRST_NAME to "Alex"
                ),
                answer = { _, done ->
                    done.complete(
                        mapOf(
                            StandardAttributeKey.EMAIL to "   ",
                            StandardAttributeKey.PHONE to tooLong,
                            StandardAttributeKey.GENDER to "not-an-option",
                            StandardAttributeKey.FIRST_NAME to "Alex"
                        )
                    )
                }
            )
        )
        activate(c, listOf("email", "phone", "gender"))
        assertEquals(JsonObject(emptyMap()), prefillValues())
    }

    @Test
    fun `a value with quotes, newlines and Unicode reaches the form as data`() {
        val name = "Alex \"Al\" O'Neil\nMorgan 😀"
        val c = controller(provider(mapOf(StandardAttributeKey.FULL_NAME to name)))
        activate(c, listOf("fullName"))
        assertEquals(name, prefillValues().getValue("fullName").jsonPrimitive.content)
    }

    @Test
    fun `the controller and its state never show a prefilled value`() {
        val email = "alex.private@example.invalid"
        val c = controller(provider(mapOf(StandardAttributeKey.EMAIL to email)))
        activate(c, listOf("email"))
        transport.page("prefill-applied")
        assertEquals(email, prefillValues().getValue("email").jsonPrimitive.content)
        val shown = listOf(c.toString(), c.state.value.toString()) + logs
        shown.forEach { assertTrue(it, !it.contains(email) && !it.contains("alex.private")) }
    }

    @Test
    fun `the contract numbers are the ones of the vendored JSON`() {
        val dir = "src/main/resources/com/admoai/sdk/interactive/contract"
        val bridge = Json.parseToJsonElement(File("$dir/bridge-messages.json").readText()).jsonObject
        val fields = Json.parseToJsonElement(File("$dir/standard-fields.json").readText()).jsonObject
        assertEquals(
            bridge.getValue("maxHiddenPreparedForms").jsonPrimitive.content.toInt(),
            BridgeContract.maxHiddenPreparedForms
        )
        assertEquals(
            bridge.getValue("prefillLookupTimeoutMs").jsonPrimitive.content.toLong(),
            BridgeContract.prefillLookupTimeoutMs
        )
        val prefill = bridge.getValue("messages").jsonArray.map { it.jsonObject }
            .first { it.getValue("type").jsonPrimitive.content == "prefill" }
        val max = prefill.getValue("payload").jsonObject.getValue("values").jsonObject.getValue("maxLength")
        assertEquals(max.jsonPrimitive.content.toInt(), BridgeContract.prefillValueMaxLength)
        val genders = fields.getValue("genderOptions").jsonArray.map {
            it.jsonObject.getValue("value").jsonPrimitive.content
        }.toSet()
        assertEquals(genders, BridgeContract.genderOptions)
    }

    @Test
    fun `the prefill keeps requested, trimmed, allowed values in catalog order`() {
        val gender = BridgeContract.genderOptions.first()
        val shown = LeadFormPrefill.values(
            mapOf(
                StandardAttributeKey.GENDER to " $gender ",
                StandardAttributeKey.EMAIL to " a@b.invalid ",
                StandardAttributeKey.PHONE to "+1"
            ),
            setOf(StandardAttributeKey.GENDER, StandardAttributeKey.EMAIL),
            BridgeContract
        )
        assertEquals(listOf("email", "gender"), shown.keys.toList())
        assertEquals("a@b.invalid", shown.getValue("email").jsonPrimitive.content)
        assertEquals(gender, shown.getValue("gender").jsonPrimitive.content)
        assertEquals(
            JsonObject(emptyMap()),
            LeadFormPrefill.values(null, setOf(StandardAttributeKey.EMAIL), BridgeContract)
        )
    }
}
