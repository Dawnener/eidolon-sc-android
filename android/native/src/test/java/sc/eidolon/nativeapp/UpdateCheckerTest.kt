package sc.eidolon.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.data.repo.isNewer

class UpdateCheckerTest {
    @Test
    fun comparesDottedVersionsNumerically() {
        assertTrue(isNewer("0.1.10", "0.1.9"))
        assertTrue(isNewer("0.2.0", "0.1.99"))
        assertTrue(isNewer("1.0", "0.9.9"))
        assertFalse(isNewer("0.1.9", "0.1.9"))
        assertFalse(isNewer("0.1.8", "0.1.9"))
        assertFalse(isNewer("0.1.9", "0.1.10"))
    }

    @Test
    fun ignoresSuffixesAndShortVersions() {
        assertTrue(isNewer("0.1.10-beta", "0.1.9"))
        assertFalse(isNewer("0.1", "0.1.0"))
    }
}
