package com.mcguidesigner.core

import com.mcguidesigner.core.editor.DocumentSet
import com.mcguidesigner.core.model.Edition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentSetTest {

    @Test
    fun `closing the tab in front moves right`() {
        // [a, B, c] -> [a, c], and c is in front.
        assertEquals(1, DocumentSet.activeAfterClose(active = 1, closing = 1, size = 3))
    }

    @Test
    fun `closing the last tab in front falls back to its left`() {
        // [a, b, C] -> [a, b], and b is in front. Staying at index 2 would be
        // out of bounds, which is the crash this guards.
        assertEquals(1, DocumentSet.activeAfterClose(active = 2, closing = 2, size = 3))
    }

    @Test
    fun `closing to the left keeps the same document in front`() {
        // [a, b, C] close a -> [b, C]. The active document must still be C,
        // which is now index 1, not index 2.
        assertEquals(1, DocumentSet.activeAfterClose(active = 2, closing = 0, size = 3))
    }

    @Test
    fun `closing to the right does not disturb the selection`() {
        assertEquals(0, DocumentSet.activeAfterClose(active = 0, closing = 2, size = 3))
    }

    @Test
    fun `closing the only tab lands on zero`() {
        assertEquals(0, DocumentSet.activeAfterClose(active = 0, closing = 0, size = 1))
    }

    @Test
    fun `an edition card brings its existing tab forward`() {
        val open = listOf(Edition.JAVA, Edition.BEDROCK, Edition.JAVA)
        assertEquals(0, DocumentSet.existingTabFor(open, Edition.JAVA), "the first, always the same one")
        assertEquals(1, DocumentSet.existingTabFor(open, Edition.BEDROCK))
        assertNull(DocumentSet.existingTabFor(listOf(Edition.JAVA), Edition.BEDROCK))
        assertNull(DocumentSet.existingTabFor(emptyList(), Edition.JAVA))
    }

    @Test
    fun `untitled documents do not collide`() {
        val java = Edition.JAVA
        val first = DocumentSet.untitledName(java, emptyList())
        val second = DocumentSet.untitledName(java, listOf(first))
        val third = DocumentSet.untitledName(java, listOf(first, second))

        assertEquals("Untitled ${java.displayName}", first)
        assertEquals("Untitled ${java.displayName} 2", second)
        assertEquals("Untitled ${java.displayName} 3", third)
    }

    @Test
    fun `each edition names its own documents`() {
        assertEquals(
            DocumentSet.untitledName(Edition.JAVA, emptyList()),
            DocumentSet.untitledName(Edition.JAVA, listOf("something else")),
        )
        // A Java and a Bedrock document open at once are not a collision.
        val java = DocumentSet.untitledName(Edition.JAVA, emptyList())
        assertEquals(
            "Untitled ${Edition.BEDROCK.displayName}",
            DocumentSet.untitledName(Edition.BEDROCK, listOf(java)),
        )
    }
}
