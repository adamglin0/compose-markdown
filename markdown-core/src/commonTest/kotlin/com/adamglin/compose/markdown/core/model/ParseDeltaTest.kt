package com.adamglin.compose.markdown.core.model

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseDeltaTest {
    @Test
    fun finishReportsEmptyDirtyRangeAndSnapshotVersion() {
        val engine = MarkdownEngine()

        engine.append("hello")
        val delta = engine.finish()

        assertEquals(delta.version, delta.snapshot.version)
        assertEquals(0, delta.dirtyRegion.start)
        assertEquals(delta.snapshot.document.sourceLength, delta.dirtyRegion.endExclusive)
        assertEquals(TextRange(0, 5), delta.snapshot.stablePrefixRange)
        assertTrue(delta.changedBlocks.isEmpty())
        assertTrue(delta.snapshot.isFinal)
    }

    @Test
    fun emptyAppendReturnsNoOpDelta() {
        val engine = MarkdownEngine()

        val delta = engine.append("")

        assertEquals(0L, delta.version)
        assertTrue(delta.changedBlocks.isEmpty())
        assertTrue(delta.removedBlockIds.isEmpty())
        assertTrue(delta.dirtyRegion.isEmpty)
        assertEquals(ParseStats.Empty, delta.stats)
    }
}
