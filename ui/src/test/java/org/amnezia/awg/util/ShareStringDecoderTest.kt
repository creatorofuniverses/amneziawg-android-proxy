package org.amnezia.awg.util

import org.amnezia.awg.config.ConfigShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareStringDecoderTest {
    private val conf = """
        # Name = awg-fi-01
        [Interface]
        PrivateKey = AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=
        Address = 10.8.0.2/32
        [Peer]
        PublicKey = ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A=
        Endpoint = 192.0.2.1:51820
        AllowedIPs = 0.0.0.0/0
    """.trimIndent() + "\n"

    @Test fun decodesAwgString() {
        val r = ShareStringDecoder.decode("  " + ConfigShare.encode(conf) + "  ")
        assertEquals("awg-fi-01", r.name)
        assertEquals("192.0.2.1:51820", r.endpoint)
    }

    @Test fun decodesRawConf() {
        val r = ShareStringDecoder.decode(conf)
        assertEquals("awg-fi-01", r.name)
    }

    @Test fun fallsBackToEndpointHostWhenNoNameComment() {
        val noName = conf.replace("# Name = awg-fi-01\n", "")
        val r = ShareStringDecoder.decode(noName)
        assertEquals("192.0.2.1", r.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGarbage() {
        ShareStringDecoder.decode("not a config at all")
    }

    @Test fun obfuscationSummaryReflectsPresence() {
        val r = ShareStringDecoder.decode(conf)
        assertTrue(r.obfuscation.isNotEmpty())
    }
}
