package com.adamglin.compose.markdown.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextRangeTest {
    @Test
    fun textRangeUsesHalfOpenIntervals() {
        val range = TextRange(start = 2, endExclusive = 5)

        assertEquals(3, range.length)
        assertTrue(range.contains(2))
        assertTrue(range.contains(4))
        assertFalse(range.contains(5))
    }

    @Test
    fun unionProducesSmallestCoveringRange() {
        val left = TextRange(start = 2, endExclusive = 5)
        val right = TextRange(start = 4, endExclusive = 9)

        assertEquals(TextRange(start = 2, endExclusive = 9), left.union(right))
        assertTrue(left.intersects(right))
    }
}
