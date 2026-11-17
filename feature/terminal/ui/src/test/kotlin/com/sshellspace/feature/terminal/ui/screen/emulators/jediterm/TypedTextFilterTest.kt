package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import com.sshellspace.feature.terminal.ui.screen.emulators.jediterm.TypedTextFilter.Source
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypedTextFilterTest {

    private var now = 0L
    private val filter = TypedTextFilter { now }

    @Test
    fun `text arriving both ways is sent once`() {
        assertTrue(filter.accept(Source.KeyTyped, "a"))
        assertFalse(filter.accept(Source.InputMethod, "a"))
    }

    @Test
    fun `fast typing in either order still pairs up`() {
        assertTrue(filter.accept(Source.InputMethod, "a"))
        assertTrue(filter.accept(Source.InputMethod, "b"))
        assertFalse(filter.accept(Source.KeyTyped, "a"))
        assertFalse(filter.accept(Source.KeyTyped, "b"))
    }

    @Test
    fun `the same text twice the same way is typed twice`() {
        assertTrue(filter.accept(Source.KeyTyped, "a"))
        assertTrue(filter.accept(Source.KeyTyped, "a"))
    }

    @Test
    fun `text the other way long after isn't a duplicate`() {
        assertTrue(filter.accept(Source.KeyTyped, "é"))
        now += 1_000_000_000L
        assertTrue(filter.accept(Source.InputMethod, "é"))
    }

    @Test
    fun `text arriving all three ways is sent once, and a repeated key still types twice`() {
        assertTrue(filter.accept(Source.KeyDown, "a"))
        assertFalse(filter.accept(Source.KeyTyped, "a"))
        assertFalse(filter.accept(Source.InputMethod, "a"))
        assertTrue(filter.accept(Source.KeyDown, "a"))
        assertFalse(filter.accept(Source.KeyTyped, "a"))
        assertFalse(filter.accept(Source.InputMethod, "a"))
    }
}
