package com.adamglin.compose.markdown.core.model

import kotlin.jvm.JvmInline

@JvmInline
public value class BlockId(public val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }
}

@JvmInline
public value class InlineId(public val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }
}
