package com.adamglin.compose.markdown.core.model

import com.adamglin.compose.markdown.core.api.MarkdownEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(delta.snapshot.stablePrefixRange, delta.stablePrefixRange)
        assertTrue(delta.insertedBlockIds.isEmpty())
        assertTrue(delta.updatedBlockIds.isEmpty())
        assertTrue(delta.snapshot.isFinal)
        assertFalse(delta.isNoOp)
    }

    @Test
    fun emptyAppendReturnsNoOpDelta() {
        val engine = MarkdownEngine()

        val delta = engine.append("")

        assertTrue(delta.isNoOp)
        assertEquals(ParseStats.Empty, delta.stats)
    }
}
