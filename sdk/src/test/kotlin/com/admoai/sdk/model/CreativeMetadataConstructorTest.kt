package com.admoai.sdk.model

import com.admoai.sdk.model.response.CreativeMetadata
import com.admoai.sdk.model.response.MetadataPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Feature: A library compiled against an older SDK keeps linking
 *   As a publisher's tech team
 *   I want the CreativeMetadata constructor I compiled against to still exist
 *   So that upgrading the SDK does not break a precompiled dependency with NoSuchMethodError
 *
 * Kotlin cannot call a HIDDEN constructor, so this goes through reflection, which is exactly how
 * Java or an already-compiled caller reaches it: by the old JVM signature.
 */
class CreativeMetadataConstructorTest {

    @Test
    fun `the constructor without assetContext is still on the JVM and leaves assetContext null`() {
        val oldSignature = arrayOf<Class<*>>(
            String::class.java, String::class.java, String::class.java, String::class.java,
            String::class.java, MetadataPriority::class.java, String::class.java, String::class.java,
            String::class.java, Integer::class.java, String::class.java, java.lang.Boolean::class.java,
            String::class.java, Integer::class.java, String::class.java, String::class.java,
            Integer::class.java, String::class.java
        )
        val constructor = CreativeMetadata::class.java.getConstructor(*oldSignature)
        val metadata = constructor.newInstance(
            "ad", "cr", null, "pl", "tp", MetadataPriority.STANDARD, null, null,
            null, null, null, null, null, null, null, "carousel", 1, "admoai_standard"
        ) as CreativeMetadata

        assertEquals("ad", metadata.adId)
        assertEquals("carousel", metadata.effectiveStandardFormatKey)
        assertEquals(1, metadata.effectiveStandardFormatVersion)
        assertEquals("admoai_standard", metadata.templateSource)
        assertNull(metadata.assetContext)
    }
}
