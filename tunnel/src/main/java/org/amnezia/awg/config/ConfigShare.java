// tunnel/src/main/java/org/amnezia/awg/config/ConfigShare.java
/*
 * Copyright © 2024 AmneziaWG. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Codec for the {@code awg://v1} config share-string:
 * {@code awg://v1/<base64url( zlib( utf8(conf) ) )>}.
 * zlib = RFC 1950 (Deflater/Inflater nowrap=false); base64url = RFC 4648 §5, no padding.
 * Pure-JVM (no android.util.Base64) so it is unit-testable without Robolectric and API-24 safe.
 */
public final class ConfigShare {
    public static final String PREFIX = "awg://v1/";
    private static final char[] ENC =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    private static final int[] DEC = new int[128];
    static {
        Arrays.fill(DEC, -1);
        for (int i = 0; i < ENC.length; i++) DEC[ENC[i]] = i;
        DEC['+'] = 62; DEC['/'] = 63; // tolerate standard alphabet too
    }

    private ConfigShare() {}

    public static String encode(final String conf) {
        final byte[] raw = conf.getBytes(StandardCharsets.UTF_8);
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false); // false => zlib (RFC1950)
        deflater.setInput(raw);
        deflater.finish();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] tmp = new byte[1024];
        while (!deflater.finished()) out.write(tmp, 0, deflater.deflate(tmp));
        deflater.end();
        return PREFIX + b64urlEncode(out.toByteArray());
    }

    public static String decode(final String shareString) {
        if (shareString == null || !shareString.startsWith(PREFIX))
            throw new IllegalArgumentException("not an awg://v1 string");
        final byte[] comp = b64urlDecode(shareString.substring(PREFIX.length()));
        final Inflater inflater = new Inflater(false);
        inflater.setInput(comp);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] tmp = new byte[1024];
        try {
            while (!inflater.finished()) {
                final int n = inflater.inflate(tmp);
                if (n == 0) {
                    if (inflater.finished() || inflater.needsDictionary()) break;
                    if (inflater.needsInput()) // ran out of bytes mid-stream → truncated
                        throw new IllegalArgumentException("truncated awg://v1 payload");
                }
                out.write(tmp, 0, n);
            }
        } catch (final DataFormatException e) {
            throw new IllegalArgumentException("corrupt awg://v1 payload", e);
        } finally {
            inflater.end();
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static String b64urlEncode(final byte[] data) {
        final StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i + 3 <= data.length) {
            final int n = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8) | (data[i + 2] & 0xff);
            sb.append(ENC[(n >> 18) & 63]).append(ENC[(n >> 12) & 63])
              .append(ENC[(n >> 6) & 63]).append(ENC[n & 63]);
            i += 3;
        }
        final int rem = data.length - i;
        if (rem == 1) {
            final int n = (data[i] & 0xff) << 16;
            sb.append(ENC[(n >> 18) & 63]).append(ENC[(n >> 12) & 63]);
        } else if (rem == 2) {
            final int n = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8);
            sb.append(ENC[(n >> 18) & 63]).append(ENC[(n >> 12) & 63]).append(ENC[(n >> 6) & 63]);
        }
        return sb.toString();
    }

    static byte[] b64urlDecode(final String s) {
        final int[] vals = new int[s.length()];
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '=' || c == '\n' || c == '\r' || c == ' ') continue; // tolerate padding/whitespace
            final int v = c < 128 ? DEC[c] : -1;
            if (v < 0) throw new IllegalArgumentException("bad base64url character: " + c);
            vals[len++] = v;
        }
        final byte[] out = new byte[len * 6 / 8];
        int buf = 0, bits = 0, oi = 0;
        for (int i = 0; i < len; i++) {
            buf = (buf << 6) | vals[i];
            bits += 6;
            if (bits >= 8) { bits -= 8; out[oi++] = (byte) ((buf >> bits) & 0xff); }
        }
        return out;
    }
}
