package dev.markstream.core.model

import kotlin.jvm.JvmInline

@JvmInline
value class BlockId(val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }

    companion object {
        val Document = BlockId(raw = 0L)
    }
}

@JvmInline
value class InlineId(val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }
}
