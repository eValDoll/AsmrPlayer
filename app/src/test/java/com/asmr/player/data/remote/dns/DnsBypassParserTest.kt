package com.asmr.player.data.remote.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsBypassParserTest {
    @Test
    fun parseHostsText_parsesIpAndHostPairs() {
        val text = """
            # comment
            1.2.3.4 example.com
            5.6.7.8 example.com
            9.9.9.9 www.example.com
        """.trimIndent()
        val map = DnsBypassParser.parseHostsText(text)
        assertTrue(map.containsKey("example.com"))
        assertEquals(2, map["example.com"]!!.size)
        assertEquals("1.2.3.4", map["example.com"]!![0].hostAddress)
        assertEquals("5.6.7.8", map["example.com"]!![1].hostAddress)
        assertEquals("9.9.9.9", map["www.example.com"]!![0].hostAddress)
    }
}

