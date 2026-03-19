package com.adamglin.compose.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.adamglin.compose.markdown.core.model.BlockId
import com.adamglin.compose.markdown.core.model.BlockNode
import com.adamglin.compose.markdown.core.model.MarkdownSnapshot
import com.adamglin.compose.markdown.core.model.ParseDelta

@Stable
class MarkdownRendererState internal constructor(
    initialSnapshot: MarkdownSnapshot?,
) {
    private var _version by mutableStateOf(initialSnapshot?.version)
    private var _blocks by mutableStateOf(initialSnapshot?.document?.blocks?.map(::RenderedMarkdownBlock).orEmpty())

    val blocks: List<RenderedMarkdownBlock>
        get() = _blocks

    fun render(snapshot: MarkdownSnapshot) {
        if (_version == snapshot.version) {
            return
        }
        _blocks = snapshot.document.blocks.map(::RenderedMarkdownBlock)
        _version = snapshot.version
    }

    fun apply(delta: ParseDelta) {
        if (_version == null || _blocks.isEmpty() && delta.snapshot.document.blocks.isNotEmpty() && delta.changedBlocks.isEmpty()) {
            render(delta.snapshot)
            return
        }

        val existingById = _blocks.associateBy { it.id }.toMutableMap()
        delta.removedBlockIds.forEach(existingById::remove)
        delta.changedBlocks.forEach { change ->
            existingById[change.id] = RenderedMarkdownBlock(change.block)
        }

        _blocks = delta.snapshot.document.blocks.map { block ->
            existingById[block.id] ?: RenderedMarkdownBlock(block)
        }
        _version = delta.version
    }
}

@Composable
fun rememberMarkdownRendererState(
    snapshot: MarkdownSnapshot? = null,
): MarkdownRendererState = remember {
    MarkdownRendererState(initialSnapshot = snapshot)
}

@Stable
class RenderedMarkdownBlock internal constructor(
    val block: BlockNode,
) {
    val id: BlockId
        get() = block.id
}
