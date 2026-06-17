// ui/src/main/java/org/amnezia/awg/util/NameValidator.kt
package org.amnezia.awg.util

import org.amnezia.awg.backend.Tunnel

enum class NameError { EMPTY, BAD_CHARS, TOO_LONG }

object NameValidator {
    private val ALLOWED = Regex("[a-zA-Z0-9_=+.-]+")

    /** Returns the first problem with [name], or null if it is a valid tunnel name. */
    fun validate(name: String): NameError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameError.EMPTY
            !ALLOWED.matches(trimmed) -> NameError.BAD_CHARS
            trimmed.length > Tunnel.NAME_MAX_LENGTH -> NameError.TOO_LONG
            else -> null
        }
    }
}
