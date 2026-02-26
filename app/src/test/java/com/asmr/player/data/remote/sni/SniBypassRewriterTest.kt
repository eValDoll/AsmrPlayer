package com.asmr.player.data.remote.sni

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SniBypassRewriterTest {
    @Test
    fun matchesAny_suffixMatch_respectsDotBoundary() {
        val rules = listOf(SniBypassRule("dlsite.com", true))
        assertTrue(SniBypassRewriter.matchesAny("dlsite.com", rules))
        assertTrue(SniBypassRewriter.matchesAny("www.dlsite.com", rules))
        assertFalse(SniBypassRewriter.matchesAny("notdlsite.com", rules))
        assertFalse(SniBypassRewriter.matchesAny("dlsite.com.evil", rules))
    }

    @Test
    fun rewrite_hostHeader_preservesPathAndQuery() {
        val proxy = "https://proxy.example.com/".toHttpUrl()
        val original = "https://play.dlsite.com/api/v3/download/sign/url?workno=RJ00000000".toHttpUrl()
        val rewritten = SniBypassRewriter.rewrite(proxy, original, "play.dlsite.com", SniBypassMode.HostHeader)!!
        assertEquals("https", rewritten.scheme)
        assertEquals("proxy.example.com", rewritten.host)
        assertEquals("/api/v3/download/sign/url", rewritten.encodedPath)
        assertEquals("workno=RJ00000000", rewritten.encodedQuery)
    }

    @Test
    fun rewrite_pathEncoded_includesTargetHostInPath() {
        val proxy = "https://proxy.example.com/".toHttpUrl()
        val original = "https://www.dlsite.com/maniax/work/=/product_id/RJ00000000.html?x=1".toHttpUrl()
        val rewritten = SniBypassRewriter.rewrite(proxy, original, "www.dlsite.com", SniBypassMode.PathEncoded)!!
        assertEquals("/www.dlsite.com/maniax/work/=/product_id/RJ00000000.html", rewritten.encodedPath)
        assertEquals("x=1", rewritten.encodedQuery)
    }
}

