package org.amnezia.awg.util

import org.amnezia.awg.config.Config
import org.amnezia.awg.config.ConfigShare
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

data class DecodedShare(
    val configText: String,
    val config: Config,
    val name: String,
    val endpoint: String?,
    val obfuscation: String,
)

object ShareStringDecoder {
    private val NAME_COMMENT = Regex("""(?im)^\s*#\s*Name\s*=\s*(.+?)\s*$""")

    fun decode(input: String): DecodedShare {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "empty input" }
        val confText = if (trimmed.startsWith(ConfigShare.PREFIX)) {
            ConfigShare.decode(trimmed)            // throws IllegalArgumentException on corruption
        } else {
            trimmed                                 // assume raw .conf
        }
        val config = try {
            Config.parse(ByteArrayInputStream(confText.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid config: ${e.message}", e)
        }
        val endpoint = config.peers.firstOrNull()?.endpoint?.orElse(null)?.toString()
        val name = NAME_COMMENT.find(confText)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: endpoint?.substringBefore(':')?.takeIf { it.isNotBlank() }
            ?: "tunnel"
        return DecodedShare(confText, config, name, endpoint, obfuscationSummary(config))
    }

    private fun obfuscationSummary(config: Config): String {
        // Compact human summary for the preview card; tolerant of the Interface API surface.
        val iface = config.`interface`
        val parts = mutableListOf<String>()
        runCatching { iface.junkPacketCount.ifPresent { parts += "Jc $it" } }
        runCatching { iface.initPacketMagicHeader.ifPresent { parts += "H1–H4" } }
        return if (parts.isEmpty()) "None" else parts.joinToString(" · ")
    }
}
