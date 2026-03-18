package dev.markstream.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.markstream.core.api.MarkdownEngine
import dev.markstream.core.model.MarkdownSnapshot
import dev.markstream.core.model.ParseDelta

@Stable
class MarkdownState internal constructor(
    private val engine: MarkdownEngine,
) {
    var snapshot by mutableStateOf(engine.snapshot())
        private set

    fun append(chunk: String): ParseDelta {
        val delta = engine.append(chunk)
        snapshot = delta.snapshot
        return delta
    }

    fun finish(): ParseDelta {
        val delta = engine.finish()
        snapshot = delta.snapshot
        return delta
    }

    fun reset() {
        engine.reset()
        snapshot = engine.snapshot()
    }
}

@Composable
fun rememberMarkdownState(
    engineFactory: () -> MarkdownEngine = { MarkdownEngine() },
): MarkdownState = remember {
    MarkdownState(
        engine = engineFactory(),
    )
}
