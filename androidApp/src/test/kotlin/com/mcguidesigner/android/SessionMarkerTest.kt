package com.mcguidesigner.android

import com.mcguidesigner.android.io.SessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session marker written alongside a backgrounded document.
 *
 * Android can kill the process at any point, so this file is the only thing
 * that remembers whether the restored document still owes the user a save.
 * Both failure directions are silent: losing the dirty flag loses their edits,
 * inventing one nags about a document that is already on disk.
 */
class SessionMarkerTest {

    @Test
    fun aDirtyDocumentWithAUriRoundTrips() {
        val encoded = SessionStore.encodeMarker(true, "content://docs/1234")
        val decoded = SessionStore.decodeMarker(encoded)

        assertTrue(decoded.dirty)
        assertEquals("content://docs/1234", decoded.uri)
    }

    @Test
    fun aCleanDocumentRoundTrips() {
        val decoded = SessionStore.decodeMarker(SessionStore.encodeMarker(false, "content://docs/1"))

        assertFalse(decoded.dirty)
        assertEquals("content://docs/1", decoded.uri)
    }

    @Test
    fun aDocumentThatWasNeverSavedHasNoUri() {
        val decoded = SessionStore.decodeMarker(SessionStore.encodeMarker(true, null))

        assertTrue(decoded.dirty)
        assertNull(decoded.uri, "a never-saved document must not restore a bogus Uri")
    }

    @Test
    fun anEmptyOrTruncatedMarkerReadsAsCleanWithNoUri() {
        listOf("", "\n", "0", "1").forEach { text ->
            val decoded = SessionStore.decodeMarker(text)
            assertNull(decoded.uri, "'$text' should not yield a Uri")
        }
        assertFalse(SessionStore.decodeMarker("").dirty)
        assertTrue(SessionStore.decodeMarker("1").dirty, "a truncated marker should keep the dirty flag it has")
    }

    @Test
    fun aTrailingNewlineDoesNotBecomeAUri() {
        assertNull(SessionStore.decodeMarker("1\n\n").uri)
    }
}
