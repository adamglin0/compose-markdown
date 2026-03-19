package com.adamglin.compose.markdown.core.source

internal class SourceBuffer {
    private val _content = StringBuilder()
    private var _snapshotCache: String? = null

    val length: Int
        get() = _content.length

    fun append(chunk: String) {
        _content.append(chunk)
        _snapshotCache = null
    }

    fun clear() {
        _content.clear()
        _snapshotCache = null
    }

    fun isEmpty(): Boolean = _content.isEmpty()

    operator fun get(index: Int): Char = _content[index]

    fun lastIndexOf(char: Char): Int {
        for (index in _content.length - 1 downTo 0) {
            if (_content[index] == char) {
                return index
            }
        }
        return -1
    }

    fun snapshot(): String = _snapshotCache ?: _content.toString().also { _snapshotCache = it }
}
