package com.admoai.sdk.model

import com.admoai.sdk.model.response.CreativeMetadata
import com.admoai.sdk.model.response.MetadataPriority
import kotlin.jvm.internal.DefaultConstructorMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Feature: An app or library compiled against SDK 1.7.2 keeps linking
 *   As a publisher's tech team
 *   I want the CreativeMetadata constructors 1.7.2 shipped to still exist
 *   So that upgrading the SDK does not break a precompiled dependency with NoSuchMethodError
 *
 * Kotlin cannot call a HIDDEN constructor, so this goes through reflection, which is exactly how an
 * already-compiled caller reaches it: by the 1.7.2 JVM signature.
 */
class CreativeMetadataConstructorTest {

    private val v172Signature = arrayOf<Class<*>>(
        String::class.java, String::class.java, String::class.java, String::class.java,
        String::class.java, MetadataPriority::class.java, String::class.java, String::class.java,
        String::class.java, Integer::class.java, String::class.java, java.lang.Boolean::class.java,
        String::class.java, Integer::class.java, String::class.java
    )

    @Test
    fun `the 1_7_2 constructor is still on the JVM and leaves the new fields absent`() {
        val constructor = CreativeMetadata::class.java.getDeclaredConstructor(*v172Signature)
        val metadata = constructor.newInstance(
            "ad", "cr", null, "pl", "tp", MetadataPriority.STANDARD, null, "tall",
            null, null, null, null, null, null, null
        ) as CreativeMetadata

        assertEquals("ad", metadata.adId)
        assertEquals("tall", metadata.style)
        assertNull(metadata.effectiveStandardFormatKey)
        assertNull(metadata.effectiveStandardFormatVersion)
        assertNull(metadata.templateSource)
        assertNull(metadata.assetContext)
    }

    @Test
    fun `the 1_7_2 default-arguments constructor is still on the JVM and applies the 1_7_2 defaults`() {
        val constructor = CreativeMetadata::class.java.getDeclaredConstructor(
            *v172Signature,
            Int::class.javaPrimitiveType,
            DefaultConstructorMarker::class.java
        )
        // What a Kotlin caller compiled against 1.7.2 emits for CreativeMetadata(adId = "ad"):
        // every parameter but the first marked as defaulted.
        val metadata = constructor.newInstance(
            "ad", null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            0x7FFF and 1.inv(), null
        ) as CreativeMetadata

        assertEquals("ad", metadata.adId)
        assertEquals("", metadata.creativeId)
        assertEquals(MetadataPriority.UNKNOWN, metadata.priority)
        assertNull(metadata.effectiveStandardFormatKey)
        assertNull(metadata.assetContext)
    }
}
