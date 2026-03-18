package dev.markstream.core.engine

import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.BlockChange
import dev.markstream.core.model.BlockId
import dev.markstream.core.model.MarkdownDocument
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta
import dev.markstream.core.model.PlainTextBlock
import dev.markstream.core.model.TextRange

internal class PlaceholderMarkdownEngine : MarkdownEngine {
    private val _source = StringBuilder()
    private var _version = 0L
    private var _stablePrefixEnd = 0
    private var _isFinal = false
    private var _snapshot = emptySnapshot()

    override fun append(chunk: String): ParseDelta {
        if (chunk.isEmpty()) {
            return noChangeDelta()
        }

        val previousLength = _source.length
        _source.append(chunk)
        _stablePrefixEnd = maxOf(_stablePrefixEnd, previousLength)
        _isFinal = false

        return rebuild(
            dirtyRegion = TextRange(previousLength, _source.length),
            includeChangedBlocks = true,
        )
    }

    override fun finish(): ParseDelta {
        if (_isFinal) {
            return noChangeDelta()
        }

        _isFinal = true
        _stablePrefixEnd = _source.length

        return rebuild(
            dirtyRegion = TextRange(_source.length, _source.length),
            includeChangedBlocks = false,
        )
    }

    override fun snapshot(): MarkdownSnapshot = _snapshot

    override fun reset() {
        _source.clear()
        _version = 0L
        _stablePrefixEnd = 0
        _isFinal = false
        _snapshot = emptySnapshot()
    }

    private fun rebuild(
        dirtyRegion: TextRange,
        includeChangedBlocks: Boolean,
    ): ParseDelta {
        _version += 1

        val blocks = currentBlocks()
        _snapshot = MarkdownSnapshot(
            version = _version,
            document = MarkdownDocument(
                sourceLength = _source.length,
                blocks = blocks,
            ),
            stablePrefixEnd = _stablePrefixEnd,
            dirtyRegion = dirtyRegion,
            isFinal = _isFinal,
        )

        return ParseDelta(
            version = _version,
            changedBlocks = if (includeChangedBlocks) {
                blocks.mapIndexed { index, block ->
                    BlockChange(
                        id = block.id,
                        newIndex = index,
                        block = block,
                    )
                }
            } else {
                emptyList()
            },
            removedBlockIds = emptyList(),
            stablePrefixEnd = _stablePrefixEnd,
            dirtyRegion = dirtyRegion,
            snapshot = _snapshot,
        )
    }

    private fun currentBlocks(): List<PlainTextBlock> {
        if (_source.isEmpty()) {
            return emptyList()
        }

        return listOf(
            PlainTextBlock(
                id = BlockId(raw = 1L),
                range = TextRange(start = 0, endExclusive = _source.length),
                text = _source.toString(),
            ),
        )
    }

    private fun noChangeDelta(): ParseDelta = ParseDelta(
        version = _snapshot.version,
        changedBlocks = emptyList(),
        removedBlockIds = emptyList(),
        stablePrefixEnd = _snapshot.stablePrefixEnd,
        dirtyRegion = TextRange(_source.length, _source.length),
        snapshot = _snapshot,
    )

    private fun emptySnapshot(): MarkdownSnapshot = MarkdownSnapshot(
        version = 0L,
        document = MarkdownDocument(
            sourceLength = 0,
            blocks = emptyList(),
        ),
        stablePrefixEnd = 0,
        dirtyRegion = TextRange.Empty,
        isFinal = false,
    )
}
