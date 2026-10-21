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

        assertEquals(0, delta.dirtyRegion.start)
        assertEquals(delta.snapshot.document.sourceLength, delta.dirtyRegion.endExclusive)
        assertEquals(5, delta.snapshot.stablePrefixEnd)
        assertTrue(delta.changedBlocks.isEmpty())
        assertTrue(delta.snapshot.isFinal)
    }

    @Test
    fun emptyAppendReturnsNoOpDelta() {
        val engine = MarkdownEngine()

        val delta = engine.append("")

        assertEquals(0L, delta.snapshot.version)
        assertTrue(delta.changedBlocks.isEmpty())
        assertTrue(delta.removedBlockIds.isEmpty())
        assertEquals(delta.dirtyRegion.start, delta.dirtyRegion.endExclusive)
        assertEquals(ParseStats.Empty, delta.stats)
    }
}
