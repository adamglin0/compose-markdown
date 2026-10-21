package com.adamglin.compose.markdown.core.block

/**
 * A list that starts out as the first [baseSize] elements of [base] and then changes on its own: elements
 * are added after them and removed from the end, while [base] is never written. Forking a block this way
 * costs O(1) however many children or lines it has. [base] must not change while the fork is in use.
 */
internal class ForkedList<T>(
    private val base: List<T>,
    private var baseSize: Int = base.size,
) : AbstractMutableList<T>() {
    private val own = ArrayList<T>()

    override val size: Int
        get() = baseSize + own.size

    override fun get(index: Int): T = if (index < baseSize) base[index] else own[index - baseSize]

    override fun add(index: Int, element: T) {
        require(index == size) { "a forked list only grows at the end" }
        own.add(element)
    }

    override fun removeAt(index: Int): T {
        require(index == size - 1) { "a forked list only shrinks at the end" }
        if (own.isNotEmpty()) {
            return own.removeAt(own.lastIndex)
        }
        baseSize -= 1
        return base[baseSize]
    }

    override fun set(index: Int, element: T): T {
        val previous = removeAt(index)
        own.add(element)
        return previous
    }
}
