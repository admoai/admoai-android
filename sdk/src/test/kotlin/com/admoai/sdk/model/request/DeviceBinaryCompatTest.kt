package com.admoai.sdk.model.request

import com.admoai.sdk.config.DeviceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Apps and libraries compiled against 1.8.x call the 7-field constructor, `copy`, and their
 * default-argument entry points by JVM signature. Appending `carrier` and `category` changed the
 * primary signatures, so hidden overloads keep the old ones. This test proves they still exist
 * and behave, by the exact descriptors a precompiled caller links against. Reflection is the only
 * way to reach them: they are HIDDEN in Kotlin and synthetic for javac.
 */
class DeviceBinaryCompatTest {

    private val s = String::class.java
    private val seven: Array<Class<*>> = Array(7) { s }
    private val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

    // Scenario: a 1.8.x caller builds a DeviceConfig with all seven fields.
    @Test
    fun `DeviceConfig 7-argument constructor still links`() {
        val ctor = DeviceConfig::class.java.getConstructor(*seven)
        val cfg = ctor.newInstance("Pixel 6", "Android", "13", "Europe/Madrid", "id", "Google", "es-ES")
        assertEquals(
            DeviceConfig("Pixel 6", "Android", "13", "Europe/Madrid", "id", "Google", "es-ES", null, null),
            cfg
        )
    }

    // Scenario: a 1.8.x Kotlin caller used named/default arguments: DeviceConfig(model = "x").
    @Test
    fun `DeviceConfig 1_8 default-argument constructor still links`() {
        val ctor = DeviceConfig::class.java.getConstructor(*seven, Int::class.javaPrimitiveType, marker)
        // Mask 0b1111110: every parameter except the first takes its default.
        val cfg = ctor.newInstance("Pixel 6", null, null, null, null, null, null, 0b1111110, null)
        assertEquals(DeviceConfig(model = "Pixel 6"), cfg)
    }

    // Scenario: a 1.8.x caller copies a DeviceConfig; carrier and category survive the copy.
    @Test
    fun `DeviceConfig 7-argument copy and copy default still link and keep the new fields`() {
        val original = DeviceConfig(model = "a", carrier = "Movistar", category = DeviceCategory.TABLET)

        val copy = DeviceConfig::class.java.getMethod("copy", *seven)
        val copied = copy.invoke(original, "b", null, null, null, null, null, null) as DeviceConfig
        assertEquals("b", copied.model)
        assertEquals("Movistar", copied.carrier)
        assertEquals(DeviceCategory.TABLET, copied.category)

        val copyDefault = DeviceConfig::class.java.getMethod(
            "copy\$default",
            DeviceConfig::class.java,
            *seven,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        // Mask 0b1111110: only model is passed.
        val viaDefault = copyDefault.invoke(null, original, "c", null, null, null, null, null, null, 0b1111110, null)
            as DeviceConfig
        assertEquals(original.copy(model = "c"), viaDefault)
    }

    // Scenario: a 1.8.x caller builds a Device request model directly.
    @Test
    fun `Device 7-argument constructor, default constructor and copy still link`() {
        val ctor = Device::class.java.getConstructor(*seven)
        val device = ctor.newInstance("Android", "13", "Pixel 6", "Google", "id", "UTC", "en") as Device
        assertNull(device.carrier)
        assertNull(device.category)

        val defaultCtor = Device::class.java.getConstructor(*seven, Int::class.javaPrimitiveType, marker)
        assertEquals(
            Device(os = "Android"),
            defaultCtor.newInstance("Android", null, null, null, null, null, null, 0b1111110, null)
        )

        val withNew = Device(os = "Android", carrier = "Movistar", category = "tablet")
        val copied = Device::class.java.getMethod("copy", *seven)
            .invoke(withNew, "iOS", null, null, null, null, null, null) as Device
        assertEquals("iOS", copied.os)
        assertEquals("Movistar", copied.carrier)
        assertEquals("tablet", copied.category)

        val copyDefault = Device::class.java.getMethod(
            "copy\$default",
            Device::class.java,
            *seven,
            Int::class.javaPrimitiveType,
            Any::class.java
        )
        assertEquals(
            withNew.copy(os = "iOS"),
            copyDefault.invoke(null, withNew, "iOS", null, null, null, null, null, null, 0b1111110, null)
        )
    }
}
