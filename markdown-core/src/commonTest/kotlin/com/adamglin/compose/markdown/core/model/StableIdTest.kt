package com.adamglin.compose.markdown.core.model

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class StableIdTest {
    @Test
    fun stableBlockIdSurvivesAppendOnlyGrowth() {
        val engine = MarkdownEngine()

        val first = engine.append("hello")
        val second = engine.append(" world")

        assertEquals(
            first.snapshot.document.blocks.single().id,
            second.snapshot.document.blocks.single().id,
        )
    }

    @Test
    fun documentRootUsesReservedStableId() {
        val engine = MarkdownEngine()

        val snapshot = engine.append("hello").snapshot

        assertEquals(BlockId.Document, snapshot.document.root.id)
    }
}
