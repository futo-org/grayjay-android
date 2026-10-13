package com.futo.platformplayer

import com.futo.platformplayer.states.StatePlayer
import junit.framework.TestCase.assertEquals
import org.junit.Test

class QueuePlayNextTests {
    private class Item(val url: String) {
        override fun toString(): String = url
    }

    private fun urls(list: List<Item>): List<String> = list.map { it.url }

    @Test
    fun `insertAfter places item directly after current`() {
        val a = Item("a"); val b = Item("b"); val c = Item("c")
        val list = mutableListOf(a, b, c)

        StatePlayer.insertAfter(list, b, Item("x")) { it.url }

        assertEquals(listOf("a", "b", "x", "c"), urls(list))
    }

    @Test
    fun `insertAfter appends when current is last`() {
        val a = Item("a"); val b = Item("b")
        val list = mutableListOf(a, b)

        StatePlayer.insertAfter(list, b, Item("x")) { it.url }

        assertEquals(listOf("a", "b", "x"), urls(list))
    }

    @Test
    fun `insertAfter moves an already queued item instead of duplicating it`() {
        val a = Item("a"); val b = Item("b"); val c = Item("c"); val d = Item("d")
        val list = mutableListOf(a, b, c, d)

        StatePlayer.insertAfter(list, a, Item("d")) { it.url }

        assertEquals(listOf("a", "d", "b", "c"), urls(list))
    }

    @Test
    fun `insertAfter keeps current in place when a duplicate before it is removed`() {
        val a = Item("a"); val b = Item("b"); val c = Item("c")
        val list = mutableListOf(a, b, c)

        StatePlayer.insertAfter(list, c, Item("a")) { it.url }

        assertEquals(listOf("b", "c", "a"), urls(list))
        assertEquals(1, list.indexOf(c))
    }

    @Test
    fun `insertAfter inserts at start without current`() {
        val list = mutableListOf<Item>()

        StatePlayer.insertAfter(list, null, Item("x")) { it.url }

        assertEquals(listOf("x"), urls(list))
    }
}
