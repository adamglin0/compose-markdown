package com.adamglin.compose.markdown.core.model

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

@Immutable
@JvmInline
value class BlockId(val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }

    companion object {
        val Document = BlockId(raw = 0L)
    }
}

@Immutable
@JvmInline
value class InlineId(val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }
}
