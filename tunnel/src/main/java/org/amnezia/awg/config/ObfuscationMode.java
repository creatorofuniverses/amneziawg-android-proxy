package org.amnezia.awg.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Classifies an {@link Interface}'s obfuscation configuration into one of three
 * user-facing modes for the protocol badge:
 *   WG       — no AmneziaWG obfuscation params at all (plain WireGuard).
 *   AMNEZIA  — obfuscation present, but special-junk I1..I5 are all static and no
 *              traffic-imitation protocol is configured.
 *   PROXY    — any special-junk uses the dynamic {@code <…>}-tagged builder format,
 *              or a traffic-imitation protocol is configured.
 */
public enum ObfuscationMode {
    WG, AMNEZIA, PROXY;

    public static ObfuscationMode of(final Interface iface) {
        final List<Optional<String>> specialJunk = Arrays.asList(
                iface.getSpecialJunkI1(), iface.getSpecialJunkI2(), iface.getSpecialJunkI3(),
                iface.getSpecialJunkI4(), iface.getSpecialJunkI5());

        final boolean hasObfuscation =
                iface.getJunkPacketCount().isPresent() || iface.getJunkPacketMinSize().isPresent() ||
                iface.getJunkPacketMaxSize().isPresent() || iface.getInitPacketJunkSize().isPresent() ||
                iface.getResponsePacketJunkSize().isPresent() || iface.getCookieReplyPacketJunkSize().isPresent() ||
                iface.getTransportPacketJunkSize().isPresent() || iface.getInitPacketMagicHeader().isPresent() ||
                iface.getResponsePacketMagicHeader().isPresent() || iface.getUnderloadPacketMagicHeader().isPresent() ||
                iface.getTransportPacketMagicHeader().isPresent() ||
                specialJunk.stream().anyMatch(Optional::isPresent) ||
                iface.getImitateProtocol().isPresent();

        if (!hasObfuscation)
            return WG;

        final boolean dynamicSignature = specialJunk.stream()
                .filter(Optional::isPresent).map(Optional::get)
                .anyMatch(s -> s.contains("<"));

        if (dynamicSignature || iface.getImitateProtocol().isPresent())
            return PROXY;

        return AMNEZIA;
    }
}
