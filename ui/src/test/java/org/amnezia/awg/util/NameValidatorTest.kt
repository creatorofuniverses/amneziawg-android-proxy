// ui/src/test/java/org/amnezia/awg/util/NameValidatorTest.kt
package org.amnezia.awg.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameValidatorTest {
    @Test fun validName() = assertNull(NameValidator.validate("awg-fi-01"))
    @Test fun empty() = assertEquals(NameError.EMPTY, NameValidator.validate(""))
    @Test fun blankIsEmpty() = assertEquals(NameError.EMPTY, NameValidator.validate("   "))
    @Test fun cyrillicIsBadChars() = assertEquals(NameError.BAD_CHARS, NameValidator.validate("тунель"))
    @Test fun spaceIsBadChars() = assertEquals(NameError.BAD_CHARS, NameValidator.validate("my tunnel"))
    @Test fun tooLong() = assertEquals(NameError.TOO_LONG, NameValidator.validate("0123456789abcdef")) // 16 chars
    @Test fun maxLengthOk() = assertNull(NameValidator.validate("0123456789abcde"))                    // 15 chars
}
