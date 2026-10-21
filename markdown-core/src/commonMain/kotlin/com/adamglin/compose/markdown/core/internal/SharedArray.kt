package com.adamglin.compose.markdown.core.internal

/**
 * Append-only storage whose current elements are published as immutable lists without copying them.
 * A published list reads only the slots that were filled before it was published, and [set] copies the
 * storage before writing, so published lists never observe a change.
 */
internal class SharedArray<T> {
    private var storage = arrayOfNulls<Any?>(INITIAL_CAPACITY)

    var size: Int = 0
        private set

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T = storage[index] as T

    fun add(element: T) {
        if (size == storage.size) {
            storage = storage.copyOf(newSize = size * 2)
        }
        storage[size] = element
        size += 1
    }

    operator fun set(index: Int, element: T) {
        storage = storage.copyOf()
        storage[index] = element
    }

    fun clear() {
        storage = arrayOfNulls(INITIAL_CAPACITY)
        size = 0
    }

    /** The current elements followed by [tail], as an immutable list that shares the storage. */
    fun followedBy(tail: List<T>): List<T> = if (size == 0) tail else PrefixList(storage = storage, prefixSize = size, tail = tail)

    private class PrefixList<T>(
        private val storage: Array<Any?>,
        private val prefixSize: Int,
        private val tail: List<T>,
    ) : AbstractList<T>() {
        override val size: Int
            get() = prefixSize + tail.size

        @Suppress("UNCHECKED_CAST")
        override fun get(index: Int): T {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("index $index, size $size")
            }
            return if (index < prefixSize) storage[index] as T else tail[index - prefixSize]
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 8
    }
}
