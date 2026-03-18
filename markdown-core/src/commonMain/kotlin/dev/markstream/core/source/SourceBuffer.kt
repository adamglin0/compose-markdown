package dev.markstream.core.source

internal class SourceBuffer {
    private val _content = StringBuilder()

    val length: Int
        get() = _content.length

    fun append(chunk: String) {
        _content.append(chunk)
    }

    fun clear() {
        _content.clear()
    }

    fun isEmpty(): Boolean = _content.isEmpty()

    fun snapshot(): String = _content.toString()
}
