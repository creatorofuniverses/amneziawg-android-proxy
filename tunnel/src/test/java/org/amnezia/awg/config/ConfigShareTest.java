// tunnel/src/test/java/org/amnezia/awg/config/ConfigShareTest.java
package org.amnezia.awg.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ConfigShareTest {
    private static final String CONF =
        "[Interface]\n" +
        "PrivateKey = aP8A1234567890abcdefghijklmnopqrstuvwxyzABC=\n" +
        "Address = 10.8.0.2/32\n" +
        "DNS = 1.1.1.1\n" +
        "Jc = 4\nJmin = 40\nJmax = 70\n" +
        "[Peer]\n" +
        "PublicKey = bQ9B1234567890abcdefghijklmnopqrstuvwxyzABC=\n" +
        "Endpoint = 192.0.2.1:51820\n" +
        "AllowedIPs = 0.0.0.0/0\n";

    @Test public void roundTrips() {
        assertEquals(CONF, ConfigShare.decode(ConfigShare.encode(CONF)));
    }

    @Test public void encodeHasPrefix() {
        assertTrue(ConfigShare.encode(CONF).startsWith("awg://v1/"));
    }

    @Test public void encodeHasNoBase64Padding() {
        final String payload = ConfigShare.encode(CONF).substring(ConfigShare.PREFIX.length());
        assertTrue("payload must not contain '='", payload.indexOf('=') < 0);
        assertTrue("payload must be url-safe alphabet",
            payload.matches("[A-Za-z0-9_-]+"));
    }

    @Test public void decodeRejectsWrongPrefix() {
        try { ConfigShare.decode("https://example.com/x"); fail("expected IAE"); }
        catch (final IllegalArgumentException expected) { /* ok */ }
    }

    @Test public void decodeRejectsTruncatedPayload() {
        final String s = ConfigShare.encode(CONF);
        try { ConfigShare.decode(s.substring(0, s.length() - 5)); fail("expected throw"); }
        catch (final RuntimeException expected) { /* corrupt → loud failure */ }
    }

    @Test public void decodesPythonGeneratedVector() throws Exception {
        final String conf = readResource("share-vector.conf");
        final String shareString = readResource("share-vector.awg").trim();
        assertEquals(conf, ConfigShare.decode(shareString));
    }

    @Test public void javaEncodeRoundTripsVectorConf() throws Exception {
        final String conf = readResource("share-vector.conf");
        assertEquals(conf, ConfigShare.decode(ConfigShare.encode(conf)));
    }

    private static String readResource(final String name) throws java.io.IOException {
        try (final java.io.InputStream in = ConfigShareTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new java.io.IOException("missing resource " + name);
            final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            final byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
