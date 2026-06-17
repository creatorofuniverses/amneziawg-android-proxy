// ui/src/test/java/org/amnezia/awg/widget/NameInputFilterTest.kt
package org.amnezia.awg.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameInputFilterTest {
    @Test fun asciiLettersAndDigitsAllowed() {
        for (c in "abcXYZ0189") assertTrue("$c should be allowed", NameInputFilter.isAllowed(c))
    }

    @Test fun symbolSubsetAllowed() {
        for (c in "_=+.-") assertTrue("$c should be allowed", NameInputFilter.isAllowed(c))
    }

    @Test fun cyrillicRejected() {
        for (c in "тунельЯ") assertFalse("$c must be rejected", NameInputFilter.isAllowed(c))
    }

    @Test fun spaceAndOtherSymbolsRejected() {
        for (c in " /\\@#") assertFalse("$c must be rejected", NameInputFilter.isAllowed(c))
    }
}
