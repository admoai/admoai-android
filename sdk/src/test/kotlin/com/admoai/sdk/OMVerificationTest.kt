package com.admoai.sdk

import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Advertiser
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.CreativeMetadata
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.MetadataPriority
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.VastData
import com.admoai.sdk.model.response.VerificationScriptResource
import com.admoai.sdk.utils.getVerificationResources
import com.admoai.sdk.utils.hasOMVerification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Open Measurement (OM) Verification serialization and deserialization.
 */
class OMVerificationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - VerificationScriptResource Deserialization

    @Test
    fun `VerificationScriptResource deserialization - parses all fields correctly`() {
        val jsonString = """
            {
                "vendorKey": "iabtechlab.com-omid",
                "scriptUrl": "https://verification.example.com/omid.js",
                "verificationParameters": "param1=value1&param2=value2"
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals("iabtechlab.com-omid", resource.vendorKey)
        assertEquals("https://verification.example.com/omid.js", resource.scriptUrl)
        assertEquals("param1=value1&param2=value2", resource.verificationParameters)
    }

    @Test
    fun `VerificationScriptResource deserialization - handles empty parameters`() {
        val jsonString = """
            {
                "vendorKey": "doubleverify.com-omid",
                "scriptUrl": "https://cdn.doubleverify.com/dvtp_src.js",
                "verificationParameters": ""
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals("doubleverify.com-omid", resource.vendorKey)
        assertEquals("https://cdn.doubleverify.com/dvtp_src.js", resource.scriptUrl)
        assertEquals("", resource.verificationParameters)
    }

    @Test
    fun `VerificationScriptResource deserialization - handles special characters in URL and parameters`() {
        val jsonString = """
            {
                "vendorKey": "ias.com-omid",
                "scriptUrl": "https://cdn.ias.com/verification.js?v=1.0",
                "verificationParameters": "anId=123&advId=456&campId=789&creativeId=abc-def_123"
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals("ias.com-omid", resource.vendorKey)
        assertEquals("https://cdn.ias.com/verification.js?v=1.0", resource.scriptUrl)
        assertEquals("anId=123&advId=456&campId=789&creativeId=abc-def_123", resource.verificationParameters)
    }

    @Test
    fun `VerificationScriptResource deserialization - handles null verificationParameters`() {
        val jsonString = """
            {
                "vendorKey": "iabtechlab.com-omid",
                "scriptUrl": "https://verification.example.com/omid.js",
                "verificationParameters": null
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals("iabtechlab.com-omid", resource.vendorKey)
        assertEquals("https://verification.example.com/omid.js", resource.scriptUrl)
        assertNull(resource.verificationParameters)
    }

    @Test
    fun `VerificationScriptResource deserialization - handles missing verificationParameters`() {
        val jsonString = """
            {
                "vendorKey": "iabtechlab.com-omid",
                "scriptUrl": "https://verification.example.com/omid.js"
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals("iabtechlab.com-omid", resource.vendorKey)
        assertEquals("https://verification.example.com/omid.js", resource.scriptUrl)
        assertNull(resource.verificationParameters)
    }

    // MARK: - Creative with VerificationScriptResources

    @Test
    fun `Creative with single verification resource - parses correctly`() {
        val jsonString = """
            {
                "contents": [
                    {"key": "headline", "value": "Test Ad", "type": "text"}
                ],
                "advertiser": {"id": "adv123", "name": "Test Advertiser"},
                "tracking": {
                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                },
                "metadata": {
                    "adId": "ad123",
                    "creativeId": "cr456",
                    "placementId": "pl001",
                    "templateId": "tpl789",
                    "priority": "standard"
                },
                "verificationScriptResources": [
                    {
                        "vendorKey": "iabtechlab.com-omid",
                        "scriptUrl": "https://verification.example.com/omid.js",
                        "verificationParameters": "sessionId=abc123"
                    }
                ]
            }
        """.trimIndent()

        val creative = json.decodeFromString<Creative>(jsonString)

        assertTrue(creative.hasOMVerification())
        assertEquals(1, creative.verificationScriptResources?.size)

        val resource = creative.getVerificationResources()?.first()
        assertEquals("iabtechlab.com-omid", resource?.vendorKey)
        assertEquals("https://verification.example.com/omid.js", resource?.scriptUrl)
        assertEquals("sessionId=abc123", resource?.verificationParameters)
    }

    @Test
    fun `Creative with multiple verification resources - parses all vendors`() {
        val jsonString = """
            {
                "contents": [
                    {"key": "headline", "value": "Test Ad", "type": "text"}
                ],
                "advertiser": {"id": "adv123", "name": "Test Advertiser"},
                "tracking": {
                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                },
                "metadata": {
                    "adId": "ad123",
                    "creativeId": "cr456",
                    "placementId": "pl001",
                    "templateId": "tpl789",
                    "priority": "standard"
                },
                "verificationScriptResources": [
                    {
                        "vendorKey": "iabtechlab.com-omid",
                        "scriptUrl": "https://verification.iabtechlab.com/omid.js",
                        "verificationParameters": "sessionId=abc123"
                    },
                    {
                        "vendorKey": "doubleverify.com-omid",
                        "scriptUrl": "https://cdn.doubleverify.com/dvtp_src.js",
                        "verificationParameters": "ctx=1234567&cmp=DV123"
                    },
                    {
                        "vendorKey": "integralads.com-omid",
                        "scriptUrl": "https://cdn.adsafeprotected.com/iasPET.1.js",
                        "verificationParameters": "anId=929999"
                    }
                ]
            }
        """.trimIndent()

        val creative = json.decodeFromString<Creative>(jsonString)

        assertTrue(creative.hasOMVerification())
        assertEquals(3, creative.verificationScriptResources?.size)

        val resources = creative.getVerificationResources()
        assertEquals(3, resources?.size)

        // Verify each vendor is present
        val vendorKeys = resources?.map { it.vendorKey } ?: emptyList()
        assertTrue(vendorKeys.contains("iabtechlab.com-omid"))
        assertTrue(vendorKeys.contains("doubleverify.com-omid"))
        assertTrue(vendorKeys.contains("integralads.com-omid"))
    }

    @Test
    fun `Creative with empty verification resources - hasOMVerification returns false`() {
        val jsonString = """
            {
                "contents": [
                    {"key": "headline", "value": "Test Ad", "type": "text"}
                ],
                "advertiser": {"id": "adv123", "name": "Test Advertiser"},
                "tracking": {
                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                },
                "metadata": {
                    "adId": "ad123",
                    "creativeId": "cr456",
                    "placementId": "pl001",
                    "templateId": "tpl789",
                    "priority": "standard"
                },
                "verificationScriptResources": []
            }
        """.trimIndent()

        val creative = json.decodeFromString<Creative>(jsonString)

        assertFalse(creative.hasOMVerification())
        assertTrue(creative.verificationScriptResources?.isEmpty() == true)
        assertTrue(creative.getVerificationResources()?.isEmpty() == true)
    }

    @Test
    fun `Creative with null verification resources - hasOMVerification returns false`() {
        val jsonString = """
            {
                "contents": [
                    {"key": "headline", "value": "Test Ad", "type": "text"}
                ],
                "advertiser": {"id": "adv123", "name": "Test Advertiser"},
                "tracking": {
                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                },
                "metadata": {
                    "adId": "ad123",
                    "creativeId": "cr456",
                    "placementId": "pl001",
                    "templateId": "tpl789",
                    "priority": "standard"
                },
                "verificationScriptResources": null
            }
        """.trimIndent()

        val creative = json.decodeFromString<Creative>(jsonString)

        assertFalse(creative.hasOMVerification())
        assertNull(creative.verificationScriptResources)
        assertNull(creative.getVerificationResources())
    }

    @Test
    fun `Creative without verification resources field - hasOMVerification returns false`() {
        val jsonString = """
            {
                "contents": [
                    {"key": "headline", "value": "Test Ad", "type": "text"}
                ],
                "advertiser": {"id": "adv123", "name": "Test Advertiser"},
                "tracking": {
                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                },
                "metadata": {
                    "adId": "ad123",
                    "creativeId": "cr456",
                    "placementId": "pl001",
                    "templateId": "tpl789",
                    "priority": "standard"
                }
            }
        """.trimIndent()

        val creative = json.decodeFromString<Creative>(jsonString)

        assertFalse(creative.hasOMVerification())
        assertNull(creative.verificationScriptResources)
        assertNull(creative.getVerificationResources())
    }

    // MARK: - Full API Response Deserialization

    @Test
    fun `Full DecisionResponse with OM verification - parses complete structure`() {
        val jsonString = """
            {
                "success": true,
                "data": [
                    {
                        "placement": "home",
                        "creatives": [
                            {
                                "contents": [
                                    {"key": "headline", "value": "Premium Ad", "type": "text"},
                                    {"key": "video_asset", "value": "https://cdn.example.com/video.mp4", "type": "video"}
                                ],
                                "advertiser": {
                                    "id": "adv123",
                                    "name": "Premium Brand",
                                    "legalName": "Premium Brand Inc.",
                                    "logoUrl": "https://cdn.example.com/logo.png"
                                },
                                "template": {"key": "video", "style": "fullscreen"},
                                "tracking": {
                                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}],
                                    "clicks": [{"key": "default", "url": "https://track.example.com/click"}],
                                    "videoEvents": [
                                        {"key": "start", "url": "https://track.example.com/start"},
                                        {"key": "complete", "url": "https://track.example.com/complete"}
                                    ]
                                },
                                "metadata": {
                                    "adId": "ad123",
                                    "creativeId": "cr456",
                                    "advertiserId": "adv123",
                                    "templateId": "tpl789",
                                    "placementId": "pl001",
                                    "priority": "sponsorship"
                                },
                                "delivery": "json",
                                "verificationScriptResources": [
                                    {
                                        "vendorKey": "iabtechlab.com-omid",
                                        "scriptUrl": "https://verification.iabtechlab.com/omid.js",
                                        "verificationParameters": "sessionId=abc123&adId=ad123"
                                    },
                                    {
                                        "vendorKey": "moat.com-omid",
                                        "scriptUrl": "https://cdn.moat.com/moatad.js",
                                        "verificationParameters": "moatClientLevel1=12345&moatClientLevel2=67890"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(jsonString)

        assertTrue(response.success)
        assertEquals(1, response.data?.size)

        val decision = response.data?.first()
        assertEquals("home", decision?.placement)
        assertEquals(1, decision?.creatives?.size)

        val creative = decision?.creatives?.first()
        assertTrue(creative?.hasOMVerification() == true)
        assertEquals(2, creative?.verificationScriptResources?.size)
        assertEquals("json", creative?.delivery)

        // Verify first verification resource
        val firstResource = creative?.verificationScriptResources?.first()
        assertEquals("iabtechlab.com-omid", firstResource?.vendorKey)
        assertEquals("https://verification.iabtechlab.com/omid.js", firstResource?.scriptUrl)
        assertTrue(firstResource?.verificationParameters?.contains("sessionId=abc123") == true)
    }

    @Test
    fun `DecisionResponse with VAST tag and OM verification - parses both correctly`() {
        val jsonString = """
            {
                "success": true,
                "data": [
                    {
                        "placement": "preroll",
                        "creatives": [
                            {
                                "contents": [],
                                "advertiser": {"id": "adv123", "name": "Video Advertiser"},
                                "tracking": {
                                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                                },
                                "metadata": {
                                    "adId": "ad123",
                                    "creativeId": "cr456",
                                    "placementId": "pl001",
                                    "templateId": "tpl789",
                                    "priority": "standard"
                                },
                                "delivery": "vast_tag",
                                "vast": {
                                    "tagUrl": "https://ads.example.com/vast?id=12345"
                                },
                                "verificationScriptResources": [
                                    {
                                        "vendorKey": "doubleverify.com-omid",
                                        "scriptUrl": "https://cdn.doubleverify.com/dvtp_src.js",
                                        "verificationParameters": "ctx=1234567"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(jsonString)

        val creative = response.data?.first()?.creatives?.first()
        assertEquals("vast_tag", creative?.delivery)
        assertEquals("https://ads.example.com/vast?id=12345", creative?.vast?.tagUrl)
        assertTrue(creative?.hasOMVerification() == true)
        assertEquals("doubleverify.com-omid", creative?.verificationScriptResources?.first()?.vendorKey)
    }

    @Test
    fun `DecisionResponse with VAST XML and OM verification - parses both correctly`() {
        val jsonString = """
            {
                "success": true,
                "data": [
                    {
                        "placement": "midroll",
                        "creatives": [
                            {
                                "contents": [],
                                "advertiser": {"id": "adv456", "name": "XML Advertiser"},
                                "tracking": {
                                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                                },
                                "metadata": {
                                    "adId": "ad123",
                                    "creativeId": "cr456",
                                    "placementId": "pl001",
                                    "templateId": "tpl789",
                                    "priority": "standard"
                                },
                                "delivery": "vast_xml",
                                "vast": {
                                    "xmlBase64": "PFZBU1QgdmVyc2lvbj0iNC4yIj48L1ZBU1Q+"
                                },
                                "verificationScriptResources": [
                                    {
                                        "vendorKey": "integralads.com-omid",
                                        "scriptUrl": "https://cdn.adsafeprotected.com/iasPET.1.js",
                                        "verificationParameters": "anId=929999&campId=12345"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(jsonString)

        val creative = response.data?.first()?.creatives?.first()
        assertEquals("vast_xml", creative?.delivery)
        assertEquals("PFZBU1QgdmVyc2lvbj0iNC4yIj48L1ZBU1Q+", creative?.vast?.xmlBase64)
        assertTrue(creative?.hasOMVerification() == true)
    }

    @Test
    fun `Native ad without OM verification - parses correctly`() {
        val jsonString = """
            {
                "success": true,
                "data": [
                    {
                        "placement": "banner",
                        "creatives": [
                            {
                                "contents": [
                                    {"key": "headline", "value": "Native Ad", "type": "text"},
                                    {"key": "image", "value": "https://cdn.example.com/banner.jpg", "type": "image"}
                                ],
                                "advertiser": {"id": "adv789", "name": "Native Advertiser"},
                                "tracking": {
                                    "impressions": [{"key": "default", "url": "https://track.example.com/imp"}],
                                    "clicks": [{"key": "default", "url": "https://track.example.com/click"}]
                                },
                                "metadata": {
                                    "adId": "ad123",
                                    "creativeId": "cr456",
                                    "placementId": "pl001",
                                    "templateId": "tpl789",
                                    "priority": "standard"
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(jsonString)

        val creative = response.data?.first()?.creatives?.first()
        assertFalse(creative?.hasOMVerification() == true)
        assertNull(creative?.verificationScriptResources)
        assertNull(creative?.delivery)
    }

    // MARK: - Edge Cases and Special Characters

    @Test
    fun `VerificationScriptResource with Unicode characters - parses correctly`() {
        val jsonString = """
            {
                "vendorKey": "测试vendor.com-omid",
                "scriptUrl": "https://verification.example.com/omid.js?name=テスト",
                "verificationParameters": "param=日本語&emoji=🎯"
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals("测试vendor.com-omid", resource.vendorKey)
        assertTrue(resource.scriptUrl.contains("テスト"))
        assertTrue(resource.verificationParameters?.contains("日本語") == true)
    }

    @Test
    fun `VerificationScriptResource with very long parameters - parses correctly`() {
        val longParams = "x".repeat(10000)
        val jsonString = """
            {
                "vendorKey": "longparams.com-omid",
                "scriptUrl": "https://verification.example.com/omid.js",
                "verificationParameters": "$longParams"
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals(10000, resource.verificationParameters?.length)
    }

    @Test
    fun `VerificationScriptResource with URL encoded parameters - preserves encoding`() {
        val jsonString = """
            {
                "vendorKey": "encoded.com-omid",
                "scriptUrl": "https://verification.example.com/omid.js",
                "verificationParameters": "key%3Dvalue%26other%3D%E2%9C%93"
            }
        """.trimIndent()

        val resource = json.decodeFromString<VerificationScriptResource>(jsonString)

        assertEquals("key%3Dvalue%26other%3D%E2%9C%93", resource.verificationParameters)
    }

    // MARK: - Multiple Creatives with Mixed OM Status

    @Test
    fun `Multiple creatives with mixed OM status - each parsed correctly`() {
        val jsonString = """
            {
                "success": true,
                "data": [
                    {
                        "placement": "mixed",
                        "creatives": [
                            {
                                "contents": [{"key": "headline", "value": "Ad 1", "type": "text"}],
                                "advertiser": {"id": "adv1", "name": "Advertiser 1"},
                                "tracking": {"impressions": [{"key": "default", "url": "https://track.example.com/imp1"}]},
                                "metadata": {"adId": "ad1", "creativeId": "cr1", "placementId": "pl1", "templateId": "tpl1", "priority": "standard"},
                                "verificationScriptResources": [
                                    {
                                        "vendorKey": "vendor1.com-omid",
                                        "scriptUrl": "https://vendor1.com/omid.js",
                                        "verificationParameters": "id=1"
                                    }
                                ]
                            },
                            {
                                "contents": [{"key": "headline", "value": "Ad 2", "type": "text"}],
                                "advertiser": {"id": "adv2", "name": "Advertiser 2"},
                                "tracking": {"impressions": [{"key": "default", "url": "https://track.example.com/imp2"}]},
                                "metadata": {"adId": "ad2", "creativeId": "cr2", "placementId": "pl2", "templateId": "tpl2", "priority": "standard"}
                            },
                            {
                                "contents": [{"key": "headline", "value": "Ad 3", "type": "text"}],
                                "advertiser": {"id": "adv3", "name": "Advertiser 3"},
                                "tracking": {"impressions": [{"key": "default", "url": "https://track.example.com/imp3"}]},
                                "metadata": {"adId": "ad3", "creativeId": "cr3", "placementId": "pl3", "templateId": "tpl3", "priority": "standard"},
                                "verificationScriptResources": []
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(jsonString)

        val creatives = response.data?.first()?.creatives
        assertEquals(3, creatives?.size)

        // First creative has OM verification
        assertTrue(creatives?.get(0)?.hasOMVerification() == true)
        assertEquals(1, creatives?.get(0)?.verificationScriptResources?.size)

        // Second creative has no OM verification (field missing)
        assertFalse(creatives?.get(1)?.hasOMVerification() == true)
        assertNull(creatives?.get(1)?.verificationScriptResources)

        // Third creative has empty OM verification array
        assertFalse(creatives?.get(2)?.hasOMVerification() == true)
        assertTrue(creatives?.get(2)?.verificationScriptResources?.isEmpty() == true)
    }

    // MARK: - API Response with Errors

    @Test
    fun `DecisionResponse with errors and no data - parses correctly`() {
        val jsonString = """
            {
                "success": false,
                "data": null,
                "errors": [
                    {"code": 422, "message": "Invalid placement"}
                ],
                "warnings": null
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(jsonString)

        assertFalse(response.success)
        assertNull(response.data)
        assertEquals(1, response.errors?.size)
        assertEquals(422, response.errors?.first()?.code)
    }

    // MARK: - Extension Functions with Data Class Instances

    @Test
    fun `hasOMVerification extension - returns true for non-empty list`() {
        val creative = Creative(
            contents = listOf(
                Content(key = "headline", value = JsonPrimitive("Test"), type = ContentType.TEXT)
            ),
            advertiser = Advertiser(id = "adv1", name = "Test"),
            tracking = TrackingInfo(
                impressions = listOf(TrackingDetail(key = "default", url = "https://example.com"))
            ),
            metadata = CreativeMetadata(
                adId = "ad1",
                creativeId = "cr1",
                placementId = "pl1",
                templateId = "tpl1",
                priority = MetadataPriority.STANDARD
            ),
            verificationScriptResources = listOf(
                VerificationScriptResource(
                    vendorKey = "test.com-omid",
                    scriptUrl = "https://test.com/omid.js",
                    verificationParameters = "test=true"
                )
            )
        )

        assertTrue(creative.hasOMVerification())
        assertNotNull(creative.getVerificationResources())
        assertEquals(1, creative.getVerificationResources()?.size)
    }

    @Test
    fun `hasOMVerification extension - returns false for null list`() {
        val creative = Creative(
            contents = listOf(
                Content(key = "headline", value = JsonPrimitive("Test"), type = ContentType.TEXT)
            ),
            advertiser = Advertiser(id = "adv1", name = "Test"),
            tracking = TrackingInfo(
                impressions = listOf(TrackingDetail(key = "default", url = "https://example.com"))
            ),
            metadata = CreativeMetadata(
                adId = "ad1",
                creativeId = "cr1",
                placementId = "pl1",
                templateId = "tpl1",
                priority = MetadataPriority.STANDARD
            ),
            verificationScriptResources = null
        )

        assertFalse(creative.hasOMVerification())
        assertNull(creative.getVerificationResources())
    }

    @Test
    fun `hasOMVerification extension - returns false for empty list`() {
        val creative = Creative(
            contents = listOf(
                Content(key = "headline", value = JsonPrimitive("Test"), type = ContentType.TEXT)
            ),
            advertiser = Advertiser(id = "adv1", name = "Test"),
            tracking = TrackingInfo(
                impressions = listOf(TrackingDetail(key = "default", url = "https://example.com"))
            ),
            metadata = CreativeMetadata(
                adId = "ad1",
                creativeId = "cr1",
                placementId = "pl1",
                templateId = "tpl1",
                priority = MetadataPriority.STANDARD
            ),
            verificationScriptResources = emptyList()
        )

        assertFalse(creative.hasOMVerification())
        assertNotNull(creative.getVerificationResources())
        assertTrue(creative.getVerificationResources()?.isEmpty() == true)
    }
}
