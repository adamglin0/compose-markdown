package com.adamglin.compose.markdown.benchmarks

internal object BenchmarkCorpus {
    fun largeParagraphDocument(repetitions: Int = 500): String = buildString(repetitions * 180) {
        repeat(repetitions) { index ->
            append("Streaming markdown paragraph ")
            append(index)
            append(" with **strong**, _emphasis_, `code`, and https://example.com/")
            append(index)
            append(" to keep inline parsing realistic while staying mostly paragraph-heavy.\n\n")
        }
    }

    fun mixedDocument(sections: Int = 40): String = buildString(sections * 320) {
        repeat(sections) { index ->
            append("# Section ")
            append(index)
            append("\n\n")
            append("Plain paragraph with **bold**, _italic_, [inline link](https://example.com/")
            append(index)
            append("), and bare url https://compose-markdown.dev/")
            append(index)
            append(".\n\n")
            append("- item one\n")
            append("- [x] task item\n")
            append("- item three with `inline code`\n\n")
            append("> quoted line\n")
            append("> second quoted line\n\n")
            append("```kotlin\n")
            append("val value = ")
            append(index)
            append("\nprintln(value)\n")
            append("```\n\n")
            append("| name | value | note |\n")
            append("| --- | ---: | :--- |\n")
            append("| alpha | ")
            append(index)
            append(" | stable |\n")
            append("| beta | ")
            append(index + 1)
            append(" | mutable |\n\n")
        }
    }

    fun chunkedMixedDocument(chunkCount: Int = 240): List<String> {
        val document = mixedDocument(sections = 60)
        return chunkText(document = document, chunkCount = chunkCount)
    }

    private fun chunkText(document: String, chunkCount: Int): List<String> {
        require(chunkCount in 1..document.length) { "chunkCount must be within document size" }

        val chunks = ArrayList<String>(chunkCount)
        var start = 0
        repeat(chunkCount) { chunkIndex ->
            val remainingChars = document.length - start
            val remainingChunks = chunkCount - chunkIndex
            val targetSize = if (remainingChunks == 1) {
                remainingChars
            } else {
                val average = remainingChars / remainingChunks
                val wobble = (chunkIndex % 5) - 2
                (average + wobble * 3).coerceIn(24, remainingChars - (remainingChunks - 1))
            }
            val endExclusive = (start + targetSize).coerceAtMost(document.length - (remainingChunks - 1))
            chunks += document.substring(start, endExclusive)
            start = endExclusive
        }
        return chunks
    }

    /** A mixed document cut to exactly [chars] characters. */
    fun mixedDocumentOfSize(chars: Int): String {
        val section = mixedDocument(sections = 1)
        return buildString(chars + section.length) {
            while (length < chars) {
                append(section)
            }
        }.take(chars)
    }

    /** One fenced code block that never closes, about [chars] characters long. */
    fun unclosedFenceDocument(chars: Int): String = buildString(chars + 32) {
        append("```kotlin\n")
        var line = 0
        while (length < chars) {
            append("val value")
            append(line++)
            append(" = compute(input)\n")
        }
    }

    /** One bullet list with many short items, about [chars] characters long. */
    fun longListDocument(chars: Int): String = buildString(chars + 32) {
        var item = 0
        while (length < chars) {
            append("- list item ")
            append(item++)
            append(" with *emphasis*\n")
        }
    }

    /**
     * Splits [document] into 4 to 10 character chunks, the size of the tokens an LLM streams. The sizes come
     * from a fixed seed so every run appends the same chunks.
     */
    fun tokenChunks(document: String): List<String> {
        val random = java.util.Random(TOKEN_SEED)
        val chunks = ArrayList<String>(document.length / 6)
        var start = 0
        while (start < document.length) {
            val end = minOf(document.length, start + 4 + random.nextInt(7))
            chunks += document.substring(start, end)
            start = end
        }
        return chunks
    }

    private const val TOKEN_SEED = 42L
}
