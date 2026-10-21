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
}
