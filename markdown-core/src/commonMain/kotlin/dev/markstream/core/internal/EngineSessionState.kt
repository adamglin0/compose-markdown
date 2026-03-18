package dev.markstream.core.internal

import dev.markstream.core.dialect.MarkdownDialect
import dev.markstream.core.model.MarkdownSnapshot

internal class EngineSessionState(
    val dialect: MarkdownDialect,
    var snapshot: MarkdownSnapshot,
) {
    val source: StringBuilder = StringBuilder()
    val openBlockStack: MutableList<OpenBlockFrame> = mutableListOf()
    val lineIndex: MutableLineIndex = MutableLineIndex()
    val cacheState: ParseCacheState = ParseCacheState()
    var version: Long = snapshot.version
    var stablePrefixEnd: Int = snapshot.stablePrefixEnd
    var isFinal: Boolean = snapshot.isFinal
    var nextBlockId: Long = 1L
}

internal data class OpenBlockFrame(
    val marker: String,
    val startOffset: Int,
)

internal class ParseCacheState {
    val blockIdsByKey: MutableMap<BlockIdentityKey, MutableList<Long>> = linkedMapOf()
}

internal data class BlockIdentityKey(
    val kind: String,
    val start: Int,
    val discriminator: String,
)
