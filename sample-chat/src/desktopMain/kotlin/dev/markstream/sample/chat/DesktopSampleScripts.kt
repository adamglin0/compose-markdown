package dev.markstream.sample.chat

internal actual fun loadPlatformSampleScripts(): List<SampleScript> = SampleChatDefaults.createScripts(::readDesktopResource)

private fun readDesktopResource(path: String): String {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
        ?: SampleScript::class.java.classLoader.getResourceAsStream(path)
        ?: error("Missing sample resource: $path")
    return stream.bufferedReader().use { reader -> reader.readText() }
}
