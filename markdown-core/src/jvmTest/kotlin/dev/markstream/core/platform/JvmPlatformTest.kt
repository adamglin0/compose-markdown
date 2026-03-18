package dev.markstream.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmPlatformTest {
    @Test
    fun jvmSourceSetIsActive() {
        assertEquals("JVM", jvmPlatformName)
    }
}
