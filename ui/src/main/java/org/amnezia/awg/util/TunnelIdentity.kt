package org.amnezia.awg.util

import org.amnezia.awg.config.Config

/**
 * Two configs identify the SAME tunnel — independent of the tunnel name — iff their interface
 * public keys are equal AND their first peer's endpoint is equal. Endpoints are compared by string
 * form; two configs that both lack an endpoint are equal on that dimension. The public key is used
 * (not the private key) because it is derived deterministically from the private key — an identical
 * identity test that does not touch secret-key bytes.
 */
fun sameTunnelIdentity(a: Config, b: Config): Boolean {
    val sameKey = a.`interface`.keyPair.publicKey == b.`interface`.keyPair.publicKey
    if (!sameKey) return false
    return firstEndpoint(a) == firstEndpoint(b)
}

private fun firstEndpoint(config: Config): String? =
    config.peers.firstOrNull()?.endpoint?.orElse(null)?.toString()
