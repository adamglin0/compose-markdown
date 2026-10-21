package com.adamglin.compose.markdown.core.model

import kotlin.jvm.JvmInline

@JvmInline
public value class BlockId internal constructor(public val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }
}

@JvmInline
public value class InlineId internal constructor(public val raw: Long) {
    init {
        require(raw >= 0L) { "raw must be non-negative" }
    }
}
