package com.adamglin.compose.markdown.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmPlatformTest {
    @Test
    fun jvmSourceSetIsActive() {
        assertEquals("JVM", jvmPlatformName)
    }
}
