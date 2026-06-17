package org.amnezia.awg.config;

import static org.junit.Assert.assertEquals;

import org.amnezia.awg.crypto.KeyPair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ObfuscationModeTest {

    private static Interface iface(final String... extraLines) throws Exception {
        final List<String> lines = new ArrayList<>();
        lines.add("PrivateKey = " + new KeyPair().getPrivateKey().toBase64());
        Collections.addAll(lines, extraLines);
        return Interface.parse(lines);
    }

    @Test
    public void plainWireGuard_isWg() throws Exception {
        assertEquals(ObfuscationMode.WG, ObfuscationMode.of(iface()));
    }

    @Test
    public void staticJunkOnly_isAmnezia() throws Exception {
        assertEquals(ObfuscationMode.AMNEZIA, ObfuscationMode.of(iface("Jc = 4")));
    }

    @Test
    public void dynamicSignaturePacket_isProxy() throws Exception {
        // I1 carrying the <…>-tagged dynamic builder format
        assertEquals(ObfuscationMode.PROXY, ObfuscationMode.of(iface("Jc = 4", "I1 = <b 0xf20c7b9a>")));
    }

    @Test
    public void imitateProtocolSet_isProxy() throws Exception {
        assertEquals(ObfuscationMode.PROXY, ObfuscationMode.of(iface("Jc = 4", "ImitateProtocol = quic")));
    }
}
