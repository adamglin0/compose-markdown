package com.adamglin.compose.markdown.core.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedArrayTest {
    @Test
    fun publishedListsNeverObserveLaterWrites() {
        val array = SharedArray<String>()
        listOf("a", "b", "c").forEach(array::add)
        val first = array.followedBy(listOf("tail"))

        array[0] = "x"
        array[1] = "y"
        array.add("d")
        val second = array.followedBy(emptyList())
        array[2] = "z"

        assertEquals(listOf("a", "b", "c", "tail"), first)
        assertEquals(listOf("x", "y", "c", "d"), second)
        assertEquals(listOf("x", "y", "z", "d"), array.followedBy(emptyList()))
    }
}
