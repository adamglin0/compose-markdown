package dev.markstream.core.model

import dev.markstream.core.api.MarkdownEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame

class MarkdownSnapshotTest {
    @Test
    fun snapshotRemainsImmutableAfterLaterAppends() {
        val engine = MarkdownEngine()

        val firstSnapshot = engine.append("hello").snapshot
        val secondSnapshot = engine.append(" world").snapshot

        assertNotSame(firstSnapshot, secondSnapshot)
        val firstBlock = firstSnapshot.document.blocks.single() as BlockNode.Paragraph
        val secondBlock = secondSnapshot.document.blocks.single() as BlockNode.Paragraph
        assertEquals("hello", (firstBlock.children.single() as InlineNode.Text).literal)
        assertEquals("hello world", (secondBlock.children.single() as InlineNode.Text).literal)
        assertFalse(firstSnapshot.isFinal)
    }
}
