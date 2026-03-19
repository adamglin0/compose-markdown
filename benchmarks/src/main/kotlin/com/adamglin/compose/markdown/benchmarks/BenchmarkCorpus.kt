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
}
