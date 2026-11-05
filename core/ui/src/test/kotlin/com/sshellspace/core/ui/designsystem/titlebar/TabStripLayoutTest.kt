package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class TabStripLayoutTest {

    @Test
    fun `tabs share the space between their minimum and maximum`() {
        assertEquals(220.dp, tabSlotWidth(1000.dp, 2))
        assertEquals(150.dp, tabSlotWidth(600.dp, 4))
        assertEquals(104.dp, tabSlotWidth(600.dp, 20))
    }

    @Test
    fun `a tab dragged far past the left edge stops at the first position`() {
        assertEquals(0f, draggedTabPosition(contentX = -5_000f, grabOffset = 40f, count = 4, slot = 100f))
    }

    @Test
    fun `a tab dragged far past the right edge stops at the last position`() {
        assertEquals(300f, draggedTabPosition(contentX = 5_000f, grabOffset = 40f, count = 4, slot = 100f))
    }

    @Test
    fun `a tab follows the pointer between the edges`() {
        assertEquals(110f, draggedTabPosition(contentX = 150f, grabOffset = 40f, count = 4, slot = 100f))
    }

    @Test
    fun `a tab takes the position it is more than half way into`() {
        assertEquals(1, dropIndex(position = 149f, count = 4, slot = 100f))
        assertEquals(2, dropIndex(position = 151f, count = 4, slot = 100f))
        assertEquals(3, dropIndex(position = 9_000f, count = 4, slot = 100f))
    }
}
