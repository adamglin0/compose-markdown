# Progressive code fence

The code block below is intentionally written so the closing fence appears near the end of the stream.

```kotlin
fun renderWorkbench(selectedTitle: String) {
    println("Preparing preview for $selectedTitle")

    val steps = listOf(
        "Reset markdown state",
        "Append incoming chunks",
        "Finish the stream"
    )

    steps.forEachIndexed { index, step ->
        println("${index + 1}. $step")
    }
```

After the fence closes, this final paragraph should still render as a normal block and prove that subsequent content remains intact.
