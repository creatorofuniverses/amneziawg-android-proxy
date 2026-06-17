package org.amnezia.awg.util

import org.amnezia.awg.config.Config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class TunnelIdentityTest {
    // Two distinct, valid 32-byte base64 keys (same form as ShareStringDecoderTest).
    private val key1 = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="
    private val key2 = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A="
    private val peerPub = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A="

    /** Build a Config from an interface private key, an optional endpoint, and an optional DNS line. */
    private fun config(privKey: String, endpoint: String?, dns: String? = null): Config {
        val sb = StringBuilder()
        sb.append("[Interface]\n")
        sb.append("PrivateKey = ").append(privKey).append('\n')
        sb.append("Address = 10.8.0.2/32\n")
        if (dns != null) sb.append("DNS = ").append(dns).append('\n')
        sb.append("[Peer]\n")
        sb.append("PublicKey = ").append(peerPub).append('\n')
        if (endpoint != null) sb.append("Endpoint = ").append(endpoint).append('\n')
        sb.append("AllowedIPs = 0.0.0.0/0\n")
        return Config.parse(ByteArrayInputStream(sb.toString().toByteArray(StandardCharsets.UTF_8)))
    }

    @Test fun sameKeySameEndpoint_isDuplicate() {
        assertTrue(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key1, "192.0.2.1:51820")))
    }

    @Test fun sameKeySameEndpoint_otherFieldsDiffer_isDuplicate() {
        // Only interface key + first-peer endpoint matter; a different DNS must not change the verdict.
        assertTrue(sameTunnelIdentity(config(key1, "192.0.2.1:51820", dns = "1.1.1.1"),
                                      config(key1, "192.0.2.1:51820", dns = "8.8.8.8")))
    }

    @Test fun sameKeyDifferentEndpoint_isNotDuplicate() {
        assertFalse(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key1, "198.51.100.7:51820")))
    }

    @Test fun differentKeySameEndpoint_isNotDuplicate() {
        assertFalse(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key2, "192.0.2.1:51820")))
    }

    @Test fun bothNoEndpoint_sameKey_isDuplicate() {
        assertTrue(sameTunnelIdentity(config(key1, null), config(key1, null)))
    }

    @Test fun oneNoEndpoint_isNotDuplicate() {
        assertFalse(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key1, null)))
    }
}
