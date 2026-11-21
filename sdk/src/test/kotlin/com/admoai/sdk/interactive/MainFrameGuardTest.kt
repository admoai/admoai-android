package com.admoai.sdk.interactive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainFrameGuardTest {
    private val launch = "https://forms.example.com/lead/abc?x=1#instanceId=i&channel=c"

    private fun allow(
        method: String = "GET",
        url: String = launch,
        already: Boolean = false,
        launchUrl: String = launch
    ) = MainFrameGuard.allow(method, url, launchUrl, already)

    @Test
    fun `launch GET once is allowed`() = assertTrue(allow())

    @Test
    fun `launch a second time is refused`() = assertFalse(allow(already = true))

    @Test
    fun `POST to the launch URL is refused`() = assertFalse(allow(method = "POST"))

    @Test
    fun `lowercase get is allowed`() = assertTrue(allow(method = "get"))

    @Test
    fun `another URL is refused`() = assertFalse(allow(url = "https://forms.example.com/lead/other?x=1"))

    @Test
    fun `another host is refused`() = assertFalse(allow(url = "https://evil.example.com/lead/abc?x=1"))

    @Test
    fun `fragment is ignored`() = assertTrue(allow(url = "https://forms.example.com/lead/abc?x=1#other"))

    @Test
    fun `explicit 443 on the request is ignored`() =
        assertTrue(allow(url = "https://forms.example.com:443/lead/abc?x=1"))

    @Test
    fun `explicit 443 on the launch URL is ignored`() = assertTrue(
        allow(
            launchUrl = "https://forms.example.com:443/lead/abc?x=1#a",
            url = "https://forms.example.com/lead/abc?x=1"
        )
    )

    @Test
    fun `other port is not equal`() = assertFalse(allow(url = "https://forms.example.com:8443/lead/abc?x=1"))

    @Test
    fun `host and scheme are case-insensitive`() = assertTrue(allow(url = "HTTPS://Forms.Example.COM/lead/abc?x=1"))

    @Test
    fun `query must match`() = assertFalse(allow(url = "https://forms.example.com/lead/abc?x=2"))

    @Test
    fun `path must match`() = assertFalse(allow(url = "https://forms.example.com/lead/ABC?x=1"))

    @Test
    fun `unparseable URL is refused`() = assertFalse(allow(url = "not a url"))
}
