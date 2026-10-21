package com.adamglin.compose.markdown.benchmarks

import com.sun.management.ThreadMXBean
import com.adamglin.compose.markdown.core.api.MarkdownEngine
import com.adamglin.compose.markdown.core.dialect.MarkdownDialect
import com.adamglin.compose.markdown.core.model.ParseDelta
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToLong

internal fun main(args: Array<String>) {
    val options = BenchmarkOptions.parse(args)
    val runner = MarkdownBenchmarkRunner(options = options)
    val report = runner.run()
    val rendered = BenchmarkReportRenderer.render(report)
    print(rendered)
    options.outputPath?.let { outputPath ->
        outputPath.parent?.let(Files::createDirectories)
        Files.writeString(outputPath, rendered)
    }
}

private class MarkdownBenchmarkRunner(
    private val options: BenchmarkOptions,
) {
    private val allocationTracker = AllocationTracker.currentThread()

    fun run(): BenchmarkReport {
        val results = BenchmarkScenario.all(smoke = options.smoke).map(::measureScenario)
        return BenchmarkReport(
            generatedAt = java.time.Instant.now().toString(),
            mode = if (options.smoke) "smoke" else "full",
            warmupIterations = options.warmupIterations,
            measuredIterations = options.measureIterations,
            results = results,
        )
    }

    private fun measureScenario(scenario: BenchmarkScenario): ScenarioResult {
        repeat(options.warmupIterations) {
            executeScenario(scenario)
        }

        val measurements = List(options.measureIterations) {
            executeScenario(scenario)
        }

        return ScenarioResult(
            scenario = scenario.id,
            description = scenario.description,
            documentChars = measurements.first().documentChars,
            appendCount = measurements.first().appendCount,
            appendTotalTimeMillis = measurements.averageBy { it.appendTotalTimeNanos.toMillis() },
            finishTimeMillis = measurements.averageBy { it.finishTimeNanos.toMillis() },
            totalParseTimeMillis = measurements.averageBy { it.totalParseTimeNanos.toMillis() },
            averageAppendMicros = measurements.averageBy { it.averageAppendNanos.toMicros() },
            preservedBlockRatio = measurements.averageBy { it.preservedBlockRatio },
            reparsedBlockRatio = measurements.averageBy { it.reparsedBlockRatio },
            allocatedBytes = measurements.averageBy { it.allocatedBytes.toDouble() }.roundToLong(),
        )
    }

    private fun executeScenario(scenario: BenchmarkScenario): ScenarioMeasurement {
        val engine = MarkdownEngine(dialect = MarkdownDialect.GfmCompat)
        val input = scenario.input
        var appendTotalTimeNanos = 0L
        var finishTimeNanos = 0L
        var preservedBlocks = 0L
        var reparsedBlocks = 0L
        val allocatedBytes = allocationTracker.measureAllocatedBytes {
            input.chunks.forEach { chunk ->
                val delta = timedAppend(engine = engine, chunk = chunk)
                appendTotalTimeNanos += delta.elapsedNanos
                preservedBlocks += delta.delta.stats.preservedBlocks.toLong()
                reparsedBlocks += delta.delta.stats.reparsedBlocks.toLong()
            }

            finishTimeNanos = measureNanos {
                val delta = engine.finish()
                preservedBlocks += delta.stats.preservedBlocks.toLong()
                reparsedBlocks += delta.stats.reparsedBlocks.toLong()
            }
        }

        val appendCount = input.chunks.size
        val totalBlocks = preservedBlocks + reparsedBlocks
        return ScenarioMeasurement(
            documentChars = input.document.length,
            appendCount = appendCount,
            appendTotalTimeNanos = appendTotalTimeNanos,
            finishTimeNanos = finishTimeNanos,
            totalParseTimeNanos = appendTotalTimeNanos + finishTimeNanos,
            averageAppendNanos = if (appendCount == 0) 0.0 else appendTotalTimeNanos.toDouble() / appendCount,
            preservedBlockRatio = if (totalBlocks == 0L) 0.0 else preservedBlocks.toDouble() / totalBlocks,
            reparsedBlockRatio = if (totalBlocks == 0L) 0.0 else reparsedBlocks.toDouble() / totalBlocks,
            allocatedBytes = allocatedBytes,
        )
    }

    private fun timedAppend(engine: MarkdownEngine, chunk: String): TimedDelta {
        lateinit var delta: ParseDelta
        val elapsedNanos = measureNanos {
            delta = engine.append(chunk)
        }
        return TimedDelta(delta = delta, elapsedNanos = elapsedNanos)
    }

    private fun measureNanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }
}

private class BenchmarkScenario(
    val id: String,
    val description: String,
    val input: ScenarioInput,
) {
    companion object {
        fun all(smoke: Boolean): List<BenchmarkScenario> = buildList {
            val largeParagraphs = BenchmarkCorpus.largeParagraphDocument(repetitions = if (smoke) 180 else 500)
            add(BenchmarkScenario("one-shot-large-paragraph", "Large paragraph-heavy document parsed in one append.", ScenarioInput.oneShot(largeParagraphs)))
            val chunkCount = if (smoke) 120 else 240
            add(
                BenchmarkScenario(
                    "chunked-mixed-append",
                    "$chunkCount small appends over mixed paragraph/list/quote/code/table content.",
                    ScenarioInput.chunked(BenchmarkCorpus.chunkedMixedDocument(chunkCount = chunkCount)),
                ),
            )
            val mixed = BenchmarkCorpus.mixedDocument(sections = if (smoke) 16 else 40)
            add(BenchmarkScenario("one-shot-mixed-document", "Large mixed document parsed in one append.", ScenarioInput.oneShot(mixed)))
            (if (smoke) listOf(25) else listOf(25, 50, 100, 200)).forEach { kilobytes ->
                val document = BenchmarkCorpus.mixedDocumentOfSize(chars = kilobytes * 1_000)
                add(
                    BenchmarkScenario(
                        "token-stream-${kilobytes}kb",
                        "Mixed ${kilobytes}KB document streamed in 4-10 character tokens.",
                        ScenarioInput.chunked(BenchmarkCorpus.tokenChunks(document)),
                    ),
                )
            }
            if (!smoke) {
                add(
                    BenchmarkScenario(
                        "token-stream-open-fence-100kb",
                        "One unclosed 100KB fenced code block streamed in 4-10 character tokens.",
                        ScenarioInput.chunked(BenchmarkCorpus.tokenChunks(BenchmarkCorpus.unclosedFenceDocument(chars = 100_000))),
                    ),
                )
                add(
                    BenchmarkScenario(
                        "token-stream-long-list-25kb",
                        "One 25KB bullet list streamed in 4-10 character tokens.",
                        ScenarioInput.chunked(BenchmarkCorpus.tokenChunks(BenchmarkCorpus.longListDocument(chars = 25_000))),
                    ),
                )
            }
        }
    }
}

private data class BenchmarkOptions(
    val smoke: Boolean,
    val warmupIterations: Int,
    val measureIterations: Int,
    val outputPath: Path?,
) {
    companion object {
        fun parse(args: Array<String>): BenchmarkOptions {
            val smoke = args.contains("--smoke")
            val outputArgument = args.firstOrNull { it.startsWith("--output=") }
            return BenchmarkOptions(
                smoke = smoke,
                warmupIterations = if (smoke) 1 else 3,
                measureIterations = if (smoke) 2 else 8,
                outputPath = outputArgument?.substringAfter('=')?.let(Path::of),
            )
        }
    }
}

private data class BenchmarkReport(
    val generatedAt: String,
    val mode: String,
    val warmupIterations: Int,
    val measuredIterations: Int,
    val results: List<ScenarioResult>,
)

private data class ScenarioResult(
    val scenario: String,
    val description: String,
    val documentChars: Int,
    val appendCount: Int,
    val appendTotalTimeMillis: Double,
    val finishTimeMillis: Double,
    val totalParseTimeMillis: Double,
    val averageAppendMicros: Double,
    val preservedBlockRatio: Double,
    val reparsedBlockRatio: Double,
    val allocatedBytes: Long,
)

private data class ScenarioMeasurement(
    val documentChars: Int,
    val appendCount: Int,
    val appendTotalTimeNanos: Long,
    val finishTimeNanos: Long,
    val totalParseTimeNanos: Long,
    val averageAppendNanos: Double,
    val preservedBlockRatio: Double,
    val reparsedBlockRatio: Double,
    val allocatedBytes: Long,
)

private data class TimedDelta(
    val delta: ParseDelta,
    val elapsedNanos: Long,
)

private class ScenarioInput(
    val chunks: List<String>,
) {
    val document: String = chunks.joinToString(separator = "")

    companion object {
        fun oneShot(document: String) = ScenarioInput(chunks = listOf(document))

        fun chunked(chunks: List<String>) = ScenarioInput(chunks = chunks)
    }
}

private object BenchmarkReportRenderer {
    fun render(report: BenchmarkReport): String = buildString {
        appendLine("# Benchmark Report")
        appendLine()
        appendLine("- generatedAt: ${report.generatedAt}")
        appendLine("- mode: ${report.mode}")
        appendLine("- warmupIterations: ${report.warmupIterations}")
        appendLine("- measuredIterations: ${report.measuredIterations}")
        appendLine()
        appendLine("| scenario | chars | appends | append total ms | avg append us | finish ms | total ms | preserved ratio | reparsed ratio | allocated bytes |")
        appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        report.results.forEach { result ->
            appendLine(
                "| ${result.scenario} | ${result.documentChars} | ${result.appendCount} | ${result.appendTotalTimeMillis.format(3)} | ${result.averageAppendMicros.format(3)} | ${result.finishTimeMillis.format(3)} | ${result.totalParseTimeMillis.format(3)} | ${result.preservedBlockRatio.format(3)} | ${result.reparsedBlockRatio.format(3)} | ${result.allocatedBytes} |",
            )
        }
    }

    private fun Double.format(scale: Int): String = java.lang.String.format("%.${scale}f", this)
}

private class AllocationTracker private constructor(
    private val bean: ThreadMXBean?,
    private val threadId: Long,
) {
    fun measureAllocatedBytes(block: () -> Unit): Long {
        val start = bean?.getThreadAllocatedBytes(threadId) ?: -1L
        block()
        val end = bean?.getThreadAllocatedBytes(threadId) ?: -1L
        return if (start >= 0L && end >= 0L) end - start else -1L
    }

    companion object {
        fun currentThread(): AllocationTracker {
            val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
            bean?.isThreadAllocatedMemoryEnabled = true
            return AllocationTracker(bean = bean, threadId = Thread.currentThread().id)
        }
    }
}

private fun Long.toMillis(): Double = this / 1_000_000.0

private fun Double.toMicros(): Double = this / 1_000.0

private inline fun <T> Iterable<T>.averageBy(selector: (T) -> Double): Double {
    var sum = 0.0
    var count = 0
    for (item in this) {
        sum += selector(item)
        count += 1
    }
    return if (count == 0) 0.0 else sum / count
}
